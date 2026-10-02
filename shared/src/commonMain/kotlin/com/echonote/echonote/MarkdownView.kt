package com.echonote.echonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.echonote.echonote.model.relativeTime
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown'ın okunur hâli. Okuma modunda editör hiç oluşturulmaz; uzun notlarda
 * her tuş vuruşundaki yeniden sarma maliyeti de böylece ortadan kalkar.
 *
 * Tasarımın okuma ölçüsü: satır uzunluğu ~65 karakterle sınırlı ([ProseMeasure]) ve
 * geniş ekranda ortalanır. Uzun satır gözün satır başını bulmasını zorlaştırıyor.
 */
@Composable
fun MarkdownView(
    content: String,
    title: String,
    tags: List<String>,
    updatedAt: String,
    onToggleTask: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = echoMarkdownColors()
    val blocks = remember(content) { parseMarkdown(content) }
    val scroll = rememberScrollState()

    // Gövdedeki ilk `#` başlığı zaten üstteki başlık bloğunda gösteriliyor; iki kez
    // yazdırma.
    val firstHeading = remember(blocks) { blocks.indexOfFirst { it is MdBlock.Heading } }
    val firstParagraph = remember(blocks) { blocks.indexOfFirst { it is MdBlock.Paragraph } }

    Column(modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
              val proseWidth = if (maxWidth < ProseMeasure) maxWidth else ProseMeasure
              Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .width(proseWidth)
                    .align(Alignment.TopCenter)
                    .padding(start = ScreenMargin, end = ScreenMargin, top = 24.dp),
              ) {
                ReadHeader(title = title, tags = tags, updatedAt = updatedAt)
                blocks.forEachIndexed { index, block ->
                    if (index == firstHeading) return@forEachIndexed
                    when (block) {
                        is MdBlock.Heading -> Text(
                            text = renderInline(block.text, colors),
                            style = headingStyle(block.level),
                            modifier = Modifier.padding(top = if (block.level <= 2) 10.dp else 4.dp),
                        )

                        is MdBlock.Paragraph -> {
                            val paragraph = renderInline(block.text, colors)
                            // Drop cap yalnızca akan düzyazıda: sert satır sonu taşıyan
                            // bir paragrafta (şiir, adres) büyük harf ilk dizeyi
                            // kopartıyor ve arada boşluk bırakıyor.
                            if (index == firstParagraph && !paragraph.hasHardBreaks()) {
                                DropCapParagraph(paragraph, bodyStyle)
                            } else {
                                Text(text = paragraph, style = bodyStyle.justifiedFor(paragraph))
                            }
                        }

                        is MdBlock.Bullet -> Row {
                            Text(
                                text = block.marker,
                                style = bodyStyle.copy(color = EchoColors.textSecondary),
                                modifier = Modifier.width(26.dp),
                            )
                            Text(text = renderInline(block.text, colors), style = bodyStyle)
                        }

                        is MdBlock.Task -> TaskRow(
                            block = block,
                            colors = colors,
                            onToggle = { onToggleTask(block.lineIndex) },
                        )

                        is MdBlock.Quote -> QuoteCard(renderInline(block.text, colors))

                        is MdBlock.Code -> Text(
                            text = block.code,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = colors.code,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(EchoShapes.field)
                                .background(colors.codeBackground)
                                .padding(14.dp),
                        )

                        MdBlock.Rule -> Box(
                            Modifier.fillMaxWidth().height(1.dp).background(EchoColors.outline),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
              }
            }
        }
        ReadingProgress(
            progress = if (scroll.maxValue <= 0) 1f else scroll.value.toFloat() / scroll.maxValue,
        )
    }
}



/**
 * Okuma modunun başlık bloğu: etiketlerden türeyen üst satır, büyük başlık, etiket
 * çipleri. Ölçüler tasarımdan: başlık 28sp/700, üst satır 10sp büyük harf ve seyrek,
 * çipler px12/py4.
 */
