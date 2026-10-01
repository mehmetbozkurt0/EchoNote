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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echonote.echonote.model.Note

@Composable
fun NoteListPane(
    state: NotesUiState,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
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
                    color = EchoColors.NeonCyan,
                )
                SyncStatusChip(state.sync, modifier = Modifier.padding(top = 2.dp))
            }
            GlassButton(text = "+ Yeni", onClick = onCreate, accent = EchoColors.NeonMint)
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(start = 4.dp).size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ayarlar",
                    tint = EchoColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.notes, key = { it.id }) { note ->
                NoteListItem(
                    note = note,
                    selected = note.id == state.selectedNoteId,
                    streaming = note.id == state.streamingNoteId,
                    onClick = { onSelect(note.id) },
                    onDelete = { onDelete(note.id) },
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
) {
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) EchoColors.GlassFillStrong else EchoColors.GlassFill,
        animationSpec = tween(durationMillis = 250),
        label = "noteItemFill",
    )
    val accentStripe by animateColorAsState(
        targetValue = if (selected) EchoColors.NeonCyan.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "noteItemStripe",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .pressScale(interaction)
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
                    color = if (selected) EchoColors.TextPrimary else EchoColors.TextPrimary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (streaming) PulsingSparkle()
                NoteItemMenu(onDelete = onDelete)
            }
            Text(
                text = note.content.toPlainSnippet(),
                style = MaterialTheme.typography.bodySmall,
                color = EchoColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Kart köşesindeki 3 nokta menüsü. IconButton dokunuşu kendisi tükettiği için
 * arkadaki kartın onClick'i (editörü açma) tetiklenmez.
 */
@Composable
private fun NoteItemMenu(onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Not menüsü",
                tint = EchoColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = EchoColors.SpaceBlack.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, EchoColors.GlassBorder),
        ) {
            DropdownMenuItem(
                text = { Text("Sil", color = EchoColors.NeonRose) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = EchoColors.NeonRose,
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
