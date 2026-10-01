package com.echonote.echonote

import com.russhwolf.settings.PropertiesSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSettingsTest {

    @Test
    fun anahtarYazilirOkunurVeSilinir() {
        val settings = AppSettings(PropertiesSettings(Properties()))

        assertTrue(settings.geminiApiKey.value.isEmpty())

        settings.setGeminiApiKey("  AIza-test-key  ")
        assertEquals("AIza-test-key", settings.geminiApiKey.value, "Kopyala-yapıştır boşlukları kırpılmalı")

        settings.setGeminiApiKey("")
        assertTrue(settings.geminiApiKey.value.isEmpty())
    }

    @Test
    fun anahtarAyniOrnekteKaliciDepodanOkunur() {
        val backing = PropertiesSettings(Properties())
        AppSettings(backing).setGeminiApiKey("kalici-anahtar")

        // Yeni örnek = uygulamanın yeniden başlatılması.
        assertEquals("kalici-anahtar", AppSettings(backing).geminiApiKey.value)
    }
}
