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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echonote.echonote.model.relativeTime
import com.echonote.echonote.model.textStats

/**
 * Not düzenleyici.
 *
 * Tasarımın "Sanctuary" ilkesi: yazarken ekranda yalnızca gerekenler kalsın. Bu yüzden
 * AI, geri al ve sil eylemleri üst bardaki "…" menüsüne çekildi — eskiden altı düğme
 * metnin üstünde iki satır yer kaplıyordu.
 */
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
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onExport: () -> Unit,
    onToggleReadMode: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    if (!state.hasSelection) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Bir not seç",
                    style = MaterialTheme.typography.headlineSmall,
                    color = EchoColors.textPrimary,
                )
                Text(
                    text = "Soldaki listeden bir not aç ya da yenisini oluştur.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoColors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        return
    }

    val isStreamingHere = state.isStreamingSelected
    val isStreamingAnywhere = state.streamingNoteId != null
    val readMode = state.readMode

    // Biçimlendirme çubuğu imleç konumunu bilmek zorunda; bu yüzden metin burada
    // TextFieldValue olarak tutuluyor. ViewModel düz String tutmaya devam ediyor —
    // seçim kalıcı bir durum değil, ekranın işi.
    var field by remember(state.selectedNoteId) {
        // İmleç başta: not açılınca kullanıcı yazdığının başını görmeli, editör
        // metnin sonuna kaydırılmış halde açılmamalı.
        mutableStateOf(TextFieldValue(state.editorContent, TextRange.Zero))
    }
    // İçerik dışarıdan değişti (AI akışı, geri al): alanı tazele. Kullanıcının kendi
    // yazması zaten eşit olduğu için buraya düşmez, imleç zıplamaz.
    LaunchedEffect(state.editorContent) {
        if (state.editorContent != field.text) {
            field = TextFieldValue(state.editorContent, TextRange(state.editorContent.length))
        }
    }

    Column(modifier.fillMaxSize()) {
        EditorTopBar(
            title = state.editorTitle,
            onTitleChange = onTitleChange,
            onBack = onBack,
            readMode = readMode,
            onToggleReadMode = onToggleReadMode,
            onExport = onExport,
            onExpand = onExpand,
            onCondense = onCondense,
            onUndo = onUndo,
            onStop = onStop,
            onDelete = onDelete,
            canUndo = state.canUndo,
            isStreamingHere = isStreamingHere,
            isStreamingAnywhere = isStreamingAnywhere,
        )

        EditorMetaRow(state)

        TagRow(
            tags = state.selectedNote?.tags.orEmpty(),
            onAdd = onAddTag,
            onRemove = onRemoveTag,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        AnimatedVisibility(
            visible = isStreamingHere,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            StreamingBanner(Modifier.padding(horizontal = 20.dp))
        }

        if (readMode && !isStreamingHere) {
            ReadModeBar(onToggleReadMode = onToggleReadMode)
            MarkdownView(
                content = state.editorContent,
                onToggleTask = onToggleTask,
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            )
        } else {
            // Yazma yüzeyi de okuma ölçüsüne uyar: geniş panelde satır ekran boyunca
            // uzarsa göz satır başını kaybediyor. Dar ekranda bu sınır hiç devreye
            // girmez, orada genişliği zaten ekran belirliyor.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                MarkdownEditor(
                    value = field,
                    readOnly = isStreamingHere,
                    onValueChange = { updated ->
                        field = updated
                        if (updated.text != state.editorContent) onContentChange(updated.text)
                    },
                    modifier = Modifier.fillMaxSize().widthIn(max = ProseMeasure),
                )
            }
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                MarkdownToolbar(
                    enabled = !isStreamingHere,
                    onAction = { action ->
                        val updated = applyMarkdown(field, action)
                        field = updated
                        onContentChange(updated.text)
                    },
                    modifier = Modifier.widthIn(max = ProseMeasure),
                )
            }
        }
    }
}

/** Geri · başlık + kaydedildi · paylaş · oku · "…" */
@Composable
private fun EditorTopBar(
    title: String,
    onTitleChange: (String) -> Unit,
    onBack: (() -> Unit)?,
    readMode: Boolean,
    onToggleReadMode: () -> Unit,
    onExport: () -> Unit,
    onExpand: () -> Unit,
    onCondense: () -> Unit,
    onUndo: () -> Unit,
    onStop: () -> Unit,
    onDelete: (() -> Unit)?,
    canUndo: Boolean,
    isStreamingHere: Boolean,
    isStreamingAnywhere: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Listeye dön",
                    tint = EchoColors.textPrimary,
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        BasicTextField(
            value = title,
            onValueChange = onTitleChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = EchoColors.textPrimary,
            ),
            cursorBrush = SolidColor(EchoColors.primary),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onExport, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Dışa aktar",
                tint = EchoColors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = onToggleReadMode, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = if (readMode) "Düzenleyiciye dön" else "Okuma modu",
                tint = if (readMode) EchoColors.primaryBright else EchoColors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        EditorOverflowMenu(
            onExpand = onExpand,
            onCondense = onCondense,
            onUndo = onUndo,
            onStop = onStop,
            onDelete = onDelete,
            canUndo = canUndo,
            isStreamingHere = isStreamingHere,
            isStreamingAnywhere = isStreamingAnywhere,
        )
    }
}

