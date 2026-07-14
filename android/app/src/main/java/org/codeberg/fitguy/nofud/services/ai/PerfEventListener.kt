package org.codeberg.fitguy.nofud.services.ai

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import org.codeberg.fitguy.nofud.services.PerfLog
import java.io.IOException

/**
 * Debug-only OkHttp [EventListener] that records the latency phases of every
 * call made through the shared client (all AI providers, STT, OpenFoodFacts —
 * they share FoodAnalysisService.defaultClient). One instance is created per
 * call by [Factory], so per-call state lives in plain fields.
 *
 * On completion it emits a single line via [PerfLog.event]:
 *
 *   op=net phase=call host=<h> dnsMs=<n> connectMs=<n> tlsMs=<n> ttfbMs=<n> \
 *     totalMs=<n> reqBytes=<n> respBytes=<n> status=<code>
 *
 * A field is -1 when its phase didn't happen (e.g. a pooled connection skips
 * dns/connect/tls). `status` is -1 on network failure. Installed only in debug
 * builds (see FoodAnalysisService.defaultClient), so it never ships.
 */
class PerfEventListener : EventListener() {
    private var host: String = "?"
    private var callStartNs = 0L
    private var dnsStartNs = 0L
    private var dnsEndNs = 0L
    private var connectStartNs = 0L
    private var connectEndNs = 0L
    private var tlsStartNs = 0L
    private var tlsEndNs = 0L
    private var firstByteNs = 0L
    private var reqBytes = -1L
    private var respBytes = -1L
    private var status = -1

    override fun callStart(call: Call) {
        callStartNs = System.nanoTime()
        host = call.request().url.host
    }

    override fun dnsStart(call: Call, domainName: String) { dnsStartNs = System.nanoTime() }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) {
        dnsEndNs = System.nanoTime()
    }

    override fun connectStart(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) {
        connectStartNs = System.nanoTime()
    }
    override fun connectEnd(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: okhttp3.Protocol?) {
        connectEndNs = System.nanoTime()
    }

    override fun secureConnectStart(call: Call) { tlsStartNs = System.nanoTime() }
    override fun secureConnectEnd(call: Call, handshake: okhttp3.Handshake?) { tlsEndNs = System.nanoTime() }

    override fun requestBodyEnd(call: Call, byteCount: Long) { reqBytes = byteCount }

    override fun responseHeadersStart(call: Call) {
        if (firstByteNs == 0L) firstByteNs = System.nanoTime()
    }
    override fun responseHeadersEnd(call: Call, response: Response) { status = response.code }
    override fun responseBodyEnd(call: Call, byteCount: Long) { respBytes = byteCount }

    override fun callEnd(call: Call) = emit()
    override fun callFailed(call: Call, ioe: IOException) = emit()

    private fun emit() {
        val endNs = System.nanoTime()
        PerfLog.event(
            "op=net phase=call host=$host" +
                " dnsMs=${ms(dnsStartNs, dnsEndNs)}" +
                " connectMs=${ms(connectStartNs, connectEndNs)}" +
                " tlsMs=${ms(tlsStartNs, tlsEndNs)}" +
                " ttfbMs=${ms(callStartNs, firstByteNs)}" +
                " totalMs=${ms(callStartNs, endNs)}" +
                " reqBytes=$reqBytes respBytes=$respBytes status=$status"
        )
    }

    /** Millis between two nanoTime marks, or -1 if either mark is unset. */
    private fun ms(startNs: Long, endNs: Long): Long =
        if (startNs == 0L || endNs == 0L || endNs < startNs) -1 else (endNs - startNs) / 1_000_000

    companion object {
        val Factory = EventListener.Factory { PerfEventListener() }
    }
}
