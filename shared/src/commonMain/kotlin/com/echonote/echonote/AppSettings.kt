package com.echonote.echonote

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cihaza özel ayarlar (Android: SharedPreferences, desktop: java.util.prefs).
 *
 * Gemini anahtarı artık burada yaşıyor; eskiden `Secrets.kt`'de derlenmiş sabit olarak
 * APK'nın içinde gidiyordu ve binary'yi eline geçiren herkes çıkarabiliyordu.
 */
class AppSettings(private val settings: Settings = Settings()) {

    private val _geminiApiKey = MutableStateFlow(settings.getStringOrNull(KEY_GEMINI).orEmpty())
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun setGeminiApiKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) settings.remove(KEY_GEMINI) else settings.putString(KEY_GEMINI, trimmed)
        _geminiApiKey.value = trimmed
    }

    // --- Görünüm ---

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == settings.getStringOrNull(KEY_THEME) } ?: ThemeMode.System
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        settings.putString(KEY_THEME, mode.name)
        _themeMode.value = mode
    }

    private val _fontScale = MutableStateFlow(
        settings.getFloatOrNull(KEY_FONT_SCALE)?.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) ?: 1f
    )

    /** Tüm `sp` değerlerini ölçekler; erişilebilirlik için tek kaldıraç. */
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        settings.putFloat(KEY_FONT_SCALE, clamped)
        _fontScale.value = clamped
    }

    // --- Masaüstü pencere durumu ---

    fun windowState(): WindowPlacement? {
        val w = settings.getIntOrNull(KEY_WIN_W) ?: return null
        val h = settings.getIntOrNull(KEY_WIN_H) ?: return null
        return WindowPlacement(
            width = w,
            height = h,
            x = settings.getIntOrNull(KEY_WIN_X),
            y = settings.getIntOrNull(KEY_WIN_Y),
        )
    }

    fun saveWindowState(placement: WindowPlacement) {
        settings.putInt(KEY_WIN_W, placement.width)
        settings.putInt(KEY_WIN_H, placement.height)
        placement.x?.let { settings.putInt(KEY_WIN_X, it) }
        placement.y?.let { settings.putInt(KEY_WIN_Y, it) }
    }

    private companion object {
        const val KEY_GEMINI = "gemini_api_key"
        const val KEY_THEME = "theme_mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_WIN_W = "window_width"
        const val KEY_WIN_H = "window_height"
        const val KEY_WIN_X = "window_x"
        const val KEY_WIN_Y = "window_y"
    }
}

enum class ThemeMode { System, Dark, Light }

data class WindowPlacement(val width: Int, val height: Int, val x: Int?, val y: Int?)

const val MIN_FONT_SCALE = 0.8f
const val MAX_FONT_SCALE = 1.6f
