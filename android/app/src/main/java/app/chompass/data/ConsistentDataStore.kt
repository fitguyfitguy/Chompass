package app.chompass.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

/**
 * Makes every `data` collector see each completed write.
 *
 * Works around AndroidX DataStore bug b/431787506 (present in every stable release
 * through 1.2.1; fixed upstream in 1.3.0-alpha03, change I6a427): a collector that
 * starts while `updateData` is writing reads the old file tagged with the new version,
 * then drops the real update because its version is not newer. That collector stays
 * stale until the next write (e.g. a screen opened right after logging water keeps
 * showing the old total).
 *
 * After every write through this wrapper finishes, collectors re-subscribe to the
 * delegate, whose cache is then current, so they always reach the latest value.
 * Re-subscribing is a cache hit and repeated equal values are filtered out.
 *
 * Remove once the app depends on a stable DataStore release that includes the fix;
 * `ConsistentDataStoreTest` starts failing its upstream-bug check at that point.
 */
internal class ConsistentDataStore<T>(private val delegate: DataStore<T>) : DataStore<T> {
    private val completedWrites = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val data: Flow<T> = completedWrites
        .flatMapLatest { delegate.data }
        .distinctUntilChanged()

    override suspend fun updateData(transform: suspend (t: T) -> T): T =
        try {
            delegate.updateData(transform)
        } finally {
            // Also after a failed write: a re-read is harmless and never misses a change.
            completedWrites.update { it + 1 }
        }
}
