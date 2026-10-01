package com.echonote.echonote

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * [highlightMarkdown] her tuş vuruşunda tüm metin üzerinde çalışıyor. Bu testin amacı
 * iki şey:
 *
 * 1. Maliyeti **ölçmek** — "yavaş olmalı" sezgisiyle optimize etmemek için.
 * 2. Bir **tripwire** bırakmak: notlar büyüyüp maliyet eşiği aşarsa test kırmızıya
 *    döner ve kullanıcı yavaşlamayı hissetmeden önce haber verir.
 *
 * Örnek metin, gerçek kullanımın biçimine göre üretiliyor: uzun düz paragraflar,
 * çok az markdown işareti (gerçek notlarda 30 KB metinde yalnızca 14 başlık ve
 * 11 liste maddesi var, hiç kalın/italik/kod/alıntı yok).
 */
class MarkdownHighlighterBenchmarkTest {

    private val colors = MarkdownColors(
        heading = Color.Cyan,
        marker = Color.Gray,
        code = Color.Green,
        codeBackground = Color.DarkGray,
        quote = Color.Magenta,
    )

    /** Gerçek en uzun notun biçimi: ~3.5 KB, 4 satır, neredeyse hiç işaret yok. */
    private fun longProseNote(): String {
        val sentence = "Bunu yazarken ne hissettigimi tam olarak bilmiyorum ama yazmak " +
            "dusunmenin yarisi gibi geliyor ve devam etmek istiyorum. "
        val paragraph = sentence.repeat(7)
        return buildString {
            appendLine("# Baslik")
            appendLine()
            append(paragraph)
            append("\n\n")
            append(paragraph)
            append("\n\n")
            append(paragraph)
        }
    }

    @Test
    fun gercekNotBoyutundaVurgulamaMaliyetiEsiginAltinda() {
        val text = longProseNote()
        assertTrue(text.length in 2_500..6_000, "Örnek metin gerçekçi boyutta olmalı: ${text.length}")

        // Isınma: JIT derlemesi ölçümü kirletmesin.
        repeat(200) { highlightMarkdown(text, colors) }

        val iterations = 500
        val elapsed = measureTime {
            repeat(iterations) { highlightMarkdown(text, colors) }
        }
        val perCallMicros = elapsed.inWholeMicroseconds.toDouble() / iterations
        println("[ÖLÇÜM] highlightMarkdown: ${"%.1f".format(perCallMicros)} µs/çağrı (${text.length} karakter)")

        // 60 fps'te bir karenin bütçesi 16_667 µs. Vurgulama bunun %10'unu
        // (1_667 µs) geçiyorsa yazarken gerçekten kare düşürüyor demektir.
        assertTrue(
            perCallMicros < 1_667,
            "Markdown vurgulaması kare bütçesinin %10'unu aşıyor: $perCallMicros µs. " +
                "Bu noktada tek geçişli regex / sonuç cache'i gerekir.",
        )
    }

    @Test
    fun markdownYogunMetindeDeEsiginAltinda() {
        // Yoğun işaretli metin: ileride kullanım değişirse diye üst sınır kontrolü.
        val dense = buildString {
            repeat(60) { i ->
                appendLine("## Bolum $i")
                appendLine("- madde **kalin** ve *italik* ve `kod`")
                appendLine("> alinti satiri")
                appendLine()
            }
        }
        repeat(100) { highlightMarkdown(dense, colors) }

        val iterations = 200
        val elapsed = measureTime { repeat(iterations) { highlightMarkdown(dense, colors) } }
        val perCallMicros = elapsed.inWholeMicroseconds.toDouble() / iterations
        println("[ÖLÇÜM] yoğun markdown: ${"%.1f".format(perCallMicros)} µs/çağrı (${dense.length} karakter)")

        assertTrue(perCallMicros < 5_000, "Yoğun markdown çok pahalı: $perCallMicros µs")
    }
}
