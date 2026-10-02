package com.echonote.echonote

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.relativeTime

@Composable
fun NoteListPane(
    state: NotesUiState,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSearchChange: (String) -> Unit,
    onTagFilter: (String?) -> Unit,
    onTogglePin: (String) -> Unit,
    searchFocus: FocusRequester,
    modifier: Modifier = Modifier,
    /** false: mobil tam ekran sayfa — dış cam çerçeve yok, cam efekti kartlarda kalır. */
    framed: Boolean = true,
) {
    val container = if (framed) Modifier.glass(RoundedCornerShape(24.dp)) else Modifier
    Column(modifier.fillMaxSize().then(container)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "EchoNote",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = EchoColors.neonCyan,
                )
                SyncStatusChip(state.sync, modifier = Modifier.padding(top = 2.dp))
            }
            GlassButton(text = "+ Yeni", onClick = onCreate, accent = EchoColors.neonMint)
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(start = 4.dp).size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ayarlar",
                    tint = EchoColors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        SearchField(
            query = state.searchQuery,
            onQueryChange = onSearchChange,
            focusRequester = searchFocus,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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

            state.visibleNotes.isEmpty() -> {
                EmptyState(
                    title = "Henüz not yok",
                    detail = "Yukarıdaki + Yeni ile ilk notunu oluştur.",
                )
                return@Column
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.visibleNotes, key = { it.id }) { note ->
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
                    .glass(RoundedCornerShape(18.dp), fill = EchoColors.neonRose.copy(alpha = 0.16f))
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
                    tint = EchoColors.neonRose,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        content = { content() },
    )
}

/** Etiket filtre çubuğu; etiket yoksa hiç çizilmez. */
@Composable
private fun TagFilterBar(tags: List<String>, active: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(tags, key = { it }) { tag ->
            val isActive = tag == active
            Box(
                Modifier
                    .glass(
                        shape = RoundedCornerShape(50),
                        fill = if (isActive) EchoColors.neonCyan.copy(alpha = 0.18f) else EchoColors.glassFill,
                    )
                    .clickable { onSelect(tag) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) EchoColors.neonCyan else EchoColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = EchoColors.textPrimary,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        repeat(5) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .alpha(alpha)
                    .glass(RoundedCornerShape(18.dp)),
            )
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
            .glass(RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = EchoColors.textSecondary,
            modifier = Modifier.size(18.dp).padding(end = 2.dp),
        )
        Box(Modifier.weight(1f).padding(start = 8.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = "Notlarda ara",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoColors.textSecondary.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = EchoColors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(EchoColors.neonCyan),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
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
        targetValue = if (selected) EchoColors.glassFillStrong else EchoColors.glassFill,
        animationSpec = tween(durationMillis = 250),
        label = "noteItemFill",
    )
    val accentStripe by animateColorAsState(
        targetValue = if (selected) EchoColors.neonCyan.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "noteItemStripe",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .pressScale(interaction)
            // Kart opak zemin almalı: altındaki kaydırarak-silme zemini cam dolgunun
            // %5 opaklığından sızıp her kartı pembeye boyuyordu.
            .background(EchoColors.spaceBlack, RoundedCornerShape(18.dp))
            .glass(shape = RoundedCornerShape(18.dp), fill = fill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Column(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentStripe),
        ) {}
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "Adsız not" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) EchoColors.textPrimary else EchoColors.textPrimary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (note.pinned) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Sabitlenmiş",
                        tint = EchoColors.neonMint,
                        modifier = Modifier.size(14.dp).padding(end = 2.dp),
                    )
                }
                if (streaming) PulsingSparkle()
                NoteItemMenu(pinned = note.pinned, onDelete = onDelete, onTogglePin = onTogglePin)
            }
            Text(
                text = note.content.toPlainSnippet(),
                style = MaterialTheme.typography.bodySmall,
                color = EchoColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val age = relativeTime(note.updatedAt)
            if (age.isNotEmpty()) {
                Text(
                    text = age,
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.textSecondary.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 4.dp),
                )
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
            shape = RoundedCornerShape(16.dp),
            containerColor = EchoColors.spaceBlack.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, EchoColors.glassBorder),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (pinned) "Sabitlemeyi kaldır" else "Sabitle",
                        color = EchoColors.neonMint,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = EchoColors.neonMint,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text("Sil", color = EchoColors.neonRose) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = EchoColors.neonRose,
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

/** AI yazarken liste öğesinde yanıp sönen kıvılcım. */
@Composable
private fun PulsingSparkle() {
    val transition = rememberInfiniteTransition(label = "sparkle")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "sparkleAlpha",
    )
    Text(text = "✨", modifier = Modifier.alpha(alpha))
}

private fun String.toPlainSnippet(): String =
    lineSequence()
        .map { it.trim().trimStart('#', '-', '+', '*', '>', ' ').replace("**", "").replace("`", "") }
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .take(80)
