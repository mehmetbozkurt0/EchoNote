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

    private companion object {
        const val KEY_GEMINI = "gemini_api_key"
    }
}
