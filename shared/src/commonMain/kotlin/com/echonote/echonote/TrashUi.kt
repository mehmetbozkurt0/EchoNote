package com.echonote.echonote

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.echonote.echonote.model.Note

/**
 * Silme onayları.
 *
 * Çöp kutusunun kendi listesi artık burada değil: Ayarlar sayfasındaki "Çöp Kutusu"
 * kartına taşındı ([SettingsPane]). Ayrı bir pencere olarak açılması, tasarımın "her
 * şey tek bir ayarlar sayfasında" düzenine aykırıydı.
 */

/**
 * Çöpe taşıma onayı. Silme geri alınabilir olduğu için ağır bir uyarı değil, kazayla
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
        containerColor = EchoColors.surfaceHigh,
        shape = EchoShapes.sheet,
        title = {
            Text(
                text = "Çöp kutusuna taşınsın mı?",
                color = EchoColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = "\"${note.title.ifBlank { "Adsız not" }}\" çöp kutusuna gidecek. " +
                    "Oradan geri alabilirsin; 30 gün sonra kalıcı olarak silinir.",
                color = EchoColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            GhostButton(text = "Çöpe taşı", onClick = onConfirm, accent = EchoColors.danger)
        },
        dismissButton = {
            GhostButton(text = "Vazgeç", onClick = onDismiss)
        },
    )
}

/** Kalıcı silme onayı: geri dönüşü olmayan tek işlem, bu yüzden ikinci kez soruluyor. */
@Composable
fun DeleteForeverDialog(
    note: Note,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoColors.surfaceHigh,
        shape = EchoShapes.sheet,
        title = {
            Text(
                text = "Kalıcı olarak silinsin mi?",
                color = EchoColors.danger,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = "\"${note.title.ifBlank { "Adsız not" }}\" tüm cihazlardan kalıcı olarak " +
                    "silinecek. Bu işlemin geri dönüşü yok.",
                color = EchoColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            GhostButton(text = "Kalıcı sil", onClick = onConfirm, accent = EchoColors.danger)
        },
        dismissButton = {
            GhostButton(text = "Vazgeç", onClick = onDismiss)
        },
    )
}

/** Çöp kutusunu toptan boşaltma onayı. */
@Composable
fun EmptyTrashDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoColors.surfaceHigh,
        shape = EchoShapes.sheet,
        title = {
            Text(
                text = "Çöp kutusu boşaltılsın mı?",
                color = EchoColors.danger,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = "Çöp kutusundaki $count not tüm cihazlardan kalıcı olarak silinecek. " +
                    "Bu işlemin geri dönüşü yok.",
                color = EchoColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            GhostButton(text = "Hepsini sil", onClick = onConfirm, accent = EchoColors.danger)
        },
        dismissButton = { GhostButton(text = "Vazgeç", onClick = onDismiss) },
    )
}
