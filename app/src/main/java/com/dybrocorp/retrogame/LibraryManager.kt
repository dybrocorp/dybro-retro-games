package com.dybrocorp.retrogame

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class Game(
    val title: String,
    val path: String,
    val system: GameSystem,
    val coverPath: String? = null
)

enum class GameSystem(val displayName: String, val extensions: List<String>) {
    GBA("GameBoy Advance", listOf("gba")),
    SNES("Super Nintendo", listOf("sfc", "smc")),
    N64("Nintendo 64", listOf("n64", "z64", "v64")),
    PSP("PlayStation Portable", listOf("iso", "pbp")),
    NES("Nintendo (NES)", listOf("nes")),
    UNKNOWN("Desconocido", emptyList());

    companion object {
        fun fromExtension(ext: String): GameSystem {
            return values().firstOrNull { it.extensions.contains(ext.lowercase()) } ?: UNKNOWN
        }
    }
}

class LibraryManager {
    fun scanDirectory(dir: File): List<Game> {
        val games = mutableListOf<Game>()
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val ext = file.extension
                val system = GameSystem.fromExtension(ext)
                if (system != GameSystem.UNKNOWN) {
                    games.add(Game(file.nameWithoutExtension, file.absolutePath, system))
                }
            } else if (file.isDirectory) {
                games.addAll(scanDirectory(file))
            }
        }
        return games
    }

    fun saveLibrary(context: Context, games: List<Game>) {
        val gson = Gson()
        val json = gson.toJson(games)
        context.openFileOutput("library.json", Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    fun loadLibrary(context: Context): List<Game> {
        val file = File(context.filesDir, "library.json")
        if (!file.exists()) return emptyList()
        
        return try {
            val json = file.readText()
            val type = object : TypeToken<List<Game>>() {}.type
            val games: List<Game> = Gson().fromJson(json, type)
            // Filtrar juegos cuyo archivo ya no existe O tienen sistema desconocido
            games.filter { File(it.path).exists() && it.system != GameSystem.UNKNOWN }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeGame(context: Context, gameToRemove: Game) {
        val currentGames = loadLibrary(context).toMutableList()
        currentGames.removeAll { it.path == gameToRemove.path }
        saveLibrary(context, currentGames)
    }
}
