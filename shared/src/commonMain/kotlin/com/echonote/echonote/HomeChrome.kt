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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echonote.echonote.data.ConnectionState
import com.echonote.echonote.data.SyncState

/** Alt gezinmenin sekmeleri. Tasarımdaki "Koleksiyonlar" burada etiketlere karşılık geliyor. */
enum class HomeTab(val label: String, val icon: ImageVector) {
    Notes("Notlar", Icons.Default.Description),
    Tags("Etiketler", Icons.Outlined.Sell),
    Settings("Ayarlar", Icons.Default.Tune),
}

/**
 * Uygulama başlığı: marka + senkron durumu.
 *
 * Tasarımda sağda bir hesap avatarı var; hesap ekranı Ayarlar sekmesinin kendisi
 * olduğu için oraya ikinci bir giriş koymadım — aynı yere iki kapı kafa karıştırır.
 */
@Composable
fun HomeHeader(sync: SyncState, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Text(
            text = "echonote",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = EchoColors.textPrimary,
        )
        Spacer(Modifier.width(12.dp))
        SyncPill(sync)
    }
}

/**
 * Senkron rozeti. Çevrimdışı olmak bir hata değil — uygulama çevrimdışı-önce — bu yüzden
 * sakin bir nabızla anlatılıyor, kırmızı uyarıyla değil.
 */
@Composable
fun SyncPill(sync: SyncState, modifier: Modifier = Modifier) {
    val (label, color) = when {
        sync.pendingCount > 0L -> "${sync.pendingCount} bekliyor" to EchoColors.accent
        sync.connection == ConnectionState.Live -> "senkronize" to EchoColors.sync
        sync.connection == ConnectionState.OfflineMode -> "yerel" to EchoColors.textSecondary
        else -> "bağlanıyor" to EchoColors.textSecondary
    }
    val breathing = rememberInfiniteTransition(label = "syncPulse")
    val dotAlpha by breathing.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "syncDot",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .echoSurface(EchoShapes.pill, fill = EchoColors.surfaceLow)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Box(Modifier.size(7.dp).alpha(dotAlpha).background(color, EchoShapes.pill))
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.textSecondary,
        )
    }
}

/**
 * Alt gezinme. Tasarımda bulanık (blur) bir zemin var; burada opak yüzey kullanıldı —
 * cihazda ölçtüğümüzde yarı saydam katmanlar kare başına ölçülebilir maliyet
 * çıkarıyordu ve bu çubuk her karede ekranda.
 */
@Composable
fun HomeBottomBar(
    current: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxWidth()
            .background(EchoColors.surfaceLow)
            .padding(top = 8.dp, bottom = 10.dp),
    ) {
        HomeTab.entries.forEach { tab -> BottomBarItem(tab, tab == current) { onSelect(tab) } }
    }
}

@Composable
private fun BottomBarItem(tab: HomeTab, active: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (active) EchoColors.primaryBright else EchoColors.textSecondary,
        animationSpec = tween(180),
        label = "navTint",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .background(
                    color = if (active) EchoColors.primary.copy(alpha = 0.15f) else Color.Transparent,
                    shape = EchoShapes.pill,
                )
                .padding(horizontal = 18.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(text = tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Yeni not: tasarımdaki yüzen periwinkle düğme. */
@Composable
fun NewNoteFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .size(60.dp)
            .background(EchoColors.primary, EchoShapes.pill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Yeni not",
            tint = EchoColors.onPrimary,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Listenin altındaki saklama notu.
 *
 * Tasarımda burada "uçtan uca şifreli" yazıyordu; doğru değil — notlar sunucuya düz
 * metin gidiyor ve yerel SQLite de şifreli değil. Güvenlik konusunda yanlış söz veren
 * arayüz metni, hiç metin olmamasından kötüdür.
 */
@Composable
fun StorageFootnote(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Box(Modifier.size(5.dp).background(EchoColors.textSecondary.copy(alpha = 0.6f), EchoShapes.pill))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "cihazda saklanır · hesabınla eşitlenir",
            style = MaterialTheme.typography.labelSmall,
            color = EchoColors.textSecondary.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Etiketler sekmesi: tasarımdaki "Koleksiyonlar"ın bu uygulamadaki karşılığı. Klasör
 * kavramı yok; notlar etiketlerle ayrışıyor, o yüzden tarayıcı da etiket üzerinden.
 */
@Composable
fun TagsPane(
    tagCounts: List<Pair<String, Int>>,
    active: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    if (tagCounts.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.fillMaxSize().padding(32.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "Henüz etiket yok",
                style = MaterialTheme.typography.headlineSmall,
                color = EchoColors.textPrimary,
            )
            Text(
                text = "Bir notu açıp \"+ etiket\" ile başlayabilirsin.",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.weight(1.4f))
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(tagCounts, key = { it.first }) { (tag, count) ->
            val isActive = tag == active
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .echoSurface(
                        fill = if (isActive) EchoColors.surfaceHigh else EchoColors.surface,
                        border = if (isActive) EchoColors.outlineStrong else EchoColors.outline,
                    )
                    .clickable { onSelect(tag) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sell,
                    contentDescription = null,
                    tint = if (isActive) EchoColors.primaryBright else EchoColors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.titleSmall,
                    color = EchoColors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (count == 1) "1 not" else "$count not",
                    style = MaterialTheme.typography.labelMedium,
                    color = EchoColors.textSecondary,
                )
            }
        }
    }
}

/**
 * Masaüstü sol şeridi: marka, yeni not, sekmeler, altta senkron durumu.
 *
 * Alt gezinme çubuğunun geniş ekrandaki karşılığı. Fareyle çalışırken kenardaki
 * hedefler daha yakın ve geniş ekranda dikey alan yataydan değerli.
 */
@Composable
fun HomeSideRail(
    current: HomeTab,
    onSelect: (HomeTab) -> Unit,
    onNewNote: () -> Unit,
    sync: SyncState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(236.dp)
            .fillMaxHeight()
            .background(EchoColors.surfaceLow)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(
            text = "echonote",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = EchoColors.textPrimary,
            modifier = Modifier.padding(start = 6.dp, bottom = 18.dp),
        )
        PrimaryButton(
            text = "Yeni Not",
            icon = Icons.Default.Add,
            onClick = onNewNote,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(18.dp))
        HomeTab.entries.forEach { tab -> RailItem(tab, tab == current) { onSelect(tab) } }
        Spacer(Modifier.weight(1f))
        SyncPill(sync)
    }
}

@Composable
private fun RailItem(tab: HomeTab, active: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (active) EchoColors.primaryBright else EchoColors.textSecondary,
        animationSpec = tween(180),
        label = "railTint",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                color = if (active) EchoColors.primary.copy(alpha = 0.15f) else Color.Transparent,
                shape = EchoShapes.field,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.size(14.dp))
        Text(text = tab.label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
