package app.chompass.widget

import android.content.Context
import app.chompass.data.PreferencesStore
import app.chompass.models.WidgetSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** Bounded DataStore read for Glance widgets — avoids indefinite loading layout hangs. */
internal object WidgetSnapshotLoader {
    private const val READ_TIMEOUT_MS = 3_000L

    suspend fun load(context: Context): WidgetSnapshot {
        val snapshot = runCatching {
            withTimeout(READ_TIMEOUT_MS) {
                PreferencesStore(context).widgetSnapshot.first()
            }
        }.getOrNull()
        return snapshot ?: WidgetSnapshot.empty()
    }
}
