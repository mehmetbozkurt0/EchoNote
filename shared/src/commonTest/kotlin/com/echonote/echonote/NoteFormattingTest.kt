package com.echonote.echonote

import com.echonote.echonote.model.deriveTitle
import com.echonote.echonote.model.exportFileName
import com.echonote.echonote.model.relativeTime
import com.echonote.echonote.model.textStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class NoteFormattingTest {

    // --- Otomatik başlık ---

    @Test
    fun baslikIlkMarkdownBaslığindanGelir() {
        assertEquals("Alışveriş", deriveTitle("# Alışveriş\n\nkahve, süt"))
        assertEquals("Derin", deriveTitle("### Derin\n\nmetin"))
    }

    @Test
    fun baslikYoksaIlkDoluSatirKullanilir() {
        assertEquals("ilk satır", deriveTitle("\n\n  ilk satır\nikinci"))
    }

    @Test
    fun markdownIsaretleriBasliktanTemizlenir() {
        assertEquals("kalın başlık", deriveTitle("**kalın başlık**\ngövde"))
        assertEquals("madde", deriveTitle("- madde\nbaska"))
        assertEquals("birinci", deriveTitle("1. birinci"))
    }

    @Test
    fun bosIcerikVarsayilanaDuser() {
        assertEquals("Adsız not", deriveTitle(""))
        assertEquals("Adsız not", deriveTitle("   \n\n  "))
        assertEquals("Adsız not", deriveTitle("###   "))
    }

    @Test
    fun cokUzunBaslikKisaltilir() {
        val uzun = "x".repeat(200)
        val title = deriveTitle("# $uzun")
        assertTrue(title.length <= 61, "61 = 60 karakter + üç nokta: ${title.length}")
        assertTrue(title.endsWith("…"))
    }

    // --- Sayaç ---

    @Test
    fun kelimeVeKarakterSayimi() {
        val stats = textStats("bir iki üç")
        assertEquals(3, stats.words)
        assertEquals(10, stats.characters)
    }

    @Test
    fun coklaBoslukVeBosMetinSayimiBozmaz() {
        assertEquals(2, textStats("  bir   iki  ").words)
        assertEquals(0, textStats("").words)
        assertEquals(0, textStats("   \n  ").words)
    }

    @Test
    fun turkceKarakterlerKelimeSayiminiBozmaz() {
        assertEquals(3, textStats("çilek ğüşıö İstanbul").words)
    }

    // --- Göreli zaman ---

    @Test
    fun goreliZamanAraliklariDogruEtiketlenir() {
        val now = Instant.parse("2026-06-15T12:00:00Z")
        fun at(s: String) = relativeTime(s, now)

        assertEquals("az önce", at("2026-06-15T11:59:30Z"))
        assertEquals("5 dk önce", at("2026-06-15T11:55:00Z"))
        assertEquals("3 saat önce", at("2026-06-15T09:00:00Z"))
        assertEquals("dün", at("2026-06-14T10:00:00Z"))
        assertEquals("5 gün önce", at("2026-06-10T12:00:00Z"))
    }

    @Test
    fun cozumlenemeyenDamgaBosDoner() {
        assertEquals("", relativeTime("bozuk-damga"))
    }

    // --- Dışa aktarma ---

    @Test
    fun dosyaAdiGuvenliHaleGetirilir() {
        assertEquals("Alışveriş-listesi.md", exportFileName("Alışveriş listesi"))
        assertEquals("notmd.md", exportFileName("not/\\:*?\"<>|md"))
        assertEquals("not.md", exportFileName("   "))
    }
}
