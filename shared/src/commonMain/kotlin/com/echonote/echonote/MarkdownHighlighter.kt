package com.echonote.echonote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class MarkdownColors(
    val heading: Color,
    val marker: Color,
    val code: Color,
    val codeBackground: Color,
    val quote: Color,
)

/**
 * Yazarken Markdown'ı anlık vurgular. Metni değiştirmez, yalnızca stil ekler;
 * bu sayede imleç eşlemesi için [OffsetMapping.Identity] yeterlidir.
 */
class MarkdownTransformation(private val colors: MarkdownColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightMarkdown(text.text, colors), OffsetMapping.Identity)
}

fun highlightMarkdown(raw: String, colors: MarkdownColors): AnnotatedString = buildAnnotatedString {
    append(raw)

    HEADING.findAll(raw).forEach { match ->
        val level = match.groupValues[1].length
        addStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold,
                fontSize = headingSize(level),
                color = colors.heading,
            ),
            match.range.first,
            match.range.last + 1,
        )
    }

    BOLD.findAll(raw).forEach { match ->
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
        dimMarkers(match, markerLength = 2, colors.marker)
    }

    ITALIC.findAll(raw).forEach { match ->
        addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
        dimMarkers(match, markerLength = 1, colors.marker)
    }

    INLINE_CODE.findAll(raw).forEach { match ->
        addStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                color = colors.code,
                background = colors.codeBackground,
            ),
            match.range.first,
            match.range.last + 1,
        )
    }

    LIST_MARKER.findAll(raw).forEach { match ->
        val marker = match.groups[1] ?: return@forEach
        addStyle(
            SpanStyle(color = colors.marker, fontWeight = FontWeight.Bold),
            marker.range.first,
            marker.range.last + 1,
        )
    }

    BLOCKQUOTE.findAll(raw).forEach { match ->
        addStyle(
            SpanStyle(color = colors.quote, fontStyle = FontStyle.Italic),
            match.range.first,
            match.range.last + 1,
        )
    }
}

private fun AnnotatedString.Builder.dimMarkers(match: MatchResult, markerLength: Int, color: Color) {
    val start = match.range.first
    val endExclusive = match.range.last + 1
    addStyle(SpanStyle(color = color), start, start + markerLength)
    addStyle(SpanStyle(color = color), endExclusive - markerLength, endExclusive)
}

private fun headingSize(level: Int): TextUnit = when (level) {
    1 -> 26.sp
    2 -> 22.sp
    3 -> 19.sp
    else -> 17.sp
}

private val HEADING = Regex("""^(#{1,6})\s.*$""", RegexOption.MULTILINE)
private val BOLD = Regex("""\*\*([^*\n]+)\*\*""")
private val ITALIC = Regex("""(?<![*\w])\*([^*\n]+)\*(?![*\w])""")
private val INLINE_CODE = Regex("""`([^`\n]+)`""")
private val LIST_MARKER = Regex("""^(\s*[-+*])\s""", RegexOption.MULTILINE)
private val BLOCKQUOTE = Regex("""^>\s.*$""", RegexOption.MULTILINE)
