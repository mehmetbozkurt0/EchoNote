package com.echonote.echonote

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Markdown'ı okunur bloklara çevirir. Saf fonksiyon: Compose'a bağlı değil, test edilebilir.
 *
 * Kütüphane kullanılmıyor — bu projenin bağımlılık ağı kırılgan (sürüm kataloğu baştan
 * sona sabitleme yorumlarıyla dolu) ve desteklenen alt küme küçük. [MarkdownHighlighter]
 * ile aynı söz dizimini tanır; fark, burada işaretlerin gizlenip biçime dönüşmesi.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val marker: String) : MdBlock

    /** Görev kutusu. [lineIndex] ham metindeki satır; işaretleme onu yeniden yazar. */
    data class Task(val text: String, val checked: Boolean, val lineIndex: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val code: String) : MdBlock
    data object Rule : MdBlock
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val TASK = Regex("""^\s*[-+*]\s+\[([ xX])]\s+(.*)$""")
private val BULLET = Regex("""^\s*([-+*])\s+(.*)$""")
private val ORDERED = Regex("""^\s*(\d+[.)])\s+(.*)$""")
private val QUOTE = Regex("""^>\s?(.*)$""")
private val RULE = Regex("""^\s*([-*_])\1{2,}\s*$""")
private const val FENCE = "```"

fun parseMarkdown(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = raw.lines()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]

        if (line.trimStart().startsWith(FENCE)) {
            flushParagraph()
            val code = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith(FENCE)) {
                code.appendLine(lines[index])
                index++
            }
            blocks += MdBlock.Code(code.toString().trimEnd())
            index++ // kapanış çitini atla
            continue
        }

        when {
            line.isBlank() -> flushParagraph()

            RULE.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }

            HEADING.matches(line) -> {
                flushParagraph()
                val m = HEADING.find(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }

            TASK.matches(line) -> {
                flushParagraph()
                val m = TASK.find(line)!!
                blocks += MdBlock.Task(
                    text = m.groupValues[2],
                    checked = m.groupValues[1].lowercase() == "x",
                    lineIndex = index,
                )
            }

            ORDERED.matches(line) -> {
                flushParagraph()
                val m = ORDERED.find(line)!!
                blocks += MdBlock.Bullet(text = m.groupValues[2], marker = m.groupValues[1])
            }

            BULLET.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Bullet(text = BULLET.find(line)!!.groupValues[2], marker = "•")
            }

            QUOTE.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Quote(QUOTE.find(line)!!.groupValues[1])
            }

            else -> {
                // Tek Enter sert satır sonu sayılır, boşluğa dönüştürülmez.
                //
                // CommonMark burada satırları boşlukla birleştirir ve yeni paragraf için
                // boş satır bekler. Bu bir not defteri: alt alta yazılan bir şiir, adres
                // ya da liste, yazıldığı gibi görünmeli. Boş satır yine paragrafları
                // ayırıyor, yalnızca satır içi birleştirme kalktı.
                if (paragraph.isNotEmpty()) paragraph.appendLine()
                paragraph.append(line.trim())
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}

private val BOLD = Regex("""\*\*([^*\n]+)\*\*""")
private val ITALIC = Regex("""(?<![*\w])\*([^*\n]+)\*(?![*\w])""")
private val CODE = Regex("""`([^`\n]+)`""")
private val LINK = Regex("""\[([^\]\n]+)]\(([^)\s]+)\)""")

/**
 * Satır içi biçimlendirme. Vurgulama modundan farkı: işaretler (`**`, `*`, backtick,
 * bağlantı söz dizimi) **silinir**, yerine biçim gelir.
 *
 * Bağlantılar `"link"` etiketiyle annotate edilir; tıklama hedefini oradan okunur.
 */
fun renderInline(text: String, colors: MarkdownColors): AnnotatedString = buildAnnotatedString {
    var rest = text
    // Önce bağlantılar: metni kısaltıp yerine etiketli parça koyuyoruz.
    while (true) {
        val link = LINK.find(rest) ?: break
        appendStyled(rest.substring(0, link.range.first), colors)
        pushStringAnnotation("link", link.groupValues[2])
        withStyleSpan(SpanStyle(color = colors.heading, fontWeight = FontWeight.Medium)) {
            append(link.groupValues[1])
        }
        pop()
        rest = rest.substring(link.range.last + 1)
    }
    appendStyled(rest, colors)
}

private inline fun AnnotatedString.Builder.withStyleSpan(style: SpanStyle, body: () -> Unit) {
    pushStyle(style)
    body()
    pop()
}

/**
 * Bağlantı dışındaki satır içi biçimleri uygular ve **işaretleri siler** — okuma modunda
 * `**kalin**` değil kalin görünür. (Düzenleme modundaki [MarkdownTransformation] tam
 * tersini yapar: işaretleri bırakır, çünkü orada metin düzenlenebilir olmalı.)
 *
 * Metni baştan sona tarayıp en yakın eşleşmeyi alır; iç içe geçmiş biçimler desteklenmez,
 * ilk eşleşen kazanır.
 */
private fun AnnotatedString.Builder.appendStyled(text: String, colors: MarkdownColors) {
    var rest = text
    while (rest.isNotEmpty()) {
        val next = listOfNotNull(
            BOLD.find(rest)?.let { it to SpanStyle(fontWeight = FontWeight.Bold) },
            ITALIC.find(rest)?.let { it to SpanStyle(fontStyle = FontStyle.Italic) },
            CODE.find(rest)?.let {
                it to SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = colors.code,
                    background = colors.codeBackground,
                )
            },
        ).minByOrNull { it.first.range.first }

        if (next == null) {
            append(rest)
            return
        }
        val (match, style) = next
        append(rest.substring(0, match.range.first))
        pushStyle(style)
        append(match.groupValues[1])
        pop()
        rest = rest.substring(match.range.last + 1)
    }
}

/**
 * Görev kutusunu işaretler/kaldırır ve **ham metni** döndürür. Satır numarasıyla
 * çalışır çünkü aynı metin birden fazla kez geçebilir.
 */
fun toggleTask(raw: String, lineIndex: Int): String {
    val lines = raw.lines().toMutableList()
    val line = lines.getOrNull(lineIndex) ?: return raw
    val m = TASK.find(line) ?: return raw
    val checked = m.groupValues[1].lowercase() == "x"
    val open = line.indexOf('[')
    if (open < 0 || open + 1 >= line.length) return raw
    lines[lineIndex] = line.substring(0, open + 1) + (if (checked) " " else "x") + line.substring(open + 2)
    return lines.joinToString("\n")
}