@Composable
private fun EditorOverflowMenu(
    onExpand: () -> Unit,
    onCondense: () -> Unit,
    onUndo: () -> Unit,
    onStop: () -> Unit,
    onDelete: (() -> Unit)?,
    canUndo: Boolean,
    isStreamingHere: Boolean,
    isStreamingAnywhere: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Daha fazla",
                tint = EchoColors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = EchoShapes.card,
            containerColor = EchoColors.surfaceHigh,
            border = BorderStroke(1.dp, EchoColors.outline),
        ) {
            if (isStreamingHere) {
                MenuRow("Durdur", Icons.Default.Delete, EchoColors.danger) { open = false; onStop() }
            } else {
                MenuRow(
                    text = "AI ile genişlet",
                    icon = Icons.Default.AutoAwesome,
                    tint = EchoColors.primaryBright,
                    enabled = !isStreamingAnywhere,
                ) { open = false; onExpand() }
                MenuRow(
                    text = "AI ile özetle",
                    icon = Icons.AutoMirrored.Filled.List,
                    tint = EchoColors.primary,
                    enabled = !isStreamingAnywhere,
                ) { open = false; onCondense() }
                MenuRow(
                    text = "Geri al",
                    icon = Icons.Default.Undo,
                    tint = EchoColors.textPrimary,
                    enabled = canUndo,
                ) { open = false; onUndo() }
            }
            if (onDelete != null) {
                MenuRow("Çöp kutusuna taşı", Icons.Default.Delete, EchoColors.danger) {
                    open = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (enabled) tint else EchoColors.textSecondary.copy(alpha = 0.4f)
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        },
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * Okuma modu çubuğu: düzenleyiciye dönüş ve yazı boyutu.
 *
 * Yazı boyutu Ayarlar'da da var ama okurken oraya gidip gelmek akışı bozuyor; tasarım
 * da bu yüzden kontrolü okuma başlığına koymuş. İkisi aynı ayarı yazıyor.
 */
@Composable
private fun ReadModeBar(onToggleReadMode: () -> Unit) {
    val scale by AppServices.settings.fontScale.collectAsStateWithLifecycle()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        GhostButton(
            text = "Düzenleyici",
            icon = Icons.Default.Edit,
            onClick = onToggleReadMode,
        )
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .echoSurface(EchoShapes.pill, fill = EchoColors.surfaceLow)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(
                onClick = { AppServices.settings.setFontScale(scale - FONT_STEP) },
                enabled = scale > MIN_FONT_SCALE,
                modifier = Modifier.size(40.dp),
            ) {
                Text(
                    text = "A−",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (scale > MIN_FONT_SCALE) EchoColors.textPrimary
                    else EchoColors.textSecondary.copy(alpha = 0.4f),
                )
            }
            Text(
                text = "%${(scale * 100).toInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = EchoColors.textSecondary,
            )
            IconButton(
                onClick = { AppServices.settings.setFontScale(scale + FONT_STEP) },
                enabled = scale < MAX_FONT_SCALE,
                modifier = Modifier.size(40.dp),
            ) {
                Text(
                    text = "A+",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (scale < MAX_FONT_SCALE) EchoColors.textPrimary
                    else EchoColors.textSecondary.copy(alpha = 0.4f),
                )
            }
        }
    }
}

private const val FONT_STEP = 0.05f

/** "kaydedildi · az önce" ve sağda kelime/karakter sayacı. */
@Composable
private fun EditorMetaRow(state: NotesUiState) {
    val stats = textStats(state.editorContent)
    val age = relativeTime(state.selectedNote?.updatedAt.orEmpty())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(6.dp).background(EchoColors.sync, EchoShapes.pill))
        Spacer(Modifier.width(8.dp))
        Text(
            // Tasarımda "otomatik kaydedildi" yazıyor; telefon genişliğinde sağdaki
            // sayaçla birlikte sığmayıp kırpılıyordu. Yeşil nokta zaten "kaydedildi"yi
            // anlatıyor, kelimeyi kısaltmak bilgiden bir şey eksiltmiyor.
            text = if (age.isEmpty()) "kaydedildi" else "kaydedildi · $age",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 14.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${stats.words} kelime · ${stats.characters} karakter",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.textSecondary.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

@Composable
private fun MarkdownEditor(
    value: TextFieldValue,
    readOnly: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = echoMarkdownColors()
    val markdownTransformation = remember(colors) { MarkdownTransformation(colors) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        visualTransformation = markdownTransformation,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = EchoColors.textPrimary),
        cursorBrush = SolidColor(EchoColors.primary),
        // Dıştan Modifier.verticalScroll SARILMAZ: BasicTextField sınırlı yükseklik
        // verildiğinde kendi içinde kaydırır ve imleci takip eder. Dış scroll bu
        // davranışı bastırıyordu — imleç satır sonuna inince ekrandan kayboluyordu.
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun StreamingBanner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "streaming")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "streamingAlpha",
    )
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = "AI yazıyor…",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.primaryBright,
            modifier = Modifier.alpha(alpha).padding(bottom = 6.dp),
        )
        LinearProgressIndicator(
            color = EchoColors.primary,
            trackColor = EchoColors.surfaceHigh,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
