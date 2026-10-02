package com.echonote.echonote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** Biçimlendirme eylemi: imlecin bulunduğu yere Markdown işareti uygular. */
enum class MarkdownAction { H1, H2, Bold, Italic, Quote, Bullet, Task }

/**
 * Editörün altındaki biçimlendirme çubuğu.
 *
 * Tasarımda bir de "Görsel" düğmesi var; uygulamada dosya eki diye bir şey yok, o yüzden
 * burada yok — çalışmayan bir düğme koymaktansa hiç koymamak daha dürüst.
 */
@Composable
fun MarkdownToolbar(
    onAction: (MarkdownAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .echoSurface(EchoShapes.pill, fill = EchoColors.surfaceLow)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        item { ToolbarText("H1", "Başlık 1", enabled) { onAction(MarkdownAction.H1) } }
        item { ToolbarText("H2", "Başlık 2", enabled) { onAction(MarkdownAction.H2) } }
        item { ToolbarIcon(Icons.Default.FormatBold, "Kalın", enabled) { onAction(MarkdownAction.Bold) } }
        item { ToolbarIcon(Icons.Default.FormatItalic, "İtalik", enabled) { onAction(MarkdownAction.Italic) } }
        item { ToolbarIcon(Icons.Default.FormatQuote, "Alıntı", enabled) { onAction(MarkdownAction.Quote) } }
        item {
            ToolbarIcon(Icons.AutoMirrored.Filled.FormatListBulleted, "Liste", enabled) {
                onAction(MarkdownAction.Bullet)
            }
        }
        item { ToolbarIcon(Icons.Default.CheckBox, "Görev kutusu", enabled) { onAction(MarkdownAction.Task) } }
    }
}

@Composable
private fun ToolbarText(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    ToolbarSlot(description, enabled, onClick) { tint ->
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    ToolbarSlot(description, enabled, onClick) { tint ->
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ToolbarSlot(
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = if (enabled) EchoColors.textSecondary else EchoColors.textSecondary.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .pressScale(interaction)
            // 44dp: erişilebilirlik için dokunma hedefi, görsel boyut daha küçük.
            .size(44.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}

/**
 * Biçimlendirmeyi metne uygular ve imleci mantıklı yere bırakır.
 *
 * İki ayrı davranış var:
 * - **Satır işaretleri** (başlık, alıntı, liste, görev): imlecin bulunduğu satırın
 *   başına eklenir; aynı işaret zaten varsa kaldırılır (aç/kapa).
 * - **Sarmalayıcılar** (kalın, italik): seçim varsa seçimi sarar, yoksa boş bir çift
 *   koyup imleci ortasına alır — kullanıcı devamında yazmaya başlayabilsin.
 */
fun applyMarkdown(value: TextFieldValue, action: MarkdownAction): TextFieldValue = when (action) {
    MarkdownAction.Bold -> wrap(value, "**")
    MarkdownAction.Italic -> wrap(value, "*")
    MarkdownAction.H1 -> toggleLinePrefix(value, "# ")
    MarkdownAction.H2 -> toggleLinePrefix(value, "## ")
    MarkdownAction.Quote -> toggleLinePrefix(value, "> ")
    MarkdownAction.Bullet -> toggleLinePrefix(value, "- ")
    MarkdownAction.Task -> toggleLinePrefix(value, "- [ ] ")
}

private fun wrap(value: TextFieldValue, marker: String): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val selected = text.substring(start, end)
    val updated = text.substring(0, start) + marker + selected + marker + text.substring(end)
    val caret = if (selected.isEmpty()) start + marker.length else end + marker.length * 2
    return TextFieldValue(text = updated, selection = TextRange(caret))
}

/** İmlecin bulunduğu satırın başlangıç indeksi. */
private fun lineStartOf(text: String, caret: Int): Int =
    text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

private fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val lineStart = lineStartOf(text, value.selection.min)
    val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)

    // Aynı işaret zaten varsa kaldır; farklı bir başlık işareti varsa onun yerine geç.
    val existing = LINE_MARKERS.firstOrNull { line.startsWith(it) }
    val stripped = if (existing != null) line.removePrefix(existing) else line
    val newLine = if (existing == prefix) stripped else prefix + stripped

    val updated = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    val delta = newLine.length - line.length
    val caret = (value.selection.min + delta).coerceIn(lineStart, lineStart + newLine.length)
    return TextFieldValue(text = updated, selection = TextRange(caret))
}

/** Sıra önemli: "- [ ] " önce denenmeli, yoksa "- " ona takılır. */
private val LINE_MARKERS = listOf("- [ ] ", "- [x] ", "### ", "## ", "# ", "> ", "- ")
