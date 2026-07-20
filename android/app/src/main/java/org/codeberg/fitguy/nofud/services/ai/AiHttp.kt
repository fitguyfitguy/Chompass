package org.codeberg.fitguy.nofud.services.ai

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object AiHttp {
    fun clientWithReadTimeout(base: OkHttpClient, seconds: Int): OkHttpClient =
        base.newBuilder()
            .readTimeout(seconds.toLong(), TimeUnit.SECONDS)
            .build()

    fun sanitizeApiKey(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }
}
