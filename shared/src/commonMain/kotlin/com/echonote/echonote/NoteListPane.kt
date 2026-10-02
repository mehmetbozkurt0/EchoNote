package com.echonote.echonote

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.relativeTime
import com.echonote.echonote.model.textStats

/**
 * Not listesi. Tasarımdaki düzen: arama → etiket çipleri → "Sabitlenenler" bölümü →
 * "Tüm Notlar" bölümü (sıralama açılırıyla).
 *
 * Başlık ve alt gezinme burada değil [NotesHome]'da: liste masaüstünde orta panel
 * olarak da kullanılıyor ve orada kendi başlığı olmamalı.
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
    /** Listenin altına eklenen boşluk: FAB ve alt gezinmenin altında kart kalmasın. */
    bottomPadding: Dp = 0.dp,
) {
    Column(modifier.fillMaxSize()) {
        SearchField(
            query = state.searchQuery,
            onQueryChange = onSearchChange,
            focusRequester = searchFocus,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        if (state.allTags.isNotEmpty()) {
            TagFilterBar(
                tags = state.allTags,
                active = state.activeTag,
                onSelect = onTagFilter,
            )
        }

        when {
            // Henüz ilk yayın gelmedi: "boş" demek yanlış olur, iskelet göster.
            !state.loaded -> {
                ListSkeleton()
                return@Column
            }

            state.isSearching && state.visibleNotes.isEmpty() -> {
                EmptyState(
                    title = "Sonuç yok",
                    detail = "\"${state.searchQuery}\" ile eşleşen not bulunamadı.",
                )
                return@Column
            }

            state.activeTag != null && state.visibleNotes.isEmpty() -> {
                EmptyState(
                    title = "Bu etikette not yok",
                    detail = "#${state.activeTag} ile işaretlenmiş not bulunamadı.",
                )
                return@Column
            }

            state.visibleNotes.isEmpty() -> {
                EmptyState(
                    title = "Henüz not yok",
                    detail = "Sağ alttaki + ile ilk notunu oluştur.",
                )
                return@Column
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp + bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.pinnedNotes.isNotEmpty()) {
                item(key = "pinned-header") {
                    SectionHeader(
                        title = "Sabitlenenler",
                        icon = Icons.Default.PushPin,
                        trailing = { Text(
                            text = "${state.pinnedNotes.size} Not",
                            style = MaterialTheme.typography.labelMedium,
                            color = EchoColors.textSecondary,
                        ) },
                    )
                }
                noteItems(state, state.pinnedNotes, onSelect, onDelete, onTogglePin)
            }

            if (state.otherNotes.isNotEmpty()) {
                item(key = "all-header") {
                    SectionHeader(
                        title = if (state.pinnedNotes.isEmpty()) "Notlar" else "Tüm Notlar",
                        icon = null,
                        trailing = { SortMenu(state.sort, onSortChange) },
                    )
                }
                noteItems(state, state.otherNotes, onSelect, onDelete, onTogglePin)
            }

            item(key = "footnote") { StorageFootnote() }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.noteItems(
    state: NotesUiState,
    notes: List<Note>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    items(notes, key = { it.id }) { note ->
        SwipeToDelete(onDelete = { onDelete(note.id) }) {
            NoteListItem(
                note = note,
                selected = note.id == state.selectedNoteId,
                streaming = note.id == state.streamingNoteId,
                onClick = { onSelect(note.id) },
                onDelete = { onDelete(note.id) },
                onTogglePin = { onTogglePin(note.id) },
            )
        }
    }
}

/** Bölüm başlığı: "📌 Sabitlenenler … 2 Not" / "Tüm Notlar … Son Düzenlenen ⌄". */
@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EchoColors.accent,
                modifier = Modifier.size(16.dp),
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
                .echoSurface(EchoShapes.pill, fill = EchoColors.surfaceLow)
                .clickable { open = true }
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = current.label,
                style = MaterialTheme.typography.labelMedium,
                color = EchoColors.textSecondary,
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Sıralamayı değiştir",
                tint = EchoColors.textSecondary,
                modifier = Modifier.size(16.dp),
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
                            color = if (option == current) EchoColors.primaryBright else EchoColors.textPrimary,
                        )
                    },
                    onClick = {
                        open = false
                        onChange(option)
                    },
                )
            }
        }
    }
}

/**
 * Kaydırarak silme. Silme geri alınabilir olduğu (çöp kutusu) ve onay diyaloğu yine
 * çıktığı için kazara kaydırma tehlikeli değil; yine de eşik yarı genişlik, yanlışlıkla
 * tetiklenmesin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
            }
            // Asla "kapandı" sayma: silme onaya bağlı, kullanıcı vazgeçerse satır
            // yerinde kalmalı. Kart her durumda eski yerine döner.
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
                    .echoSurface(
                        shape = EchoShapes.card,
                        fill = EchoColors.danger.copy(alpha = 0.16f),
                        border = Color.Transparent,
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = EchoColors.danger,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        content = { content() },
    )
}

/**
 * Etiket filtre çubuğu. Başta "#tümü" çipi var: filtreyi kaldırmanın görünür bir yolu
 * olmalı, yoksa aktif çipe yeniden dokunmayı keşfetmek gerekiyor.
 */
