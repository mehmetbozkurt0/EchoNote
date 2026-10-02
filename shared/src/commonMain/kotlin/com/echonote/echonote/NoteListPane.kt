package com.echonote.echonote

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.relativeTime
import com.echonote.echonote.model.textStats

/** Tasarımın yatay kenar boşluğu (`margin` = 1.25rem). */
val ScreenMargin = 20.dp

/**
 * Not listesi. Ölçüler tasarımın HTML'inden birebir alındı: kart dolgusu 16dp, kartlar
 * arası 8dp, bölümler arası 24dp, çip yüksekliği 32dp, arama alanı 48dp.
 */
@Composable
fun NoteListPane(
    state: NotesUiState,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onTagFilter: (String?) -> Unit,
    onTogglePin: (String) -> Unit,
    onSortChange: (NoteSort) -> Unit,
    searchFocus: FocusRequester,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    /** Masaüstünde arama üstteki genel çubukta; liste kendi alanını çizmez. */
    showSearch: Boolean = true,
) {
    Column(modifier.fillMaxSize()) {
        if (showSearch) {
            SearchField(
                query = state.searchQuery,
                onQueryChange = onSearchChange,
                focusRequester = searchFocus,
            )
        }

        if (state.allTags.isNotEmpty()) {
            TagFilterBar(state.allTags, state.activeTag, onTagFilter)
        }

        when {
            !state.loaded -> { ListSkeleton(); return@Column }

            state.isSearching && state.visibleNotes.isEmpty() -> {
                EmptyState("Sonuç yok", "\"${state.searchQuery}\" ile eşleşen not bulunamadı.")
                return@Column
            }

            state.activeTag != null && state.visibleNotes.isEmpty() -> {
                EmptyState("Bu etikette not yok", "#${state.activeTag} ile işaretlenmiş not bulunamadı.")
                return@Column
            }

            state.visibleNotes.isEmpty() -> {
                EmptyState("Henüz not yok", "Sağ alttaki + ile ilk notunu oluştur.")
                return@Column
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.pinnedNotes.isNotEmpty()) {
                item(key = "pinned-header") {
                    SectionHeader(title = "Sabitlenenler", pinned = true) {
                        Text(
                            text = "${state.pinnedNotes.size} Not",
                            style = MaterialTheme.typography.labelSmall,
                            color = EchoColors.textMuted,
                        )
                    }
                }
                noteItems(state, state.pinnedNotes, onSelect, onDelete, onTogglePin)
            }

            if (state.otherNotes.isNotEmpty()) {
                item(key = "all-header") {
                    SectionHeader(
                        title = if (state.pinnedNotes.isEmpty()) "Notlar" else "Tüm Notlar",
                        pinned = false,
                    ) { SortMenu(state.sort, onSortChange) }
                }
                noteItems(state, state.otherNotes, onSelect, onDelete, onTogglePin)
            }

            item(key = "footnote") { StorageFootnote() }
        }
    }
}

private fun LazyListScope.noteItems(
    state: NotesUiState,
    notes: List<Note>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    items(notes, key = { it.id }) { note ->
        Box(Modifier.padding(horizontal = ScreenMargin)) {
            SwipeToDelete(onDelete = { onDelete(note.id) }) {
                NoteCard(
                    note = note,
                    selected = note.id == state.selectedNoteId,
                    streaming = note.id == state.streamingNoteId,
                    onClick = { onSelect(note.id) },
                    onTogglePin = { onTogglePin(note.id) },
                )
            }
        }
    }
}

/** "📌 Sabitlenenler … 2 Not" / "Tüm Notlar … Son Düzenlenen ⌄" */
@Composable
private fun SectionHeader(title: String, pinned: Boolean, trailing: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenMargin, end = ScreenMargin, top = 24.dp, bottom = 8.dp),
    ) {
        if (pinned) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                tint = EchoColors.accent,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = EchoColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun SortMenu(current: NoteSort, onChange: (NoteSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(EchoShapes.pill)
                .background(EchoColors.surfaceLow)
                .clickable { open = true }
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = current.label,
                style = MaterialTheme.typography.labelMedium,
                color = EchoColors.textSecondary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Sıralamayı değiştir",
                tint = EchoColors.textSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = EchoShapes.card,
            containerColor = EchoColors.surfaceHigh,
            border = BorderStroke(1.dp, EchoColors.outline),
        ) {
            NoteSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = if (option == current) EchoColors.primaryBright else EchoColors.textSecondary,
                        )
                    },
                    onClick = { open = false; onChange(option) },
                )
            }
        }
    }
}

