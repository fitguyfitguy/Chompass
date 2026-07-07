package org.codeberg.fitguy.nofud.services.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Minimal wrapper over [MediaRecorder] for voice-to-text.
 * Writes 16 kHz mono AAC to cache dir for the remote STT providers.
 */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(): File? = runCatching {
        val dir = File(context.cacheDir, "fudai-stt").apply { mkdirs() }
        val file = File(dir, "rec-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16_000)
        r.setAudioChannels(1)
        r.setAudioEncodingBitRate(64_000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        output = file
        file
    }.getOrNull()

    /** Stop + release. Returns the recorded file or null on failure. */
    fun stop(): File? {
        val r = recorder ?: return null
        val result = runCatching {
            r.stop()
            output
        }.getOrNull()
        runCatching { r.reset() }
        runCatching { r.release() }
        recorder = null
        val f = result ?: output
        output = null
        return f
    }

    fun cancel() {
        val r = recorder ?: return
        runCatching { r.stop() }
        runCatching { r.reset() }
        runCatching { r.release() }
        recorder = null
        output?.let { runCatching { it.delete() } }
        output = null
    }
}
