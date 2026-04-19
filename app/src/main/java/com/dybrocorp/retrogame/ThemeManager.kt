package com.dybrocorp.retrogame

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppTheme(
    val name: String,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

object ThemeConfigs {
    val Dark = AppTheme(
        name = "Premium Dark",
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        accent = Color(0xFF00E676),
        textPrimary = Color.White,
        textSecondary = Color.Gray
    )
    val Light = AppTheme(
        name = "Clean Light",
        background = Color(0xFFF5F5F5),
        surface = Color.White,
        accent = Color(0xFF2196F3),
        textPrimary = Color.Black,
        textSecondary = Color.DarkGray
    )
    val Retro = AppTheme(
        name = "Retro Arcade",
        background = Color(0xFF2C2C2C),
        surface = Color(0xFF3F3F3F),
        accent = Color(0xFFFF5252),
        textPrimary = Color(0xFFFFF176),
        textSecondary = Color(0xFFE0E0E0)
    )
    val Neon = AppTheme(
        name = "Cyber Neon",
        background = Color(0xFF0D0221),
        surface = Color(0xFF261447),
        accent = Color(0xFFFF3864),
        textPrimary = Color(0xFF00F0FF),
        textSecondary = Color(0xFF8C7AEE)
    )
    
    val allThemes = listOf(Dark, Light, Retro, Neon)
}

class ThemeManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(
        ThemeConfigs.allThemes.find { it.name == prefs.getString("selected_theme", "Premium Dark") } ?: ThemeConfigs.Dark
    )
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("selected_theme", theme.name).apply()
        _currentTheme.value = theme
    }
}
