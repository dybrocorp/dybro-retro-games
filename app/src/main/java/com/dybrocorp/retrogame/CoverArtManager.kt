package com.dybrocorp.retrogame

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CoverArtManager {
    private const val BASE_URL = "https://thumbnails.libretro.com/"

    fun getCoverUrl(game: Game): String {
        game.coverPath?.let { localPath ->
            val file = File(localPath)
            if (file.exists()) {
                return "file://${file.absolutePath}"
            }
        }

        val systemFolder = when (game.system) {
            GameSystem.GBA -> "Nintendo_-_Game_Boy_Advance"
            GameSystem.SNES -> "Nintendo_-_Super_Nintendo_Entertainment_System"
            GameSystem.N64 -> "Nintendo_-_Nintendo_64"
            GameSystem.PSP -> "Sony_-_PlayStation_Portable"
            else -> "Nintendo_-_Nintendo_Entertainment_System"
        }

        val cleanTitle = game.title.replace(Regex("^[0-9\\s-]+"), "")
        
        val encodedName = URLEncoder.encode(cleanTitle, "UTF-8")
            .replace("+", "%20")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%26", "&")
            .replace("%27", "'")

        return "$BASE_URL$systemFolder/Named_Boxarts/$encodedName.png"
    }

    suspend fun downloadAndSaveCover(context: Context, game: Game): String? = withContext(Dispatchers.IO) {
        val romFile = File(game.path)
        val romDir = romFile.parentFile
        val localImgNames = listOf("${game.title}.png", "${game.title}.jpg", "${game.title}.jpeg", 
                                   "${romFile.nameWithoutExtension}.png", "${romFile.nameWithoutExtension}.jpg")
        
        if (romDir != null) {
            for (imgName in localImgNames) {
                val localImage = File(romDir, imgName)
                if (localImage.exists()) {
                    val coversDir = File(context.filesDir, "covers")
                    if (!coversDir.exists()) coversDir.mkdirs()
                    val destFile = File(coversDir, "${game.title}_${game.system.name}.png")
                    localImage.copyTo(destFile, overwrite = true)
                    return@withContext destFile.absolutePath
                }
            }
        }

        try {
            val urlString = getCoverUrl(game)
            if (urlString.startsWith("file://")) return@withContext urlString.removePrefix("file://")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val coversDir = File(context.filesDir, "covers")
                if (!coversDir.exists()) coversDir.mkdirs()

                val coverFile = File(coversDir, "${game.title}_${game.system.name}.png")
                
                connection.inputStream.use { input ->
                    FileOutputStream(coverFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext coverFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
