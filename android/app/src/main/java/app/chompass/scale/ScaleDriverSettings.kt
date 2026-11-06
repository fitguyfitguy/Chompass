package app.chompass.scale

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.health.openscale.core.bluetooth.scales.ScaleDeviceHandler
import com.health.openscale.core.utils.LogManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ChompassScaleSync"

/**
 * Persistent [ScaleDeviceHandler.DriverSettings] for the vendored scale drivers:
 * an in-memory map with write-through into a dedicated small Preferences
 * DataStore. Deliberately NOT the app-wide fudaiDataStore: that proto carries
 * the whole preference set, so every key write would rewrite a large file;
 * the drivers' `userMap/...` keys are the only payload here. The driver keys
 * contain `/`, which Preferences keys allow.
 *
 * Writes go through a FIFO channel consumed by one writer coroutine in the
 * caller-owned [ioScope] (it must outlive the sync run's scope, because
 * handlers keep persisting while the run tears down). The single consumer
 * preserves call order; [flush] appends a marker item, so it completes only
 * after every earlier write reached the store.
 */
class ScaleDriverSettings(
    file: File,
    ioScope: CoroutineScope,
) : ScaleDeviceHandler.DriverSettings {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { file },
    )

    private val values = ConcurrentHashMap<String, Any>()

    private val writes = Channel<(MutablePreferences) -> Unit>(Channel.UNLIMITED)
    private val writer = ioScope.launch {
        for (edit in writes) {
            try {
                store.edit { edit(it) }
            } catch (e: IOException) {
                LogManager.w(TAG, "scale driver settings persist failed: ${e.message}")
            }
        }
    }

    /** Read every persisted key into memory. Call once per run, before the handler attaches. */
    suspend fun load() {
        val prefs = try {
            store.data.first()
        } catch (e: IOException) {
            LogManager.w(TAG, "scale driver settings unreadable, continuing with empty: ${e.message}")
            return
        }
        values.clear()
        for ((key, value) in prefs.asMap()) {
            values[key.name] = value
        }
    }

    /** Completes after every queued write reached the store (not necessarily fsynced). */
    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        writes.trySend { done.complete(Unit) }
        done.await()
    }

    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default

    override fun putInt(key: String, value: Int) {
        values[key] = value
        writes.trySend { it[intPreferencesKey(key)] = value }
    }

    override fun getString(key: String, default: String?): String? = values[key] as? String ?: default

    override fun putString(key: String, value: String) {
        values[key] = value
        writes.trySend { it[stringPreferencesKey(key)] = value }
    }

    override fun remove(key: String) {
        values.remove(key)
        writes.trySend {
            it.remove(intPreferencesKey(key))
            it.remove(stringPreferencesKey(key))
        }
    }
}
