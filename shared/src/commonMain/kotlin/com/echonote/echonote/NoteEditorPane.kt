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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echonote.echonote.model.relativeTime
import com.echonote.echonote.model.textStats

/**
 * Not düzenleyici. Ölçüler tasarımın HTML'inden: başlık çubuğu 64dp, ikon düğmeleri
 * 44dp, üstveri şeridi 10sp etiketler, gövde 620dp ölçüsünde ortalanmış, biçimlendirme
 * çubuğu altta yüzen 560dp'lik hap.
 *
 * Başlık çubuğunda notun adı değil "Not Düzenleyici" yazıyor — tasarım notun adını
 * gövdedeki `# Başlık` satırı olarak ele alıyor. Adı elle değiştirmek "…" menüsüne
 * taşındı: görünüm tasarımla birebirken yeniden adlandırma yeteneği kaybolmasın.
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
    onOpenAccount: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    if (!state.hasSelection) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bir not seç", style = MaterialTheme.typography.headlineSmall, color = EchoColors.textPrimary)
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
    val readMode = state.readMode
    var renaming by remember(state.selectedNoteId) { mutableStateOf(false) }

    // Biçimlendirme çubuğu imleç konumunu bilmek zorunda; metin burada TextFieldValue
    // olarak tutuluyor. ViewModel düz String tutmaya devam ediyor — seçim kalıcı bir
    // durum değil, ekranın işi.
    var field by remember(state.selectedNoteId) {
        mutableStateOf(TextFieldValue(state.editorContent, TextRange.Zero))
    }
    LaunchedEffect(state.editorContent) {
        if (state.editorContent != field.text) {
            field = TextFieldValue(state.editorContent, TextRange.Zero)
        }
    }

    Column(modifier.fillMaxSize()) {
        EditorTopBar(
            onBack = onBack,
            readMode = readMode,
            onToggleReadMode = onToggleReadMode,
            onExport = onExport,
            onExpand = onExpand,
            onCondense = onCondense,
            onUndo = onUndo,
            onStop = onStop,
            onDelete = onDelete,
            onRename = { renaming = true },
            onOpenAccount = onOpenAccount,
            canUndo = state.canUndo,
            isStreamingHere = isStreamingHere,
            isStreamingAnywhere = state.streamingNoteId != null,
        )

        if (readMode && !isStreamingHere) {
            ReadModeBar(onToggleReadMode = onToggleReadMode, content = state.editorContent)
            MarkdownView(
                content = state.editorContent,
                title = state.editorTitle,
                tags = state.selectedNote?.tags.orEmpty(),
                updatedAt = state.selectedNote?.updatedAt.orEmpty(),
                onToggleTask = onToggleTask,
                modifier = Modifier.weight(1f),
            )
            if (renaming) {
                RenameDialog(state.editorTitle, { onTitleChange(it); renaming = false }, { renaming = false })
            }
            return@Column
        }

        EditorMetaRow(state)
        TagRow(
            tags = state.selectedNote?.tags.orEmpty(),
            onAdd = onAddTag,
            onRemove = onRemoveTag,
            modifier = Modifier.padding(horizontal = ScreenMargin, vertical = 8.dp),
        )

        AnimatedVisibility(
            visible = isStreamingHere,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            StreamingBanner(Modifier.padding(horizontal = ScreenMargin))
        }

        // Genişlik açıkça hesaplanıyor: `fillMaxSize().widthIn(...)` zinciri Compose'da
        // min/max kısıtlarını birbirine karıştırıp ölçüyü uygulamıyordu.
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(start = ScreenMargin, end = ScreenMargin, top = 16.dp),
        ) {
            val proseWidth = if (maxWidth < ProseMeasure) maxWidth else ProseMeasure
            MarkdownEditor(
                value = field,
                readOnly = isStreamingHere,
                onValueChange = { updated ->
                    field = updated
                    if (updated.text != state.editorContent) onContentChange(updated.text)
                },
                modifier = Modifier.align(Alignment.TopCenter).width(proseWidth).fillMaxHeight(),
            )
        }
        Box(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            MarkdownToolbar(
                enabled = !isStreamingHere,
                onAction = { action ->
                    val updated = applyMarkdown(field, action)
                    field = updated
                    onContentChange(updated.text)
                },
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
    }

    if (renaming && !readMode) {
        RenameDialog(state.editorTitle, { onTitleChange(it); renaming = false }, { renaming = false })
    }
}

/** Geri · "Not Düzenleyici / kaydedildi" · paylaş · oku · "…" · hesap */
@Composable
private fun EditorTopBar(
    onBack: (() -> Unit)?,
    readMode: Boolean,
    onToggleReadMode: () -> Unit,
    onExport: () -> Unit,
    onExpand: () -> Unit,
    onCondense: () -> Unit,
    onUndo: () -> Unit,
    onStop: () -> Unit,
    onDelete: (() -> Unit)?,
    onRename: () -> Unit,
    onOpenAccount: (() -> Unit)?,
    canUndo: Boolean,
    isStreamingHere: Boolean,
    isStreamingAnywhere: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
    ) {
        if (onBack != null) {
            BarIcon(Icons.AutoMirrored.Filled.ArrowBack, "Listeye dön", onBack)
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = if (readMode) "Okuma Modu" else "Not Düzenleyici",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = EchoColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(EchoColors.secondaryBright, EchoShapes.pill))
                Spacer(Modifier.width(5.dp))
                Text("kaydedildi", style = MaterialTheme.typography.labelSmall, color = EchoColors.secondary)
            }
        }
        BarIcon(Icons.Default.Share, "Dışa aktar", onExport)
        BarIcon(
            icon = Icons.Default.AutoStories,
            description = if (readMode) "Düzenleyiciye dön" else "Okuma modu",
            onClick = onToggleReadMode,
            tint = if (readMode) EchoColors.primaryBright else EchoColors.textSecondary,
        )
        EditorOverflowMenu(
            onExpand = onExpand,
            onCondense = onCondense,
            onUndo = onUndo,
            onStop = onStop,
            onDelete = onDelete,
            onRename = onRename,
            canUndo = canUndo,
            isStreamingHere = isStreamingHere,
            isStreamingAnywhere = isStreamingAnywhere,
        )
        if (onOpenAccount != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .background(EchoColors.primary, EchoShapes.pill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenAccount,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Hesap ve ayarlar",
                    tint = EchoColors.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Başlık çubuğundaki 44dp yuvarlak ikon düğmesi. */
@Composable
private fun BarIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = EchoColors.textSecondary,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EditorOverflowMenu(
    onExpand: () -> Unit,
    onCondense: () -> Unit,
    onUndo: () -> Unit,
    onStop: () -> Unit,
    onDelete: (() -> Unit)?,
    onRename: () -> Unit,
    canUndo: Boolean,
    isStreamingHere: Boolean,
    isStreamingAnywhere: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarIcon(Icons.Default.MoreHoriz, "Daha fazla", onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = EchoShapes.card,
            containerColor = EchoColors.surfaceHigh,
            border = BorderStroke(1.dp, EchoColors.outline),
        ) {
            MenuRow("Adını değiştir", Icons.Default.TextFields, EchoColors.textPrimary) {
                open = false; onRename()
            }
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
                MenuRow("Geri al", Icons.Default.Undo, EchoColors.textPrimary, canUndo) {
                    open = false; onUndo()
                }
            }
            if (onDelete != null) {
                MenuRow("Çöp kutusuna taşı", Icons.Default.Delete, EchoColors.danger) {
                    open = false; onDelete()
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (enabled) tint else EchoColors.textMuted.copy(alpha = 0.5f)
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        },
        enabled = enabled,
        onClick = onClick,
    )
}

/** "• otomatik kaydedildi • az önce" ve sağda "N kelime • M karakter". */
@Composable
private fun EditorMetaRow(state: NotesUiState) {
    val stats = textStats(state.editorContent)
    val age = relativeTime(state.selectedNote?.updatedAt.orEmpty())
    val pulse = rememberInfiniteTransition(label = "savedDot")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "savedDotAlpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(EchoColors.surfaceLowest)
            .padding(start = ScreenMargin, end = ScreenMargin, top = 4.dp, bottom = 8.dp),
    ) {
        Box(Modifier.size(8.dp).alpha(dotAlpha).background(EchoColors.secondaryBright, EchoShapes.pill))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (age.isEmpty()) "otomatik kaydedildi" else "otomatik kaydedildi • $age",
            style = MaterialTheme.typography.labelSmall,
            color = EchoColors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.QueryStats,
            contentDescription = null,
            tint = EchoColors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${stats.words} kelime • ${stats.characters} karakter",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = EchoColors.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * Okuma modu araç çubuğu: düzenleyiciye dönüş, okuma süresi, yazı boyutu, "Düzenle".
 *
 * Tasarımda bir de sesli dinleme düğmesi var; metin okuma diye bir özellik yok, o yüzden
 * burada yok — dokunulduğunda hiçbir şey olmayan düğme koymuyorum.
 */
@Composable
private fun ReadModeBar(onToggleReadMode: () -> Unit, content: String) {
    val scale by AppServices.settings.fontScale.collectAsStateWithLifecycle()
    val stats = textStats(content)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      // Tasarımın kendi kuralı: okuma süresi rozeti dar ekranda gizleniyor
      // (`hidden sm:inline-flex`). Olmadığında üç düğme rahat sığıyor.
      val roomForReadingTime = maxWidth >= 420.dp
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenMargin, vertical = 8.dp),
      ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(EchoShapes.pill)
                .background(EchoColors.surface)
                .clickable(onClick = onToggleReadMode)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = EchoColors.secondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Düzenleyici", style = MaterialTheme.typography.labelMedium, color = EchoColors.secondary)
        }
        if (roomForReadingTime) {
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(EchoShapes.pill)
                    .background(EchoColors.surfaceLow)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = EchoColors.accent,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${readingMinutes(stats.words)} dk • ${stats.words} kelime",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.textSecondary,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(EchoShapes.pill).background(EchoColors.surface),
        ) {
            FontStepButton("A−", scale > MIN_FONT_SCALE) {
                AppServices.settings.setFontScale(scale - FONT_STEP)
            }
            Text(
                text = "%${(scale * 100).toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.textSecondary,
            )
            FontStepButton("A+", scale < MAX_FONT_SCALE) {
                AppServices.settings.setFontScale(scale + FONT_STEP)
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(EchoShapes.pill)
                .background(EchoColors.primary)
                .clickable(onClick = onToggleReadMode)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = EchoColors.onPrimary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Düzenle",
                style = MaterialTheme.typography.labelMedium,
                color = EchoColors.onPrimary,
                maxLines = 1,
            )
        }
      }
    }
}