/**
 * Kaydırarak silme. Tasarımda kartın altından kırmızı bir "Sil" alanı çıkıyor; burada
 * da öyle. Silme geri alınabilir (çöp kutusu) ve onay diyaloğu yine çıkıyor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onDelete()
            // Asla "kapandı" sayma: silme onaya bağlı, kullanıcı vazgeçerse satır
            // yerinde kalmalı.
            false
        },
        positionalThreshold = { total -> total * 0.5f },
    )

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(EchoShapes.card)
                    .background(EchoColors.danger.copy(alpha = 0.18f))
                    .padding(horizontal = 22.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = EchoColors.danger,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Sil",
                        style = MaterialTheme.typography.labelSmall,
                        color = EchoColors.danger,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
        content = { content() },
    )
}

/** Etiket filtre çubuğu; başta filtreyi kaldıran "#tümü" çipi. */
@Composable
private fun TagFilterBar(tags: List<String>, active: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = ScreenMargin, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "__all__") { FilterChip("#tümü", active == null) { onSelect(null) } }
        items(tags, key = { it }) { tag ->
            FilterChip("#$tag", tag == active) { onSelect(if (tag == active) null else tag) }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "chipPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "chipDot",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            .clip(EchoShapes.pill)
            .background(
                if (active) EchoColors.primary.copy(alpha = 0.20f) else EchoColors.surfaceLow,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (active) 16.dp else 14.dp),
    ) {
        if (active) {
            Box(Modifier.size(6.dp).alpha(dotAlpha).background(EchoColors.primary, EchoShapes.pill))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) EchoColors.primaryBright else EchoColors.textSecondary,
        )
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = EchoColors.textPrimary)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = EchoColors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.weight(1.4f))
    }
}

/** İlk yükleme: "boş liste" ile "henüz yüklenmedi" ayırt edilebilsin diye. */
@Composable
private fun ListSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenMargin, vertical = 24.dp),
    ) {
        repeat(5) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .alpha(alpha)
                    .clip(EchoShapes.card)
                    .background(EchoColors.surface),
            )
        }
    }
}

/** Arama alanı; masaüstünde üst çubukta da kullanılıyor. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = 4.dp)
            .height(48.dp)
            .clip(EchoShapes.pill)
            .background(EchoColors.surfaceLow)
            .padding(start = 16.dp, end = 14.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = EchoColors.textMuted,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.weight(1f).padding(start = 12.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = "Düşüncelerde veya etiketlerde ara…",
                    style = MaterialTheme.typography.bodySmall,
                    color = EchoColors.textMuted.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = EchoColors.textPrimary),
                cursorBrush = SolidColor(EchoColors.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (query.isEmpty()) {
            // Tasarımda burada bir mikrofon var. Sesli not diye bir özellik yok, bu yüzden
            // düğme değil ikon: dokunulduğunda hiçbir şey olmayan bir düğme koymuyorum.
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = EchoColors.textMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        } else {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Aramayı temizle",
                    tint = EchoColors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Not kartı.
 *
 * Sabitlenmiş kart tasarımda yukarıdan aşağı degrade ve köşesinde sıcak bir ışık lekesi
 * taşıyor; normal kart düz. Sağ üstteki yer imi ikonu sabitlemeyi açıp kapatıyor —
 * tasarımda ayrı bir "⋮" menüsü yok, yer imi hem görünümü hem işlevi karşılıyor.
 */
@Composable
private fun NoteCard(
    note: Note,
    selected: Boolean,
    streaming: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val background = if (note.pinned) {
        Brush.verticalGradient(listOf(EchoColors.surfaceHigh, EchoColors.surface))
    } else {
        SolidColor(if (selected) EchoColors.surfaceHigh else EchoColors.surface)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(EchoShapes.card)
            // Kart opak zemin almalı: altındaki kaydırarak-silme zemini sızmasın.
            .background(EchoColors.canvas)
            .background(background)
            .then(
                if (selected) Modifier.border(1.dp, EchoColors.outlineStrong, EchoShapes.card)
                else Modifier,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = note.title.ifBlank { "Adsız not" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = EchoColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (streaming) StreamingDot()
                if (note.pinned) {
                    Box(
                        Modifier
                            .padding(top = 4.dp, end = 6.dp)
                            .size(8.dp)
                            .background(EchoColors.accent, EchoShapes.pill),
                    )
                }
                PinToggle(pinned = note.pinned, onToggle = onTogglePin)
            }
            Text(
                text = note.content.toPlainSnippet(),
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                note.tags.take(2).forEach { tag ->
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(EchoShapes.pill)
                            .background(
                                if (note.pinned) EchoColors.surfaceHighest else EchoColors.surfaceLow,
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = EchoColors.secondary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${textStats(note.content).words} kelime",
                    style = MaterialTheme.typography.labelMedium,
                    color = EchoColors.textMuted,
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = EchoColors.textMuted.copy(alpha = 0.4f),
                )
                Text(
                    text = relativeTime(note.updatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (note.pinned) EchoColors.primaryBright else EchoColors.textMuted,
                )
            }
        }
    }
}

/** Sağ üstteki yer imi: sabitlemeyi açıp kapatır. Dokunma hedefi 44dp. */
@Composable
private fun PinToggle(pinned: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = if (pinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = if (pinned) "Sabitlemeyi kaldır" else "Sabitle",
            tint = if (pinned) EchoColors.accent else EchoColors.textMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(17.dp),
        )
    }
}

/** AI yazarken kartta yanıp sönen nokta. */
@Composable
private fun StreamingDot() {
    val transition = rememberInfiniteTransition(label = "sparkle")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "sparkleAlpha",
    )
    Box(
        Modifier
            .padding(top = 4.dp, end = 6.dp)
            .size(8.dp)
            .alpha(alpha)
            .background(EchoColors.primary, EchoShapes.pill),
    )
}

private fun String.toPlainSnippet(): String =
    lineSequence()
        .map { it.trim().trimStart('#', '-', '+', '*', '>', ' ').replace("**", "").replace("`", "") }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .take(160)
