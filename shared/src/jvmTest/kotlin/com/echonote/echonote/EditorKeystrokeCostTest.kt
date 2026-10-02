package com.echonote.echonote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Editördeki gecikmenin **not uzunluğuyla birlikte** arttığını biliyoruz; bu test o
 * artışın nereden geldiğini ayrıştırır.
 *
 * `BasicTextField(value: String, onValueChange)` imzasında her tuş vuruşu şunu yapar:
 *
 * 1. Yeni bir `String` üretilir (tüm metin kopyalanır) — O(n)
 * 2. Değer ViewModel'deki StateFlow'a gider ve geri döner — O(n) bir kopya daha
 * 3. `VisualTransformation` tüm metni yeniden vurgular — O(n)
 *
 * Üçü de metin uzunluğuna doğrusal. Buradaki amaç bu doğrusallığı **ölçmek**: 3 KB'de
 * ihmal edilebilir olan maliyet 30 KB'de kare bütçesinin ne kadarını yiyor?
 *
 * ## Ölçüm sonucu (masaüstü JVM)
 *
 * | Metin | Tuş başına | Kare bütçesi |
 * |---|---|---|
 * | 3 KB | 103 µs | %0.6 |
 * | 10 KB | 299 µs | %1.8 |
 * | 30 KB | 878 µs | %5.3 |
 * | 60 KB | 1806 µs | %10.8 |
 *
 * Doğrusallık doğrulandı (19x metin → 17.5x maliyet). **Ama sonuç negatif:** bu
 * depodaki en uzun gerçek not 3 550 karakter, ortanca 1 252. O boyutta bu yolun
 * tamamı bir karenin %0.6'sı. Yani `BasicTextField`'in `String` aşırı yüklemesi,
 * kullanıcının hissettiği uzunluğa bağlı gecikmenin sebebi **değil** —
 * `TextFieldState` göçü bunu düzeltmez.
 *
 * Geriye kalan doğrusal maliyet Compose'un kendi metin yerleşiminde; o ancak gerçek
 * cihazda kare süresiyle ölçülebilir. Bu test bir tripwire olarak kalıyor: notlar
 * 30 KB'ı aşarsa bu yol gerçekten pahalı hale gelir.
 */
class EditorKeystrokeCostTest {

    private val colors = MarkdownColors(
        heading = Color.Cyan,
        marker = Color.Gray,
        code = Color.Green,
        codeBackground = Color.DarkGray,
        quote = Color.Magenta,
    )

    private fun prose(approximateChars: Int): String {
        val sentence = "Bunu yazarken ne hissettigimi tam olarak bilmiyorum ama yazmak " +
            "dusunmenin yarisi gibi geliyor ve devam etmek istiyorum. "
        return buildString {
            appendLine("# Baslik")
            appendLine()
            while (length < approximateChars) {
                append(sentence)
                if (length % 900 < sentence.length) append("\n\n")
            }
        }
    }

    /** Bir tuş vuruşunun mevcut mimaride yaptığı işin ölçülebilir kısmı. */
    private fun oneKeystroke(text: String, caret: Int): Int {
        // 1 + 2: metnin yeniden kurulması (String aşırı yüklemesi + StateFlow turu)
        val edited = text.substring(0, caret) + "x" + text.substring(caret)
        val roundTripped = AnnotatedString(edited).text
        // 3: tüm metnin yeniden vurgulanması
        return highlightMarkdown(roundTripped, colors).length
    }

    @Test
    fun tusVurusuMaliyetiNotUzunluguylaOlculur() {
        val boyutlar = listOf(3_000, 10_000, 30_000, 60_000)
        val sonuclar = mutableListOf<Pair<Int, Double>>()

        for (hedef in boyutlar) {
            val text = prose(hedef)
            val caret = text.length / 2

            repeat(200) { oneKeystroke(text, caret) } // ısınma: JIT

            // Turun **en iyisi** alınıyor, ortalaması değil. Bu bir tripwire; makine
            // paralel bir derlemeyle meşgulken ortalama 3-4 katına çıkıp yanlış alarm
            // veriyordu. En küçük ölçüm, dış yükten en az kirlenmiş olanıdır.
            val iterations = 200
            val micros = (1..5).minOf { _ ->
                val elapsed = measureTime { repeat(iterations) { oneKeystroke(text, caret) } }
                elapsed.inWholeMicroseconds.toDouble() / iterations
            }
            sonuclar += text.length to micros
            println(
                "[ÖLÇÜM] ${text.length} karakter → ${"%.0f".format(micros)} µs/tuş " +
                    "(kare bütçesinin %${"%.1f".format(micros / 16_667 * 100)}'i)"
            )
        }

        // Doğrusallığı raporla: maliyet gerçekten uzunlukla mı artıyor?
        val (kucukLen, kucukUs) = sonuclar.first()
        val (buyukLen, buyukUs) = sonuclar.last()
        println(
            "[ÖLÇÜM] ${buyukLen / kucukLen}x metin → ${"%.1f".format(buyukUs / kucukUs)}x maliyet"
        )

        // Tripwire: en uzun gerçekçi notta bile tek tuş, kare bütçesinin yarısını
        // aşmamalı. Aşıyorsa artımlı vurgulama (yalnız değişen satır) şart demektir.
        assertTrue(
            buyukUs < 8_333,
            "60 KB notta tuş başına $buyukUs µs — kare bütçesinin yarısından fazlası.",
        )
    }
}
