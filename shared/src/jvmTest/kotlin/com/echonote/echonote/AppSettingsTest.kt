package com.echonote.echonote

import com.russhwolf.settings.PropertiesSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    @Test
    fun cihazKimligiYenidenBaslatmayiAtlatir() {
        // Bu kimlik her açılışta değişirse device_id'ye dayanan çakışma çözümü çöker.
        val backing = PropertiesSettings(Properties())
        val ilk = AppSettings(backing).deviceId

        assertTrue(ilk.isNotBlank())
        assertEquals(ilk, AppSettings(backing).deviceId, "Yeniden başlatma kimliği değiştirmemeli")
    }

    @Test
    fun ayriKurulumlarAyriKimlikAlir() {
        val a = AppSettings(PropertiesSettings(Properties())).deviceId
        val b = AppSettings(PropertiesSettings(Properties())).deviceId

        assertNotEquals(a, b, "İki cihaz aynı kimliği almamalı")
    }
}
