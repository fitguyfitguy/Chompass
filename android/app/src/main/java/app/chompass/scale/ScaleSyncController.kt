package app.chompass.scale

import android.content.Context
import app.chompass.data.BodyCompositionRepository
import app.chompass.data.BodyFatRepository
import app.chompass.data.ProfileRepository
import app.chompass.data.WeightRepository
import app.chompass.models.Gender
import app.chompass.models.UserProfile
import com.health.openscale.core.bluetooth.BluetoothEvent
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
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

/** Progress of one scale sync run, observed by ScaleSyncSheet. */
sealed interface ScaleSyncState {
    data object Idle : ScaleSyncState
    data object Scanning : ScaleSyncState
    data class Connecting(val deviceName: String) : ScaleSyncState
    data class Reading(val deviceName: String) : ScaleSyncState
    /** Scale asked which of its saved user slots to bind; labels align with ids. */
    data class AwaitingUser(val labels: List<String>, val ids: List<Int>) : ScaleSyncState
    /** Scale asked for a slot's PIN/consent code (0..9999). */
    data class AwaitingConsent(val deviceName: String, val scaleIndex: Int) : ScaleSyncState
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
 * only. Interaction requests from handlers (user-slot pick, PIN/consent) surface
 * as sheet states ([ScaleSyncState.AwaitingUser], [ScaleSyncState.AwaitingConsent])
 * and come back through [onUserSelected] / [onConsentEntered]; the profile shim
 * is the single app user. Slot mapping and consent persist across runs through
 * [ScaleDriverSettings].
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

    /** Persistence scope for driver settings: must outlive [runScope], because
     *  handlers keep persisting while the run tears down. */
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val driverSettings = ScaleDriverSettings(
        File(context.filesDir, "scale_driver.preferences_pb"),
        settingsScope,
    )

    /** Device name captured when [ScaleSyncState.Reading] was entered, so a
     *  prompt round-trip can restore the reading row. */
    @Volatile
    private var lastDeviceName: String? = null

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
        driverSettings.load()
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
                    lastDeviceName = name
                    _state.value = ScaleSyncState.Reading(name)
                    h.attach(t, this, driverSettings, this, scope)
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
        lastDeviceName = null
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
     * The handler asks the app user to pick a scale user slot (CHOOSE_USER) or
     * enter the slot's PIN/consent code (ENTER_CONSENT). Both surface as sheet
     * states; answers come back through [onUserSelected] / [onConsentEntered].
     * Unreadable payloads keep the old behavior: log + ignore.
     */
    override fun onUserInteractionRequired(interactionType: BluetoothEvent.UserInteractionType, data: Any?) {
        when (interactionType) {
            BluetoothEvent.UserInteractionType.CHOOSE_USER -> {
                val (labels, ids) = normalizeChooseUser(data) ?: run {
                    LogManager.w(TAG, "CHOOSE_USER with unreadable payload — ignored")
                    return
                }
                LogManager.i(TAG, "CHOOSE_USER: ${ids.size} slots offered")
                _state.value = ScaleSyncState.AwaitingUser(labels, ids)
            }

            BluetoothEvent.UserInteractionType.ENTER_CONSENT -> {
                val scaleIndex = (data as? IntArray)?.getOrNull(0)
                if (scaleIndex == null) {
                    LogManager.w(TAG, "ENTER_CONSENT with unreadable payload — ignored")
                    return
                }
                LogManager.i(TAG, "ENTER_CONSENT for slot $scaleIndex")
                _state.value = ScaleSyncState.AwaitingConsent(lastDeviceName ?: "scale", scaleIndex)
            }
        }
    }

    /**
     * Answers the AwaitingUser prompt: [scaleIndex] is the scale slot id from the
     * sheet, −1 registers a new user on the scale (consent is auto-generated
     * inside the handler). Restores Reading unless the handler already moved the
     * state forward (it may raise ENTER_CONSENT inside the feedback call).
     */
    fun onUserSelected(scaleIndex: Int) {
        if (_state.value !is ScaleSyncState.AwaitingUser) return
        LogManager.i(TAG, "user picked slot $scaleIndex")
        runScope?.launch {
            try {
                handler?.onUserInteractionFeedback(
                    BluetoothEvent.UserInteractionType.CHOOSE_USER,
                    0,
                    scaleIndex,
                )
            } catch (e: Exception) {
                LogManager.e(TAG, "CHOOSE_USER feedback failed: ${e.message}", e)
            }
            if (_state.value is ScaleSyncState.AwaitingUser) {
                _state.value = ScaleSyncState.Reading(lastDeviceName ?: "scale")
            }
        }
    }

    /** Answers the AwaitingConsent prompt with the scale slot's PIN code (0..9999). */
    fun onConsentEntered(consent: Int) {
        val current = _state.value as? ScaleSyncState.AwaitingConsent ?: return
        LogManager.i(TAG, "consent entered for slot ${current.scaleIndex}")
        runScope?.launch {
            try {
                handler?.onUserInteractionFeedback(
                    BluetoothEvent.UserInteractionType.ENTER_CONSENT,
                    0,
                    Pair(current.scaleIndex, consent),
                )
            } catch (e: Exception) {
                LogManager.e(TAG, "ENTER_CONSENT feedback failed: ${e.message}", e)
            }
            if (_state.value is ScaleSyncState.AwaitingConsent) {
                _state.value = ScaleSyncState.Reading(lastDeviceName ?: "scale")
            }
        }
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

/**
 * Normalizes the CHOOSE_USER payload the vendored handlers emit: a pair of
 * (labels, slot ids) where labels are a `CharSequence[]` (or `List`) and ids an
 * `IntArray` (or `List` of numbers). The last id is typically −1, the
 * "register a new user" row. Returns null for anything else — callers ignore
 * those, matching the pre-handshake behavior.
 */
internal fun normalizeChooseUser(data: Any?): Pair<List<String>, List<Int>>? {
    val pair = data as? Pair<*, *> ?: return null
    val labels = when (val first = pair.first) {
        is Array<*> -> first.map { it?.toString().orEmpty() }
        is List<*> -> first.map { it?.toString().orEmpty() }
        else -> return null
    }
    val ids = when (val second = pair.second) {
        is IntArray -> second.toList()
        is List<*> -> second.map { (it as? Number)?.toInt() ?: return null }
        else -> return null
    }
    if (ids.isEmpty() || labels.size != ids.size) return null
    return labels to ids
}
