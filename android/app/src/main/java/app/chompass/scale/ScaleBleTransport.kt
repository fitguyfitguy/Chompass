package app.chompass.scale

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import com.health.openscale.core.bluetooth.scales.ScaleDeviceHandler
import com.health.openscale.core.service.ScannedDeviceInfo
import com.health.openscale.core.utils.LogManager
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import com.welie.blessed.GattStatus
import com.welie.blessed.HciStatus
import com.welie.blessed.WriteType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * Chompass-side BLE transport for the vendored openScale drivers. Implements
 * [ScaleDeviceHandler.Transport] over blessed-kotlin (MIT, JitPack) and owns the
 * [BluetoothCentralManager]: scanning, connecting, and the peripheral callback
 * plumbing. The sync flow is orchestrated by [ScaleSyncController].
 *
 * API usage mirrors upstream GattScaleAdapter.kt at the vendored pin (serialized
 * op queue, per-characteristic deferreds with timeout, pacing delays). That file
 * is deliberately NOT vendored — see third_party/openscale/SNAPSHOT.md.
 *
 * GATT I/O is serialized on a single op queue, one in-flight callback path
 * (handlers call transport methods synchronously from blessed callbacks).
 * Every notification's raw bytes are logged under `ChompassScaleSync` — this is
 * the fixture-capture hook for the replay test.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_SCAN/CONNECT granted by ScaleSyncSheet before start.
