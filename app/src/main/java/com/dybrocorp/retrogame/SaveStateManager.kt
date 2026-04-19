package com.dybrocorp.retrogame

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SaveSlot(
    val index: Int,
    val exists: Boolean,
    val timestamp: String,
    val filePath: String
)

class SaveStateManager(private val context: Context) {

    fun slotFile(gamePath: String, slot: Int): File {
        val name = File(gamePath).nameWithoutExtension
        val dir = File(context.filesDir, "saves")
        dir.mkdirs()
        return File(dir, "${name}.state${slot}")
    }

    fun listSlots(gamePath: String): List<SaveSlot> {
        val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        return (0..9).map { i ->
            val f = slotFile(gamePath, i)
            SaveSlot(
                index = i,
                exists = f.exists(),
                timestamp = if (f.exists()) fmt.format(Date(f.lastModified())) else "Vacío",
                filePath = f.absolutePath
            )
        }
    }

    fun delete(gamePath: String, slot: Int) {
        slotFile(gamePath, slot).delete()
    }
}