@Composable
private fun TagFilterBar(tags: List<String>, active: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "__all__") { FilterChip(label = "#tümü", active = active == null) { onSelect(null) } }
        items(tags, key = { it }) { tag ->
            FilterChip(label = "#$tag", active = tag == active) {
                onSelect(if (tag == active) null else tag)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .echoSurface(
                shape = EchoShapes.pill,
                fill = if (active) EchoColors.primary.copy(alpha = 0.15f) else EchoColors.surfaceLow,
                border = if (active) EchoColors.primary.copy(alpha = 0.55f) else EchoColors.outline,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (active) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(EchoColors.primary, EchoShapes.pill),
            )
            Spacer(Modifier.width(7.dp))
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = EchoColors.textPrimary,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = EchoColors.textSecondary,
            textAlign = TextAlign.Center,
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        repeat(5) {
            Box(Modifier.fillMaxWidth().height(104.dp).alpha(alpha).echoSurface())
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .echoSurface(EchoShapes.pill, fill = EchoColors.surfaceLow)
            .padding(start = 16.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = EchoColors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Box(Modifier.weight(1f).padding(start = 12.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = "Düşüncelerde veya etiketlerde ara…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoColors.textSecondary.copy(alpha = 0.8f),
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
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Aramayı temizle",
                    tint = EchoColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Not kartı: başlık, iki satır alıntı, altta etiket çipleri ve kelime sayısı + zaman.
 * Sabitlenmiş notun sağ üstünde terracotta nokta, seçili notun solunda periwinkle şerit.
 */
@Composable
private fun NoteListItem(
    note: Note,
    selected: Boolean,
    streaming: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) EchoColors.surfaceHigh else EchoColors.surface,
        animationSpec = tween(durationMillis = 220),
        label = "noteItemFill",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected || note.pinned) EchoColors.outlineStrong else EchoColors.outline,
        animationSpec = tween(durationMillis = 220),
        label = "noteItemBorder",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .pressScale(interaction)
            // Kart opak zemin almalı: altındaki kaydırarak-silme zemini yarı saydam
            // dolgudan sızıp her kartı kızıla boyuyordu.
            .background(EchoColors.canvas, EchoShapes.card)
            .echoSurface(shape = EchoShapes.card, fill = fill, border = borderColor)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        if (selected) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(EchoColors.primary))
        }
        Box(Modifier.weight(1f)) {
          Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "Adsız not" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = EchoColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Sağ üstteki ⋮ bindirilmiş duruyor; başlık altına girmesin.
                    modifier = Modifier.weight(1f).padding(end = 36.dp),
                )
                if (note.pinned) {
                    Box(Modifier.size(7.dp).background(EchoColors.accent, EchoShapes.pill))
                }
                if (streaming) PulsingSparkle()
            }
            Text(
                text = note.content.toPlainSnippet(),
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) {
                note.tags.take(2).forEach { tag ->
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .echoSurface(EchoShapes.pill, fill = EchoColors.surfaceLow)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = EchoColors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = buildString {
                        append("${textStats(note.content).words} kelime")
                        val age = relativeTime(note.updatedAt)
                        if (age.isNotEmpty()) append("  ·  $age")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = EchoColors.textSecondary.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
          }
          // Dokunma hedefi 48dp kalsın ama satır yüksekliğini sürüklemesin: menü
          // akışın dışında, kartın sağ üstüne bindirildi.
          Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
              NoteItemMenu(pinned = note.pinned, onDelete = onDelete, onTogglePin = onTogglePin)
          }
        }
    }
}

/**
 * Kart köşesindeki 3 nokta menüsü. IconButton dokunuşu kendisi tükettiği için
 * arkadaki kartın onClick'i (editörü açma) tetiklenmez.
 */
@Composable
private fun NoteItemMenu(pinned: Boolean, onDelete: () -> Unit, onTogglePin: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Not menüsü",
                tint = EchoColors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = EchoShapes.card,
            containerColor = EchoColors.surfaceHigh,
            border = BorderStroke(1.dp, EchoColors.outline),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (pinned) "Sabitlemeyi kaldır" else "Sabitle",
                        color = EchoColors.accent,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = EchoColors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text("Sil", color = EchoColors.danger) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = EchoColors.danger,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/** AI yazarken liste öğesinde yanıp sönen nokta. */
@Composable
private fun PulsingSparkle() {
    val transition = rememberInfiniteTransition(label = "sparkle")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "sparkleAlpha",
    )
    Box(
        Modifier
            .size(7.dp)
            .alpha(alpha)
            .background(EchoColors.primary, EchoShapes.pill),
    )
}

private fun String.toPlainSnippet(): String =
    lineSequence()
        .map { it.trim().trimStart('#', '-', '+', '*', '>', ' ').replace("**", "").replace("`", "") }
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .take(120)