@Composable
private fun ReadHeader(title: String, tags: List<String>, updatedAt: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (tags.isNotEmpty()) {
                Box(Modifier.size(6.dp).background(EchoColors.accent, EchoShapes.pill))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tags.joinToString(" & ") { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.weight(1f))
            val age = relativeTime(updatedAt)
            if (age.isNotEmpty()) {
                Text(
                    text = "Son güncelleme: $age",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.textSecondary.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = title.ifBlank { "Adsız not" },
            style = MaterialTheme.typography.displayMedium,
            color = EchoColors.textPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (tags.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                tags.forEach { tag ->
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(EchoShapes.pill)
                            .background(EchoColors.surface)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = EchoColors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Alıntıyı kendi kartına alır. Tasarımda bu, metnin akışını kesen bir "düşünce
 * parçası": yüzey dolgusu, ince kenarlık ve solda periwinkle şerit.
 */
@Composable
private fun QuoteCard(text: AnnotatedString) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .echoSurface(EchoShapes.card, fill = EchoColors.surfaceLow)
            .padding(end = 18.dp),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(EchoColors.primary))
        Column(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Text(
                text = "DÜŞÜNCE PARÇASI",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.accent,
            )
            Text(
                text = text,
                style = bodyStyle.copy(
                    color = EchoColors.textPrimary,
                    fontStyle = FontStyle.Italic,
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Okuma ilerlemesi. Tasarımda altta ince bir çubuk ve "%100 Okundu" yazıyor; burada
 * yalnızca çubuk var — yüzde, kaydırdıkça sürekli değişen bir sayı olarak metnin
 * dikkatini çalıyordu.
 */
@Composable
private fun ReadingProgress(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(EchoColors.surfaceHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(EchoColors.primary),
        )
    }
}

/**
 * İki yana yaslama yalnızca akan metinde uygulanır.
 *
 * Sert satır sonu taşıyan bir paragraf — şiir, adres, alt alta notlar — yaslanırsa her
 * satır zorla tam genişliğe gerilir ve kelimelerin arası açılır. Böyle bir paragraf
 * soldan hizalı kalır.
 */
private fun TextStyle.justifiedFor(text: AnnotatedString): TextStyle =
    copy(textAlign = if (text.hasHardBreaks()) TextAlign.Start else TextAlign.Justify)

/** Paragraf akan metin mi, yoksa alt alta yazılmış satırlar mı. */
private fun AnnotatedString.hasHardBreaks(): Boolean = text.lineSequence().count() > 1

/**
 * Gerçek drop cap: ilk harf sola oturur, metnin ilk satırları onun sağından akar,
 * kalanı tam genişlikte devam eder.
 *
 * Compose'da CSS'teki `float` yok; bu yüzden metin [rememberTextMeasurer] ile dar
 * genişlikte ölçülüp harfin yanına sığan kısım ile kalanı ayrılıyor. Tasarımın
 * değerleri: 54sp harf, 46sp satır kutusu, 12dp sağ boşluk, serif, terracotta.
 */
@Composable
private fun DropCapParagraph(text: AnnotatedString, style: TextStyle) {
    if (text.isEmpty() || !text.first().isLetter()) {
        Text(text = text, style = style.justifiedFor(text))
        return
    }

    val capStyle = TextStyle(
        fontSize = 54.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Serif,
        color = EchoColors.accent,
    )
    val justified = style.justifiedFor(text)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val capLayout = measurer.measure(AnnotatedString(text.first().toString()), capStyle)
        val capWidth = with(density) { capLayout.size.width.toDp() }
        val lineHeight = with(density) { style.lineHeight.toDp() }
        // Harfin kapladığı satır sayısı: kutusu kaç gövde satırına denk geliyorsa.
        val wrapLines = ((46.dp + lineHeight - 1.dp) / lineHeight).toInt().coerceAtLeast(2)

        val rest = text.subSequence(1, text.length)
        val narrowWidth = (maxWidth - capWidth - 12.dp).coerceAtLeast(40.dp)
        val narrowLayout = measurer.measure(
            text = rest,
            style = justified,
            constraints = Constraints(maxWidth = with(density) { narrowWidth.roundToPx() }),
            maxLines = wrapLines,
        )
        var split = narrowLayout.getLineEnd(
            lineIndex = (narrowLayout.lineCount - 1).coerceAtLeast(0),
            visibleEnd = true,
        ).coerceIn(0, rest.length)
        // Satır sonundaki boşluk kalan bloğun başına düşüp ikinci bloğu içeri
        // kaydırıyordu; sarmalanan son satırdan sonraki boşlukları atla.
        while (split < rest.length && rest[split] == ' ') split++

        Column {
            Row {
                Text(
                    text = text.first().toString(),
                    style = capStyle,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = rest.subSequence(0, split),
                    style = justified,
                    modifier = Modifier.width(narrowWidth),
                )
            }
            if (split < rest.length) {
                Text(
                    text = rest.subSequence(split, rest.length),
                    style = justified,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(block: MdBlock.Task, colors: MarkdownColors, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(vertical = 2.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (block.checked) EchoColors.primary else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (block.checked) EchoColors.primary else EchoColors.textSecondary,
                    shape = RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (block.checked) {
                Text("✓", style = TextStyle(fontSize = 13.sp, color = EchoColors.onPrimary))
            }
        }
        Text(
            text = renderInline(block.text, colors),
            style = if (block.checked) {
                bodyStyle.copy(color = EchoColors.textSecondary)
            } else {
                bodyStyle
            },
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Gövde metni; tasarımın uzun metin ölçüsü (17/28). */
private val bodyStyle: TextStyle
    @Composable @ReadOnlyComposable get() = TextStyle(
        fontSize = 17.sp,
        // Tasarımın uzun metin ölçüsü: leading 1.8.
        lineHeight = 30.sp,
        letterSpacing = (-0.01).sp,
        color = LocalEchoPalette.current.textPrimary,
    )

@Composable
@ReadOnlyComposable
private fun headingStyle(level: Int): TextStyle {
    val size = when (level) {
        1 -> 26.sp
        2 -> 21.sp
        3 -> 18.sp
        else -> 17.sp
    }
    return TextStyle(
        fontSize = size,
        lineHeight = size * 1.3f,
        fontWeight = FontWeight.SemiBold,
        color = LocalEchoPalette.current.textPrimary,
    )
}
