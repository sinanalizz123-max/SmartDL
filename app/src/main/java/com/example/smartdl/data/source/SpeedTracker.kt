package com.example.smartdl.data.source

import android.os.SystemClock

class SpeedTracker {
    private var lastTimeMs = SystemClock.elapsedRealtime()
    private var lastBytes = 0L
    private var currentSpeed = 0L

    fun update(totalBytesDownloaded: Long): Long {
        val now = SystemClock.elapsedRealtime()
        val deltaTime = now - lastTimeMs
        if (deltaTime >= 500) {
            val deltaBytes = totalBytesDownloaded - lastBytes
            if (deltaTime > 0) {
                currentSpeed = (deltaBytes * 1000L) / deltaTime
            }
            lastTimeMs = now
            lastBytes = totalBytesDownloaded
        }
        return currentSpeed
    }
}
