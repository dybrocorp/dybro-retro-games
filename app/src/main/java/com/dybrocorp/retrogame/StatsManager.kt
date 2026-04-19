package com.dybrocorp.retrogame

import android.content.Context
import android.content.SharedPreferences

class StatsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("retro_stats", Context.MODE_PRIVATE)

    private var currentGameStartTime: Long = 0L
    private var currentGameName: String? = null

    // Tracking Playtime
    fun startGame(gameName: String) {
        if (currentGameName == gameName) return // Ya está corriendo
        currentGameName = gameName
        currentGameStartTime = System.currentTimeMillis()
    }

    fun stopGame() {
        currentGameName?.let {
            val elapsedMillis = System.currentTimeMillis() - currentGameStartTime
            val currentSeconds = prefs.getLong("time_$it", 0L)
            val globalSeconds = prefs.getLong("time_global", 0L)
            
            prefs.edit()
                .putLong("time_$it", currentSeconds + (elapsedMillis / 1000))
                .putLong("time_global", globalSeconds + (elapsedMillis / 1000))
                .apply()
        }
        currentGameName = null
    }

    fun getGlobalPlaytimeFormatted(): String {
        val totalSeconds = prefs.getLong("time_global", 0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun getMostPlayedGame(): String {
        var bestGame = "Ninguno"
        var maxTime = 0L
        for (entry in prefs.all) {
            if (entry.key.startsWith("time_") && entry.key != "time_global") {
                val gameName = entry.key.removePrefix("time_")
                val time = entry.value as Long
                if (time > maxTime) {
                    maxTime = time
                    bestGame = gameName
                }
            }
        }
        return if (maxTime > 0) bestGame else "Aún sin datos"
    }
}
