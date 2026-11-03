package app.chompass.scale

import android.content.Context
import app.chompass.data.BodyCompositionRepository
import app.chompass.data.BodyFatRepository
import app.chompass.data.ProfileRepository
import app.chompass.data.WeightRepository
import app.chompass.models.Gender
import app.chompass.models.UserProfile
import com.health.openscale.core.bluetooth.data.ScaleMeasurement
import com.health.openscale.core.bluetooth.data.ScaleUser
import com.health.openscale.core.bluetooth.scales.ScaleDeviceHandler
import com.health.openscale.core.data.GenderType
import com.health.openscale.core.data.WeightUnit
import com.health.openscale.core.utils.LogManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

/** Progress of one scale sync run, observed by ScaleSyncSheet. */
sealed interface ScaleSyncState {
    data object Idle : ScaleSyncState
    data object Scanning : ScaleSyncState
    data class Connecting(val deviceName: String) : ScaleSyncState
    data class Reading(val deviceName: String) : ScaleSyncState
    data class Done(val importedCount: Int) : ScaleSyncState
    data class Failed(val message: String) : ScaleSyncState
}

/**
 * Orchestrates one native BLE scale sync: scan for a device claimed by
 * [ScaleDriverRegistry], connect through [ScaleBleTransport], drive the vendored
 * handler, map published [ScaleMeasurement]s via [ScaleMeasurementMapper], and
 * persist them through the existing repositories (same dedupe/import paths as
 * file import). No Health Connect composition write-back — see docs/PARITY.md.
 *
 * The caller (UI) checks BLUETOOTH_SCAN/CONNECT permissions first; Android 12+
 * only. Interaction requests from handlers (user-slot confirmations) are logged
 * and auto-defaulted; the profile shim is the single app user.
 */
