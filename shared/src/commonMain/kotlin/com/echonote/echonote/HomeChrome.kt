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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
fun HomeHeader(sync: SyncState, onOpenAccount: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(64.dp).padding(horizontal = ScreenMargin),
    ) {
        Text(
            text = "echonote",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = EchoColors.textPrimary,
        )
        Spacer(Modifier.width(8.dp))
        SyncPill(sync)
        Spacer(Modifier.weight(1f))
        if (onOpenAccount != null) {
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

/**
 * Senkron rozeti. Çevrimdışı olmak bir hata değil — uygulama çevrimdışı-önce — bu yüzden
 * sakin bir nabızla anlatılıyor, kırmızı uyarıyla değil.
 */
@Composable
fun SyncPill(sync: SyncState, modifier: Modifier = Modifier) {
    val label = when {
        sync.pendingCount > 0L -> sync.pendingCount.toString() + " bekliyor"
        sync.connection == ConnectionState.Live -> "senkronize"
        sync.connection == ConnectionState.OfflineMode -> "yerel"
        else -> "bağlanıyor"
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
            .clip(EchoShapes.pill)
            .background(EchoColors.surfaceHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .alpha(dotAlpha)
                .background(
                    if (sync.pendingCount > 0L) EchoColors.accent else EchoColors.secondaryBright,
                    EchoShapes.pill,
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = EchoColors.secondary,
        )
    }
}

/**
 * Alt gezinme: 80dp yükseklik, 22dp ikonlar, etkin sekme hap zeminde.
 *
 * Tasarımda zemin yarı saydam + blur; burada opak. Cihazda ölçtüğümüzde yarı saydam
 * katmanlar kare başına ölçülebilir maliyet çıkarıyordu ve bu çubuk her karede ekranda.
 */
@Composable
fun HomeBottomBar(
    current: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(EchoColors.canvas)
            .padding(horizontal = ScreenMargin),
    ) {
        HomeTab.entries.forEach { tab -> BottomBarItem(tab, tab == current) { onSelect(tab) } }
    }
}

@Composable
private fun BottomBarItem(tab: HomeTab, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) EchoColors.primary else EchoColors.textSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(EchoShapes.pill)
            .background(if (active) EchoColors.surfaceHigh else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(text = tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Yeni not: 56dp periwinkle hap, sağ üstünde terracotta rozet. */
@Composable
fun NewNoteFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.size(62.dp), contentAlignment = Alignment.BottomStart) {
        Box(
            Modifier
                .pressScale(interaction)
                .size(56.dp)
                .background(EchoColors.primary, EchoShapes.pill)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Yeni not",
                tint = EchoColors.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(12.dp)
                .background(EchoColors.accent, EchoShapes.pill),
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
    val pulse = rememberInfiniteTransition(label = "footnote")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "footnoteDot",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
    ) {
        Box(Modifier.size(6.dp).alpha(dotAlpha).background(EchoColors.secondaryBright, EchoShapes.pill))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "cihazda saklanır • hesabınla eşitlenir",
            style = MaterialTheme.typography.labelSmall,
            color = EchoColors.textMuted,
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
 * Masaüstü sol şeridi. Tasarımdan: 256dp genişlik, `surface-container-low` zemin,
 * 24dp dikey / 20dp yatay dolgu, 12dp köşeli gezinme satırları.
 */
@Composable
fun HomeSideRail(
    current: HomeTab,
    onSelect: (HomeTab) -> Unit,
    onNewNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(256.dp)
            .fillMaxHeight()
            .background(EchoColors.surfaceLow)
            .padding(horizontal = ScreenMargin, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            Box(Modifier.size(10.dp).background(EchoColors.primary, EchoShapes.pill))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "echonote",
                style = MaterialTheme.typography.headlineSmall,
                color = EchoColors.textPrimary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoShapes.field)
                .background(EchoColors.primary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNewNote,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = null,
                tint = EchoColors.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Yeni Not",
                style = MaterialTheme.typography.labelLarge,
                color = EchoColors.onPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Ctrl+N",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.onPrimary.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(24.dp))
        HomeTab.entries.forEach { tab -> RailItem(tab, tab == current) { onSelect(tab) } }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoShapes.field)
                .background(EchoColors.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(Modifier.size(8.dp).background(EchoColors.secondaryBright, EchoShapes.pill))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "yerel kasa hazır",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                tint = EchoColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Masaüstü üst çubuğu: ortada arama, sağda senkron rozeti ve ayarlar.
 * Tasarımdan: 64dp yükseklik, 32dp yatay dolgu, arama en fazla 576dp.
 */
@Composable
fun DesktopTopBar(
    sync: SyncState,
    query: String,
    onQueryChange: (String) -> Unit,
    searchFocus: FocusRequester,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(64.dp).padding(horizontal = 32.dp),
    ) {
        Box(Modifier.weight(1f).widthIn(max = 576.dp)) {
            SearchField(query = query, onQueryChange = onQueryChange, focusRequester = searchFocus)
        }
        Spacer(Modifier.width(16.dp))
        SyncPill(sync)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(EchoShapes.field)
                .background(EchoColors.surfaceLow)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSettings,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Ayarlar",
                tint = EchoColors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
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

/**
 * Masaüstünün en alt durum çubuğu.
 *
 * Tasarımda burada "uçtan uca şifreli" de yazıyordu; doğru olmadığı için yok. Kalanlar
 * gerçek: depo biçimi, metin kodlaması ve not biçimi.
 */
@Composable
fun DesktopStatusBar(noteCount: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(EchoColors.surfaceLowest)
            .padding(horizontal = 32.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(6.dp).background(EchoColors.secondaryBright, EchoShapes.pill))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "cihazda saklanır",
            style = MaterialTheme.typography.labelSmall,
            color = EchoColors.textMuted,
        )
        Spacer(Modifier.weight(1f))
        listOf("SQLite yerel kasa · $noteCount not", "UTF-8", "Markdown").forEachIndexed { i, label ->
            if (i > 0) {
                Text(
                    text = "  ·  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.textMuted.copy(alpha = 0.4f),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.textMuted,
            )
        }
    }
}