class ScaleBleTransport(
    context: Context,
    private val scope: CoroutineScope,
    /** Per scanned advertisement; controller runs registry support matching. */
    private val onScanned: (ScannedDeviceInfo) -> Unit,
    /** Services discovered and link ready for I/O. */
    private val onReady: () -> Unit,
    /** Link lost (disconnect or connection failure); [reason] is a technical string. */
    private val onLinkLost: (reason: String) -> Unit,
    /** Notification payload forwarded to handler.handleNotification. */
    private val onNotification: (characteristic: UUID, data: ByteArray) -> Unit,
) : ScaleDeviceHandler.Transport {
    private companion object {
        const val TAG = "ChompassScaleSync"

        // TuningProfile.Balanced.forGatt() values from upstream ModernScaleAdapter.kt
        // at the vendored pin (comment-only reference; upstream-first sync source).
        const val CONNECT_AFTER_SCAN_DELAY_MS = 650L
        const val NOTIFY_SETUP_DELAY_MS = 120L
        const val WRITE_WITH_RESPONSE_DELAY_MS = 80L
        const val WRITE_WITHOUT_RESPONSE_DELAY_MS = 35L
        const val POST_WRITE_DELAY_MS = 20L
        const val POST_READ_DELAY_MS = 20L
        const val OPERATION_TIMEOUT_MS = 1000L
        const val REQUEST_MTU_BYTES = 185
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val central: BluetoothCentralManager by lazy {
        BluetoothCentralManager(context, centralCallback, mainHandler)
    }

    private var currentPeripheral: BluetoothPeripheral? = null
    private val deferredMap = mutableMapOf<UUID, CompletableDeferred<Unit>>()

    private val opQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val opWorker: Job = scope.launch {
        while (isActive) {
            val op = opQueue.receiveCatching().getOrNull() ?: break
            op()
        }
    }

    private suspend fun ioGap(ms: Long) {
        delay(ms.milliseconds)
    }

    fun startScan() {
        scope.launch { runCatching { central.scanForPeripherals() } }
    }

    fun stopScan() {
        scope.launch { runCatching { if (central.isScanning) central.stopScan() } }
    }

    fun connect(address: String) {
        scope.launch {
            delay(CONNECT_AFTER_SCAN_DELAY_MS.milliseconds)
            runCatching { central.stopScan() }
            val peripheral = currentPeripheral
            if (peripheral != null && peripheral.address == address) {
                runCatching { central.connect(peripheral, peripheralCallback) }
                    .onFailure { LogManager.e(TAG, "connect call failed: ${it.message}", it) }
            }
        }
    }

    fun close() {
        stopScan()
        scope.launch {
            currentPeripheral?.let { runCatching { central.cancelConnection(it) } }
            runCatching { central.close() }
        }
        opQueue.close()
        opWorker.cancel()
    }

    private fun completeDeferred(characteristic: UUID) {
        deferredMap.remove(characteristic)?.complete(Unit)
    }

    /** Local copy of ScaleDeviceHandler.toHexPreview (member extension; no instance here). */
    private fun ByteArray.hexPreview(limit: Int): String {
        if (limit <= 0 || isEmpty()) return "(payload ${size}b)"
        val show = minOf(size, limit)
        val sb = StringBuilder("payload=[")
        for (i in 0 until show) {
            if (i > 0) sb.append(' ')
            sb.append(String.format("%02X", this[i]))
        }
        if (size > limit) sb.append(" …(+").append(size - limit).append("b)")
        sb.append(']')
        return sb.toString()
    }

    private val centralCallback = object : BluetoothCentralManagerCallback() {
        override fun onDiscovered(peripheral: BluetoothPeripheral, scanResult: android.bluetooth.le.ScanResult) {
            val record = scanResult.scanRecord
            val info = ScannedDeviceInfo(
                name = peripheral.name,
                address = peripheral.address,
                rssi = scanResult.rssi,
                serviceUuids = record?.serviceUuids?.mapNotNull { it?.uuid } ?: emptyList(),
                manufacturerData = record?.manufacturerSpecificData as SparseArray<ByteArray>?,
                serviceData = record?.serviceData
                    ?.mapKeys { it.key.uuid }
                    ?.mapValues { it.value.copyOf() }
                    ?: emptyMap(),
            )
            LogManager.i(TAG, "scan hit ${info.name} (${info.address}) rssi=${info.rssi}")
            onScanned(info)
        }

        override fun onConnected(peripheral: BluetoothPeripheral) {
            currentPeripheral = peripheral
            LogManager.i(TAG, "connected ${peripheral.address}")
        }

        override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
            LogManager.e(TAG, "connection failed ${peripheral.address}: $status", null)
            if (currentPeripheral?.address == peripheral.address) currentPeripheral = null
            onLinkLost("connection failed: $status")
        }

        override fun onDisconnected(peripheral: BluetoothPeripheral, status: HciStatus) {
            LogManager.i(TAG, "disconnected ${peripheral.address}: $status")
            if (currentPeripheral?.address == peripheral.address) {
                currentPeripheral = null
                onLinkLost("disconnected: $status")
            }
        }
    }

    private val peripheralCallback = object : BluetoothPeripheralCallback() {
        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            runCatching { peripheral.requestMtu(REQUEST_MTU_BYTES) }
            currentPeripheral = peripheral
            LogManager.i(TAG, "services discovered ${peripheral.address}")
            onReady()
        }

        override fun onCharacteristicUpdate(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus,
        ) {
            LogManager.d(TAG, "<- notify chr=${characteristic.uuid} len=${value.size} $status ${value.hexPreview(24)}")
            onNotification(characteristic.uuid, value)
            completeDeferred(characteristic.uuid)
        }

        override fun onCharacteristicWrite(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus,
        ) {
            LogManager.d(TAG, "<- write resp chr=${characteristic.uuid} len=${value.size} $status")
            completeDeferred(characteristic.uuid)
        }

        override fun onNotificationStateUpdate(
            peripheral: BluetoothPeripheral,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus,
        ) {
            LogManager.d(TAG, "<- notify state chr=${characteristic.uuid} $status")
            completeDeferred(characteristic.uuid)
        }
    }

    // --- ScaleDeviceHandler.Transport -----------------------------------------

    override fun setNotifyOn(service: UUID, characteristic: UUID) {
        opQueue.trySend {
            val p = currentPeripheral ?: return@trySend
            LogManager.d(TAG, "-> set notify chr=$characteristic")
            deferredMap[characteristic] = CompletableDeferred()
            val started = p.startNotify(service, characteristic)
            if (!started) {
                LogManager.w(TAG, "failed to initiate notify for $characteristic")
                completeDeferred(characteristic)
            }
            try {
                withTimeout(OPERATION_TIMEOUT_MS.milliseconds) { deferredMap[characteristic]?.await() }
            } catch (_: Throwable) {
                LogManager.w(TAG, "timeout waiting for notify setup on $characteristic")
            } finally {
                completeDeferred(characteristic)
                ioGap(NOTIFY_SETUP_DELAY_MS)
            }
        }
    }

    override fun write(service: UUID, characteristic: UUID, payload: ByteArray, withResponse: Boolean) {
        opQueue.trySend {
            val p = currentPeripheral ?: return@trySend
            val ch = p.getCharacteristic(service, characteristic) ?: return@trySend

            val supportsWriteResponse = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            val supportsWriteNoResponse = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val type = when {
                withResponse && supportsWriteResponse -> WriteType.WITH_RESPONSE
                !withResponse && supportsWriteNoResponse -> WriteType.WITHOUT_RESPONSE
                supportsWriteResponse -> WriteType.WITH_RESPONSE
                supportsWriteNoResponse -> WriteType.WITHOUT_RESPONSE
                else -> {
                    LogManager.w(TAG, "characteristic $characteristic does not support writing")
                    return@trySend
                }
            }

            ioGap(if (withResponse) WRITE_WITH_RESPONSE_DELAY_MS else WRITE_WITHOUT_RESPONSE_DELAY_MS)
            p.writeCharacteristic(service, characteristic, payload, type)
            LogManager.d(TAG, "-> write chr=$characteristic len=${payload.size} ${payload.hexPreview(24)}")

            try {
                withTimeout(OPERATION_TIMEOUT_MS.milliseconds) { deferredMap[characteristic]?.await() }
            } catch (_: Throwable) {
                LogManager.w(TAG, "timeout waiting for write on $characteristic")
            } finally {
                completeDeferred(characteristic)
                ioGap(POST_WRITE_DELAY_MS)
            }
        }
    }

    override fun read(service: UUID, characteristic: UUID) {
        opQueue.trySend {
            val p = currentPeripheral ?: return@trySend
            p.getCharacteristic(service, characteristic) ?: return@trySend
            p.readCharacteristic(service, characteristic)
            LogManager.d(TAG, "-> read chr=$characteristic")
            try {
                withTimeout(OPERATION_TIMEOUT_MS.milliseconds) { deferredMap[characteristic]?.await() }
            } catch (_: Throwable) {
                LogManager.w(TAG, "timeout waiting for read on $characteristic")
            } finally {
                completeDeferred(characteristic)
                ioGap(POST_READ_DELAY_MS)
            }
        }
    }

    override fun disconnect() {
        currentPeripheral?.let { p -> scope.launch { runCatching { central.cancelConnection(p) } } }
    }

    override fun getPeripheral(): BluetoothPeripheral? = currentPeripheral

    override fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean {
        return currentPeripheral?.getCharacteristic(service, characteristic) != null
    }
}