class ScaleSyncController(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val weightRepository: WeightRepository,
    private val bodyFatRepository: BodyFatRepository,
    private val bodyCompositionRepository: BodyCompositionRepository,
) : ScaleDeviceHandler.Callbacks, ScaleDeviceHandler.DataProvider {

    private companion object {
        const val TAG = "ChompassScaleSync"
        const val SCAN_TIMEOUT_MS = 60_000L
        const val RUN_HARD_TIMEOUT_MS = 120_000L
    }

    private val _state = MutableStateFlow<ScaleSyncState>(ScaleSyncState.Idle)
    val state: StateFlow<ScaleSyncState> = _state.asStateFlow()

    private var runJob: Job? = null
    private var transport: ScaleBleTransport? = null
    private var handler: ScaleDeviceHandler? = null
    private var runScope: CoroutineScope? = null
    private val importMutex = Mutex()

    /** Profile snapshot built once per run (handlers call DataProvider synchronously). */
    @Volatile
    private var cachedUser: ScaleUser? = null

    /** Starts a sync run and suspends until it reaches Done or Failed. */
    suspend fun startAndAwait(): ScaleSyncState {
        if (_state.value !is ScaleSyncState.Idle && _state.value !is ScaleSyncState.Done &&
            _state.value !is ScaleSyncState.Failed
        ) {
            return _state.value
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        runScope = scope
        cachedUser = buildScaleUser()
        val finished = CompletableDeferred<ScaleSyncState>()
        _state.value = ScaleSyncState.Scanning

        val ble = ScaleBleTransport(
            context = context,
            scope = scope,
            onScanned = { device ->
                if (handler != null) return@ScaleBleTransport
                val (h, support) = ScaleDriverRegistry.supportFor(device) ?: return@ScaleBleTransport
                LogManager.i(TAG, "matched ${support.displayName} at ${device.address}")
                handler = h
                _state.value = ScaleSyncState.Connecting(support.displayName)
                transport?.stopScan()
                transport?.connect(device.address)
            },
            onReady = {
                val h = handler
                val t = transport
                val user = cachedUser
                if (h != null && t != null && user != null) {
                    val name = _state.value.let { s ->
                        (s as? ScaleSyncState.Connecting)?.deviceName ?: "scale"
                    }
                    _state.value = ScaleSyncState.Reading(name)
                    h.attach(t, this, InMemoryDriverSettings(), this, scope)
                    h.handleConnected(user)
                }
            },
            onLinkLost = { reason ->
                val current = _state.value
                if (current is ScaleSyncState.Reading || current is ScaleSyncState.Connecting) {
                    if (publishedCount.get() > 0) {
                        finished.complete(ScaleSyncState.Done(publishedCount.get()))
                    } else {
                        finished.complete(ScaleSyncState.Failed(reason))
                    }
                }
            },
            onNotification = { characteristic, data ->
                handler?.handleNotification(characteristic, data)
            },
        )
        transport = ble
        ble.startScan()

        runJob = scope.launch {
            // Scan cap: if nothing claimed the device within 60 s, fail the run.
            launch {
                delay(SCAN_TIMEOUT_MS)
                if (_state.value is ScaleSyncState.Scanning) {
                    LogManager.w(TAG, "scan timeout — no supported scale found")
                    finished.complete(ScaleSyncState.Failed("no supported scale found"))
                }
            }
            try {
                val result = withTimeout(RUN_HARD_TIMEOUT_MS) { finished.await() }
                _state.value = result
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                LogManager.w(TAG, "sync hard timeout")
                _state.value = if (publishedCount.get() > 0) {
                    ScaleSyncState.Done(publishedCount.get())
                } else {
                    ScaleSyncState.Failed("timeout")
                }
            } finally {
                ble.close()
            }
        }
        return runJob?.let { _state.first { s -> s is ScaleSyncState.Done || s is ScaleSyncState.Failed } }
            ?: ScaleSyncState.Failed("not started")
    }

    fun cancel() {
        runScope?.cancel()
        transport?.close()
        _state.value = ScaleSyncState.Idle
        cleanupRun()
    }

    private val publishedCount = AtomicInteger(0)

    private fun cleanupRun() {
        handler?.detach()
        handler = null
        transport = null
        runScope = null
        cachedUser = null
        publishedCount.set(0)
    }

    // --- ScaleDeviceHandler.Callbacks ------------------------------------------

    override fun onPublish(measurement: ScaleMeasurement) {
        val mapped = ScaleMeasurementMapper.map(measurement) ?: return
        if (mapped.weight == null && mapped.fat == null && mapped.composition == null) return
        LogManager.i(
            TAG,
            "publish -> weight=${mapped.weight != null} fat=${mapped.fat != null} comp=${mapped.composition != null}",
        )
        val scope = runScope ?: return
        scope.launch {
            var changed = 0
            importMutex.withLock {
                mapped.weight?.let { changed += weightRepository.importFromFile(listOf(it)) }
                mapped.fat?.let { changed += bodyFatRepository.importFromFile(listOf(it)) }
                mapped.composition?.let { changed += bodyCompositionRepository.importFromFile(listOf(it)) }
            }
            if (changed > 0) {
                publishedCount.addAndGet(changed)
            }
        }
    }

    override fun onInfo(resId: Int, vararg args: Any) {
        LogManager.d(TAG, "handler info res=$resId")
    }

    override fun onWarn(resId: Int, vararg args: Any) {
        LogManager.w(TAG, "handler warn res=$resId")
    }

    override fun onError(resId: Int, t: Throwable?, vararg args: Any) {
        LogManager.e(TAG, "handler error res=$resId: ${t?.message ?: "—"}", t)
        _state.value = ScaleSyncState.Failed(t?.message ?: context.getString(resId))
    }

    /**
     * Contingency: some scales ask for user-slot confirmations. Auto-defaulted for
     * now (log + ignore); if the device pass stalls here, wire a confirm step into
     * ScaleSyncSheet and call handler.onUserInteractionFeedback.
     */
    override fun onUserInteractionRequired(interactionType: com.health.openscale.core.bluetooth.BluetoothEvent.UserInteractionType, data: Any?) {
        LogManager.w(TAG, "handler requested user interaction $interactionType — auto-defaulted (no-op)")
    }

    override fun resolveString(resId: Int, vararg args: Any): String =
        context.getString(resId, *args)

    // --- ScaleDeviceHandler.DataProvider ----------------------------------------

    override fun currentUser(): ScaleUser = cachedUser ?: ScaleUser()

    override fun usersForDevice(): List<ScaleUser> = listOfNotNull(cachedUser)

    override fun lastMeasurementFor(userId: Int): ScaleMeasurement? = null

    private suspend fun buildScaleUser(): ScaleUser {
        val profile: UserProfile? = profileRepository.profile.first()
        return ScaleUser(
            id = 0,
            userName = profile?.name ?: "",
            birthday = profile?.birthday?.let { Date.from(it) } ?: Date(),
            bodyHeight = profile?.heightCm?.toFloat() ?: 175f,
            gender = when (profile?.gender) {
                Gender.MALE -> GenderType.MALE
                Gender.FEMALE, Gender.OTHER, null -> GenderType.FEMALE
            },
            initialWeight = profile?.weightKg?.toFloat() ?: 70f,
            goalWeight = profile?.goalWeightKg?.toFloat() ?: 0f,
            scaleUnit = WeightUnit.KG,
        )
    }
}

/** In-memory DriverSettings: handler tuning prefs don't need persistence across runs. */
private class InMemoryDriverSettings : ScaleDeviceHandler.DriverSettings {
    private val ints = mutableMapOf<String, Int>()
    private val strings = mutableMapOf<String, String>()

    override fun getInt(key: String, default: Int): Int = ints[key] ?: default
    override fun putInt(key: String, value: Int) {
        ints[key] = value
    }

    override fun getString(key: String, default: String?): String? = strings[key] ?: default
    override fun putString(key: String, value: String) {
        strings[key] = value
    }

    override fun remove(key: String) {
        ints.remove(key)
        strings.remove(key)
    }
}