@Composable
private fun FontStepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) EchoColors.textPrimary else EchoColors.textMuted.copy(alpha = 0.4f),
        )
    }
}

/** Ortalama okuma hızı 200 kelime/dk; en az 1 dk. */
private fun readingMinutes(words: Int): Int = ((words + 199) / 200).coerceAtLeast(1)

private const val FONT_STEP = 0.05f

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoColors.surfaceHigh,
        shape = EchoShapes.sheet,
        title = { Text("Notun adı", color = EchoColors.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(EchoShapes.field)
                    .background(EchoColors.surfaceLow)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(draft) }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = EchoColors.textPrimary),
                    cursorBrush = SolidColor(EchoColors.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { PrimaryButton(text = "Kaydet", onClick = { onConfirm(draft) }) },
        dismissButton = { GhostButton(text = "Vazgeç", onClick = onDismiss) },
    )
}

/**
 * Editördeki etiket satırı. Çipler tasarımdaki gibi: yarı dolgu periwinkle zemin,
 * `secondary-fixed` metin, soluk bir "#" ve 16dp'lik kaldırma düğmesi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        tags.forEach { tag ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(EchoShapes.pill)
                    .background(EchoColors.primary.copy(alpha = 0.22f))
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = "#",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.primaryBright.copy(alpha = 0.7f),
                )
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.secondaryBright,
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { onRemove(tag) }, modifier = Modifier.size(16.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Etiketi kaldır",
                        tint = EchoColors.secondary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        if (adding) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(min = 120.dp)
                    .clip(EchoShapes.pill)
                    .background(EchoColors.surfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onAdd(draft)
                            draft = ""
                            adding = false
                        }
                    ),
                    textStyle = MaterialTheme.typography.labelSmall.copy(color = EchoColors.textPrimary),
                    cursorBrush = SolidColor(EchoColors.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(EchoShapes.pill)
                    .background(EchoColors.surfaceHigh)
                    .clickable { adding = true }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = EchoColors.primaryBright,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Etiket Ekle",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.primaryBright,
                )
            }
        }
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
        // verildiğinde kendi içinde kaydırır ve imleci takip eder.
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
