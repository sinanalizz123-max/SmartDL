package com.example.smartdl.data.source

object OrphanCache {
    @Volatile
    var lastScan: List<String> = emptyList()
}
