package com.ezsocial.video

import java.net.URI

object Links {
    fun extract(text: String): String {
        val link = Regex("https?://[^\\s<>\"']+").findAll(text).map { it.value.trimEnd('.', ',', ';', '!', '?', ')', '。') }
            .firstOrNull {
                runCatching { val uri = URI(it); !uri.host.isNullOrBlank() && uri.userInfo == null }.getOrDefault(false)
            }
        return requireNotNull(link) { "Share or paste a complete https:// video link." }
    }
}
