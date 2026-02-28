package com.example.smartdl.presentation.ui

import kotlin.math.ln
import kotlin.math.pow

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    val digitGroups = (ln(bytesPerSec.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytesPerSec / 1024.0.pow(digitGroups.toDouble())
    return String.format("%.1f %s", value, units[digitGroups])
}
