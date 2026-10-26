package app.chompass.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Reproduces AndroidX b/431787506 with a real file-backed DataStore: the write is held
 * open after DataStore has bumped its version, and a new collector starts in that window.
 */
class ConsistentDataStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private val serializer = GatedIntSerializer()

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @After
    fun tearDown() {
        serializer.release()
        scope.cancel()
    }

    @Test
    fun collectorStartedDuringWriteReceivesTheWrite() = runBlocking {
        val store = ConsistentDataStore(newStore())
        assertEquals(0, store.data.first())

        val late = raceLateCollectorAgainstWrite(store)

        assertEquals(1, withTimeout(10_000) { late.await() })
        assertEquals(1, store.data.first())
    }

    @Test
    fun reReadsAfterAWriteDoNotReEmitEqualValues() = runBlocking {
        val store = ConsistentDataStore(newStore())
        val seen = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val collector = scope.async { store.data.onEach { seen += it }.first { it == 2 } }
        awaitSeen(seen, 0)
        store.updateData { 1 }
        awaitSeen(seen, 1)
        store.updateData { 1 } // no change: DataStore skips the write, the wrapper still re-reads
        delay(200)
        store.updateData { 2 }
        withTimeout(10_000) { collector.await() }
        // DataStore conflates values; each value is waited for, so none is skipped here.
        assertEquals(listOf(0, 1, 2), seen.toList())
    }

    private suspend fun awaitSeen(seen: List<Int>, value: Int) = withTimeout(10_000) {
        while (value !in seen.toList()) delay(10)
    }

    /**
     * Characterizes the upstream bug the wrapper exists for. If this starts failing after a
     * DataStore upgrade, the upgrade fixed b/431787506 and ConsistentDataStore can go.
     */
    @Test
    fun upstreamDataStoreDropsTheWriteForALateCollector() = runBlocking {
        val store = newStore()
        assertEquals(0, store.data.first())

        val late = raceLateCollectorAgainstWrite(store)

        assertNull(
            "DataStore now delivers the write; remove ConsistentDataStore (b/431787506 fixed)",
            withTimeoutOrNull(2_000) { late.await() }
        )
        assertEquals(1, store.data.first()) // the write itself succeeded
    }

    /** Starts a write, holds it after the version bump, starts a new collector, then finishes the write. */
    private suspend fun raceLateCollectorAgainstWrite(store: DataStore<Int>): kotlinx.coroutines.Deferred<Int> {
        serializer.holdWrites()
        val write = scope.async { store.updateData { 1 } }
        withTimeout(10_000) { serializer.writeStarted.await() }

        val firstSeen = CompletableDeferred<Int>()
        val late = scope.async {
            store.data.onEach { firstSeen.complete(it) }.first { it == 1 }
        }
        // The late collector has read the old file while the write is still in progress.
        assertEquals(0, withTimeout(10_000) { firstSeen.await() })

        serializer.release()
        withTimeout(10_000) { write.await() }
        return late
    }

    private fun newStore(): DataStore<Int> = DataStoreFactory.create(
        serializer = serializer,
        scope = scope,
        produceFile = { File(folder.root, "race.store") }
    )

    /** Int serializer whose writes can be held open, as a slow disk would. */
    private class GatedIntSerializer : Serializer<Int> {
        override val defaultValue = 0

        @Volatile private var gate: CompletableDeferred<Unit>? = null
        val writeStarted = CompletableDeferred<Unit>()

        fun holdWrites() {
            gate = CompletableDeferred()
        }

        fun release() {
            gate?.complete(Unit)
        }

        override suspend fun readFrom(input: InputStream): Int = DataInputStream(input).readInt()

        override suspend fun writeTo(t: Int, output: OutputStream) {
            gate?.let {
                writeStarted.complete(Unit)
                it.await()
            }
            DataOutputStream(output).writeInt(t)
        }
    }
}
