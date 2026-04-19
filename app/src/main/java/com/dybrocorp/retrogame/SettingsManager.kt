package com.dybrocorp.retrogame

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val useJoystick: Boolean = false,
    val buttonOpacity: Float = 1.0f,
    val gamepadSkin: String = "Classic"
)

class SettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _currentSettings = MutableStateFlow(
        AppSettings(
            useJoystick = prefs.getBoolean("use_joystick", false),
            buttonOpacity = prefs.getFloat("button_opacity", 1.0f),
            gamepadSkin = prefs.getString("gamepad_skin", "Classic") ?: "Classic"
        )
    )
    val currentSettings: StateFlow<AppSettings> = _currentSettings.asStateFlow()

    fun updateSettings(settings: AppSettings) {
        prefs.edit()
            .putBoolean("use_joystick", settings.useJoystick)
            .putFloat("button_opacity", settings.buttonOpacity)
            .putString("gamepad_skin", settings.gamepadSkin)
            .apply()
        _currentSettings.value = settings
    }
}
