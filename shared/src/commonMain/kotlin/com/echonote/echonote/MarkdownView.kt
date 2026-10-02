package com.echonote.echonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    onToggleTask: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = echoMarkdownColors()
    val blocks = remember(content) { parseMarkdown(content) }
    val scroll = rememberScrollState()

    // İlk paragrafın ilk harfi büyütülür (tasarımdaki "drop cap").
    val firstParagraph = remember(blocks) { blocks.indexOfFirst { it is MdBlock.Paragraph } }

    Column(modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = ProseMeasure).align(Alignment.CenterHorizontally),
            ) {
                blocks.forEachIndexed { index, block ->
                    when (block) {
                        is MdBlock.Heading -> Text(
                            text = renderInline(block.text, colors),
                            style = headingStyle(block.level),
                            modifier = Modifier.padding(top = if (block.level <= 2) 10.dp else 4.dp),
                        )

                        is MdBlock.Paragraph -> Text(
                            text = if (index == firstParagraph) {
                                withInitial(renderInline(block.text, colors))
                            } else {
                                renderInline(block.text, colors)
                            },
                            style = bodyStyle,
                        )

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
        ReadingProgress(
            progress = if (scroll.maxValue <= 0) 1f else scroll.value.toFloat() / scroll.maxValue,
        )
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
 * İlk harfi büyütür. Gerçek bir drop cap'te metin harfin etrafından dolanır; Compose'da
 * bunun taşınabilir karşılığı yok, bu yüzden harf satır içinde büyütülüyor — süslemenin
 * amacı olan "paragraf burada başlıyor" vurgusunu veriyor.
 */
@Composable
private fun withInitial(text: AnnotatedString): AnnotatedString {
    if (text.isEmpty() || !text.first().isLetter()) return text
    val accent = EchoColors.accent
    return buildAnnotatedString {
        append(text)
        addStyle(
            SpanStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold, color = accent),
            start = 0,
            end = 1,
        )
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
        lineHeight = 28.sp,
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
