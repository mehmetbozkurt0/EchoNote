package com.echonote.echonote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorPane(
    state: NotesUiState,
    onContentChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onExpand: () -> Unit,
    onCondense: () -> Unit,
    onUndo: () -> Unit,
    onStop: () -> Unit,
    onToggleTask: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** false: mobil tam ekran sayfa — dış cam çerçeve yok, cam efekti butonlarda kalır. */
    framed: Boolean = true,
) {
    val container = if (framed) Modifier.glass(RoundedCornerShape(24.dp)) else Modifier

    // Seçim üzerinden karar verilir, listede görünmesi beklenmez: yeni oluşturulan not
    // DB akışıyla birkaç ms sonra listeye düşer, editör o anda açık olmalı.
    if (!state.hasSelection) {
        Box(modifier.fillMaxSize().then(container), contentAlignment = Alignment.Center) {
            Text("Bir not seç", color = EchoColors.TextSecondary)
        }
        return
    }

    val isStreamingHere = state.isStreamingSelected
    val isStreamingAnywhere = state.streamingNoteId != null
    // Mod korunur: okuma modundayken ekran döndürmek seni editöre atmasın.
    var readMode by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().then(container).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                GlassButton(
                    text = "←",
                    onClick = onBack,
                    accent = EchoColors.TextPrimary,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
            BasicTextField(
                value = state.editorTitle,
                onValueChange = onTitleChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = EchoColors.TextPrimary,
                ),
                cursorBrush = SolidColor(EchoColors.NeonCyan),
                modifier = Modifier.weight(1f),
            )
        }

        // FlowRow: butonlar yataya sığmazsa ezilmek yerine alt satıra akar (mobil).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            GlassButton(
                text = "✨ AI Genişlet",
                onClick = onExpand,
                accent = EchoColors.NeonCyan,
                enabled = !isStreamingAnywhere,
            )
            GlassButton(
                text = "AI Özetle",
                onClick = onCondense,
                accent = EchoColors.NeonLavender,
                enabled = !isStreamingAnywhere,
            )
            GlassButton(
                text = if (readMode) "✎ Düzenle" else "👁 Oku",
                onClick = { readMode = !readMode },
                accent = EchoColors.TextPrimary,
            )
            if (isStreamingHere) {
                GlassButton(text = "Durdur", onClick = onStop, accent = EchoColors.NeonRose)
            } else {
                GlassButton(
                    text = "↩ Geri Al",
                    onClick = onUndo,
                    accent = EchoColors.NeonMint,
                    enabled = state.canUndo,
                )
            }
        }

        AnimatedVisibility(
            visible = isStreamingHere,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            StreamingBanner()
        }

        HorizontalDivider(
            color = EchoColors.GlassBorder,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (readMode && !isStreamingHere) {
            MarkdownView(
                content = state.editorContent,
                onToggleTask = onToggleTask,
                modifier = Modifier.weight(1f),
            )
        } else {
            MarkdownEditor(
                content = state.editorContent,
                readOnly = isStreamingHere,
                onContentChange = onContentChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MarkdownEditor(
    content: String,
    readOnly: Boolean,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val markdownTransformation = remember { MarkdownTransformation(echoMarkdownColors()) }

    BasicTextField(
        value = content,
        onValueChange = onContentChange,
        readOnly = readOnly,
        visualTransformation = markdownTransformation,
        textStyle = TextStyle(
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = EchoColors.TextPrimary,
        ),
        cursorBrush = SolidColor(EchoColors.NeonCyan),
        // Dıştan Modifier.verticalScroll SARILMAZ: BasicTextField sınırlı yükseklik
        // verildiğinde kendi içinde kaydırır ve imleci takip eder. Dış scroll bu
        // davranışı bastırıyordu — imleç satır sonuna inince ekrandan kayboluyordu.
        // (String aşırı yüklemesi scrollState parametresi almıyor; iç kaydırma tek yol.)
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun StreamingBanner() {
    val transition = rememberInfiniteTransition(label = "streaming")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "streamingAlpha",
    )
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = "▍AI yazıyor…",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.NeonCyan,
            modifier = Modifier.alpha(alpha).padding(bottom = 4.dp),
        )
        LinearProgressIndicator(
            color = EchoColors.NeonCyan,
            trackColor = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
