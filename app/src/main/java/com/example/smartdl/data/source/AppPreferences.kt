package com.example.smartdl.data.source

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("smartdl_prefs", Context.MODE_PRIVATE)

    fun getMaxParallel(): Int = prefs.getInt("max_parallel", 3)
    fun setMaxParallel(value: Int) {
        prefs.edit().putInt("max_parallel", value.coerceAtLeast(1)).apply()
    }
}
