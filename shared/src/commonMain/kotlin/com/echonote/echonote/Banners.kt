package com.echonote.echonote

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Başlık altındaki tek satırlık senkron göstergesi. Yazdığın şeyin depoya ulaşıp
 * ulaşmadığını ve canlı bağlantının ayakta olup olmadığını görünür kılar.
 *
 * Öncelik sırası bilinçli: bağlantı sorunu, kaydetme durumundan önce gelir — çünkü
 * bağlantı yokken "Kaydediliyor…" yazmak yanıltıcı olur.
 */
@Composable
fun SyncStatusChip(state: NotesUiState, modifier: Modifier = Modifier) {
    val (label, color, pulsing) = when {
        state.connection == ConnectionState.OfflineMode ->
            Triple("Çevrimdışı mod", EchoColors.TextSecondary, false)

        state.connection == ConnectionState.Reconnecting ->
            Triple("Bağlantı yok — yeniden deneniyor", EchoColors.NeonRose, true)

        state.connection == ConnectionState.Connecting ->
            Triple("Bağlanıyor…", EchoColors.TextSecondary, true)

        state.hasUnsavedChanges ->
            Triple("Kaydediliyor…", EchoColors.NeonLavender, true)

        else -> Triple("Kaydedildi", EchoColors.NeonMint, false)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .alpha(if (pulsing) pulseAlpha() else 1f)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dikkat çekmesi gereken geçici durumlar için yumuşak nabız. */
@Composable
private fun pulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "syncPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "syncPulseAlpha",
    )
    return alpha
}

/**
 * Hata bannerı. Uygulama kökünde gösterilir: eskiden yalnızca editör panelindeydi ve
 * hiç not seçili olmadığında (örn. açılışta bağlantı hatası) hiç görünmüyordu.
 */
@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Kökte serbest duruyor: mesh zeminin üstünde okunabilir kalması için
            // önce koyu bir perde, sonra cam katman.
            .background(EchoColors.SpaceBlack.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .glass(shape = RoundedCornerShape(16.dp), fill = EchoColors.NeonRose.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = EchoColors.NeonRose,
            modifier = Modifier.weight(1f),
        )
        GlassButton(text = "Kapat", onClick = onDismiss, accent = EchoColors.NeonRose)
    }
}
