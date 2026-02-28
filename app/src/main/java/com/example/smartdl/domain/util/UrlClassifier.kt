package com.example.smartdl.domain.util

import android.net.Uri

object UrlClassifier {
    private val videoHosts = setOf(
        "youtube.com",
        "youtu.be",
        "instagram.com",
        "facebook.com",
        "fb.watch",
        "twitter.com",
        "x.com",
        "tiktok.com"
    )

    fun isVideoUrl(url: String): Boolean {
        val host = url.hostOrNull() ?: return false
        return videoHosts.any { host == it || host.endsWith(".$it") }
    }
}

fun String.hostOrNull(): String? {
    return try {
        Uri.parse(this).host?.lowercase()
    } catch (e: Exception) {
        null
    }
}

fun String.isVideoUrl(): Boolean = UrlClassifier.isVideoUrl(this)
