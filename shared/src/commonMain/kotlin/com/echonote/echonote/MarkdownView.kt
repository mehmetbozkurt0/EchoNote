package com.echonote.echonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown'ın okunur hâli. Okuma modunda editör hiç oluşturulmaz; uzun notlarda
 * her tuş vuruşundaki yeniden sarma maliyeti de böylece ortadan kalkar.
 */
@Composable
fun MarkdownView(
    content: String,
    onToggleTask: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = remember { echoMarkdownColors() }
    val blocks = remember(content) { parseMarkdown(content) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = renderInline(block.text, colors),
                    style = TextStyle(
                        fontSize = headingSize(block.level),
                        fontWeight = FontWeight.Bold,
                        color = EchoColors.NeonCyan,
                        lineHeight = headingSize(block.level) * 1.3f,
                    ),
                    modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = renderInline(block.text, colors),
                    style = bodyStyle,
                )

                is MdBlock.Bullet -> Row {
                    Text(
                        text = block.marker,
                        style = bodyStyle.copy(color = EchoColors.TextSecondary),
                        modifier = Modifier.width(26.dp),
                    )
                    Text(text = renderInline(block.text, colors), style = bodyStyle)
                }

                is MdBlock.Task -> TaskRow(
                    block = block,
                    colors = colors,
                    onToggle = { onToggleTask(block.lineIndex) },
                )

                is MdBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .background(EchoColors.NeonLavender.copy(alpha = 0.7f)),
                    )
                    Text(
                        text = renderInline(block.text, colors),
                        style = bodyStyle.copy(
                            color = EchoColors.NeonLavender,
                            fontStyle = FontStyle.Italic,
                        ),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

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
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.codeBackground)
                        .padding(12.dp),
                )

                MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(EchoColors.GlassBorder)
                        .padding(vertical = 6.dp),
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
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (block.checked) EchoColors.NeonMint.copy(alpha = 0.25f) else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (block.checked) EchoColors.NeonMint else EchoColors.TextSecondary,
                    shape = RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (block.checked) {
                Text("✓", style = TextStyle(fontSize = 12.sp, color = EchoColors.NeonMint))
            }
        }
        Text(
            text = renderInline(block.text, colors),
            style = if (block.checked) {
                bodyStyle.copy(color = EchoColors.TextSecondary)
            } else {
                bodyStyle
            },
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

private val bodyStyle = TextStyle(
    fontSize = 15.sp,
    lineHeight = 24.sp,
    color = EchoColors.TextPrimary,
)

private fun headingSize(level: Int) = when (level) {
    1 -> 24.sp
    2 -> 20.sp
    3 -> 18.sp
    else -> 16.sp
}
