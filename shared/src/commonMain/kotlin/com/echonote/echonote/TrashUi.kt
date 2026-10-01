package com.echonote.echonote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echonote.echonote.model.Note

/**
 * Silme onayı. Silme artık geri alınabilir olduğu için ağır bir uyarı değil, kazayla
 * dokunmaya karşı hafif bir durak: metin nereye gittiğini de söylüyor.
 */
@Composable
fun DeleteConfirmDialog(
    note: Note,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoColors.SpaceBlack.copy(alpha = 0.96f),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Çöp kutusuna taşınsın mı?",
                color = EchoColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "\"${note.title.ifBlank { "Adsız not" }}\" çöp kutusuna gidecek. " +
                    "Oradan geri alabilirsin; 30 gün sonra kalıcı olarak silinir.",
                color = EchoColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            GlassButton(text = "Çöpe taşı", onClick = onConfirm, accent = EchoColors.NeonRose)
        },
        dismissButton = {
            GlassButton(text = "Vazgeç", onClick = onDismiss, accent = EchoColors.TextSecondary)
        },
    )
}

/** Çöp kutusu: geri yükleme ve kalıcı silme. */
@Composable
fun TrashSheet(
    trashed: List<Note>,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmForever by remember { mutableStateOf<Note?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            // Cam katman %5 opak: tek basina altindaki liste icinden gecer ve panel
            // okunmaz olur. Once koyu bir zemin, sonra cam.
            .background(EchoColors.SpaceBlack.copy(alpha = 0.94f), RoundedCornerShape(24.dp))
            .glass(RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Çöp kutusu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EchoColors.TextPrimary,
                )
                Text(
                    text = if (trashed.isEmpty()) "Boş" else "${trashed.size} not · 30 gün sonra kalıcı silinir",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.TextSecondary,
                )
            }
            GlassButton(text = "Kapat", onClick = onClose, accent = EchoColors.TextSecondary)
        }

        HorizontalDivider(color = EchoColors.GlassBorder)

        if (trashed.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Sildiğin notlar burada 30 gün bekler",
                    style = MaterialTheme.typography.bodySmall,
                    color = EchoColors.TextSecondary,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                items(trashed, key = { it.id }) { note ->
                    TrashRow(
                        note = note,
                        onRestore = { onRestore(note.id) },
                        onDeleteForever = { confirmForever = note },
                    )
                }
            }
        }
    }

    confirmForever?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmForever = null },
            containerColor = EchoColors.SpaceBlack.copy(alpha = 0.96f),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Kalıcı olarak silinsin mi?", color = EchoColors.NeonRose, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "\"${note.title.ifBlank { "Adsız not" }}\" tüm cihazlardan kalıcı olarak " +
                        "silinecek. Bu işlemin geri dönüşü yok.",
                    color = EchoColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                GlassButton(
                    text = "Kalıcı sil",
                    onClick = {
                        onDeleteForever(note.id)
                        confirmForever = null
                    },
                    accent = EchoColors.NeonRose,
                )
            },
            dismissButton = {
                GlassButton(text = "Vazgeç", onClick = { confirmForever = null }, accent = EchoColors.TextSecondary)
            },
        )
    }
}

@Composable
private fun TrashRow(note: Note, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp), fill = EchoColors.GlassFill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = note.title.ifBlank { "Adsız not" },
            style = MaterialTheme.typography.titleSmall,
            color = EchoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            GlassButton(text = "Geri yükle", onClick = onRestore, accent = EchoColors.NeonMint)
            GlassButton(text = "Kalıcı sil", onClick = onDeleteForever, accent = EchoColors.NeonRose)
        }
    }
}

/** Ayarlar sheet'inden çöp kutusunu açan satır. */
@Composable
fun TrashEntryRow(count: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Çöp kutusu",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.TextPrimary,
            )
            Text(
                text = if (count == 0) "Boş" else "$count not",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.TextSecondary,
            )
        }
        GlassButton(text = "Aç", onClick = onOpen, accent = EchoColors.NeonLavender)
    }
}
