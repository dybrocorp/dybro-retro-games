package com.dybrocorp.retrogame

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val useJoystick: Boolean = false,
    val buttonOpacity: Float = 1.0f,
    val gamepadSkin: String = "Clásico",
    val speedMultiplier: Float = 1.0f,
    val autoSaveMinutes: Int = 0,      // 0 = desactivado
    val aspectRatio: String = "4:3"   // "4:3", "16:9", "Stretch"
)

class SettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _currentSettings = MutableStateFlow(
        AppSettings(
            useJoystick = prefs.getBoolean("use_joystick", false),
            buttonOpacity = prefs.getFloat("button_opacity", 1.0f),
            gamepadSkin = prefs.getString("gamepad_skin", "Clásico") ?: "Clásico",
            speedMultiplier = prefs.getFloat("speed_multiplier", 1.0f),
            autoSaveMinutes = prefs.getInt("auto_save_minutes", 0),
            aspectRatio = prefs.getString("aspect_ratio", "4:3") ?: "4:3"
        )
    )
    val currentSettings: StateFlow<AppSettings> = _currentSettings.asStateFlow()

    fun updateSettings(settings: AppSettings) {
        prefs.edit()
            .putBoolean("use_joystick", settings.useJoystick)
            .putFloat("button_opacity", settings.buttonOpacity)
            .putString("gamepad_skin", settings.gamepadSkin)
            .putFloat("speed_multiplier", settings.speedMultiplier)
            .putInt("auto_save_minutes", settings.autoSaveMinutes)
            .putString("aspect_ratio", settings.aspectRatio)
            .apply()
        _currentSettings.value = settings
    }
}
