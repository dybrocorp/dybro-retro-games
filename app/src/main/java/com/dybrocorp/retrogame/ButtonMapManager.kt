package com.dybrocorp.retrogame

import android.content.Context
import android.view.KeyEvent

class ButtonMapManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("button_maps", Context.MODE_PRIVATE)

    fun getMapping(keyCode: Int): Int? {
        if (!prefs.contains(keyCode.toString())) return null
        return prefs.getInt(keyCode.toString(), -1).takeIf { it != -1 }
    }

    fun setMapping(keyCode: Int, retroDeviceId: Int) {
        prefs.edit().putInt(keyCode.toString(), retroDeviceId).apply()
    }

    fun clearMapping(keyCode: Int) {
        prefs.edit().remove(keyCode.toString()).apply()
    }
}
