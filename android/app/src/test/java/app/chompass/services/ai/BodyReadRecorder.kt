package app.chompass.services.ai

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Records the name of every thread that pulls response-body bytes through [interceptor].
 * On Android, a body read on the main thread that has to wait for the network throws
 * NetworkOnMainThreadException; the JVM tests check the reading thread instead.
 */
internal class BodyReadRecorder {
    val threads = ConcurrentLinkedQueue<String>()
    val firstRead = CountDownLatch(1)

    /** Coroutine debug mode (on under test) appends " @coroutine#N" to thread names. */
    val readOnCaller: Boolean get() = threads.any { it == CALLER_THREAD || it.startsWith("$CALLER_THREAD @") }

    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val body = response.body
        val source = object : ForwardingSource(body!!.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                threads += Thread.currentThread().name
                firstRead.countDown()
                return super.read(sink, byteCount)
            }
        }.buffer()
        response.newBuilder().body(source.asResponseBody(body.contentType(), body.contentLength())).build()
    }
}

/** A single-thread dispatcher standing in for Android's main dispatcher. */
internal fun callerDispatcher(name: String = CALLER_THREAD): ExecutorCoroutineDispatcher =
    Executors.newSingleThreadExecutor { Thread(it, name).apply { isDaemon = true } }.asCoroutineDispatcher()

internal const val CALLER_THREAD = "caller"
