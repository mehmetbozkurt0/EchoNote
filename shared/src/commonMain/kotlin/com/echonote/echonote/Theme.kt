package com.echonote.echonote

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism paleti; koyu ve açık varyantı var.
 *
 * Çağrı yerleri değişmesin diye [EchoColors] adı korundu, ama artık sabit bir `object`
 * değil CompositionLocal'den okunan bir palet: `EchoColors.neonCyan` temaya göre farklı
 * değer döndürüyor. Büyük harfli alanlar eski yazımı sürdürmek için.
 */
data class EchoPalette(
    val spaceBlack: Color,
    val nightBlue: Color,
    val deepPurple: Color,
    val abyssTeal: Color,
    val neonCyan: Color,
    val neonMint: Color,
    val neonLavender: Color,
    val neonRose: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorder: Color,
    /** Cam çerçevenin degrade uçları: koyu temada beyaz, açıkta siyah. */
    val glassEdgeStrong: Color,
    val glassEdgeSoft: Color,
    val codeBackground: Color,
    val isLight: Boolean,
)

/** Özgün görünüm: uzay siyahı zemin üzerine pastel neon vurgular. */
val DarkPalette = EchoPalette(
    spaceBlack = Color(0xFF05060E),
    nightBlue = Color(0xFF14306B),
    deepPurple = Color(0xFF311B55),
    abyssTeal = Color(0xFF0B3B44),
    neonCyan = Color(0xFF7DE2FF),
    neonMint = Color(0xFF9FFFCB),
    neonLavender = Color(0xFFC5B3FF),
    neonRose = Color(0xFFFF9EBB),
    textPrimary = Color(0xFFEAF0FF),
    textSecondary = Color(0xFF9AA6C3),
    glassFill = Color.White.copy(alpha = 0.05f),
    glassFillStrong = Color.White.copy(alpha = 0.10f),
    glassBorder = Color.White.copy(alpha = 0.10f),
    glassEdgeStrong = Color.White.copy(alpha = 0.14f),
    glassEdgeSoft = Color.White.copy(alpha = 0.04f),
    codeBackground = Color.White.copy(alpha = 0.07f),
    isLight = false,
)

/**
 * Açık varyant. Renkleri çevirmek yetmiyor: cam dolgusu açık zeminde beyaz kalırsa
 * görünmez olur, bu yüzden dolgu ve çerçeve siyaha döndürülüp opaklıkları ayrı seçildi.
 * Pastel neonlar da açık zeminde okunmadığı için koyulaştırıldı.
 */
val LightPalette = EchoPalette(
    spaceBlack = Color(0xFFF2F5FB),
    nightBlue = Color(0xFF9FBDF5),
    deepPurple = Color(0xFFC9B8F0),
    abyssTeal = Color(0xFFA9DCE3),
    neonCyan = Color(0xFF0E6B88),
    neonMint = Color(0xFF0F7350),
    neonLavender = Color(0xFF5742AE),
    neonRose = Color(0xFFAB2D57),
    textPrimary = Color(0xFF13171E),
    textSecondary = Color(0xFF596375),
    glassFill = Color.Black.copy(alpha = 0.04f),
    glassFillStrong = Color.Black.copy(alpha = 0.09f),
    glassBorder = Color.Black.copy(alpha = 0.12f),
    glassEdgeStrong = Color.Black.copy(alpha = 0.13f),
    glassEdgeSoft = Color.Black.copy(alpha = 0.04f),
    codeBackground = Color.Black.copy(alpha = 0.06f),
    isLight = true,
)

val LocalEchoPalette = staticCompositionLocalOf { DarkPalette }

/** Eski `EchoColors.X` yazımını koruyan composable erişimci. */
val EchoColors: EchoPalette
    @Composable @ReadOnlyComposable get() = LocalEchoPalette.current

private fun colorSchemeFor(p: EchoPalette) = if (p.isLight) {
    lightColorScheme(
        primary = p.neonCyan, secondary = p.neonLavender, tertiary = p.neonMint,
        background = p.spaceBlack, onBackground = p.textPrimary,
        surface = p.spaceBlack, onSurface = p.textPrimary,
        onSurfaceVariant = p.textSecondary, outline = p.glassBorder,
        error = p.neonRose, errorContainer = p.neonRose.copy(alpha = 0.12f),
        onErrorContainer = p.neonRose,
    )
} else {
    darkColorScheme(
        primary = p.neonCyan, onPrimary = Color(0xFF04222E),
        secondary = p.neonLavender, onSecondary = Color(0xFF1E1438),
        tertiary = p.neonMint, onTertiary = Color(0xFF073021),
        background = p.spaceBlack, onBackground = p.textPrimary,
        surface = p.spaceBlack, onSurface = p.textPrimary,
        surfaceVariant = Color(0xFF11141F), onSurfaceVariant = p.textSecondary,
        outline = p.glassBorder, error = p.neonRose, onError = Color(0xFF3B0A1A),
        errorContainer = p.neonRose.copy(alpha = 0.12f), onErrorContainer = p.neonRose,
    )
}

/**
 * [fontScale] tüm `sp` değerlerini tek yerden ölçekler: her metin stilini tek tek
 * çarpmak yerine yoğunluğun fontScale'ini değiştiriyoruz, böylece sonradan eklenen
 * metinler de kendiliğinden uyar.
 */
@Composable
fun EchoTheme(
    palette: EchoPalette = DarkPalette,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalEchoPalette provides palette,
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
    ) {
        MaterialTheme(colorScheme = colorSchemeFor(palette)) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = palette.neonCyan,
                    backgroundColor = palette.neonCyan.copy(alpha = 0.25f),
                ),
                content = content,
            )
        }
    }
}

/** Temaya uyumlu, yüksek kontrastlı Markdown renkleri. */
@Composable
@ReadOnlyComposable
fun echoMarkdownColors(): MarkdownColors {
    val p = LocalEchoPalette.current
    return MarkdownColors(
        heading = p.neonCyan,
        marker = p.textSecondary.copy(alpha = 0.75f),
        code = p.neonMint,
        codeBackground = p.codeBackground,
        quote = p.neonLavender,
    )
}

/** Buzlu cam yüzey: yuvarlatılmış köşe + yarı saydam dolgu + incecik degrade çerçeve. */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = LocalEchoPalette.current.glassFill,
): Modifier {
    val p = LocalEchoPalette.current
    return this
        .clip(shape)
        .background(fill)
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(p.glassEdgeStrong, p.glassEdgeSoft)),
            shape = shape,
        )
}

/** Basılıyken yumuşakça küçülen dokunma geri bildirimi (ripple yerine). */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Uygulamanın en arka planı: uzay siyahı zemine yavaşça süzülen gece mavisi,
 * koyu mor ve derin teal ışık lekelerinden oluşan mesh gradient.
 *
 * **Burada `blur` YOK, bilerek.** Eskiden tam ekran `blur(48.dp)` vardı; Galaxy S22'de
 * `dumpsys gfxinfo` ile ölçüldüğünde ortanca kare süresini **17 ms → 11 ms** düşürdü
 * (yani her karede ~6 ms, uygulama açık olduğu sürece). Ekran görüntüsü karşılaştırması
 * görünür bir fark göstermedi: radial gradient'ler zaten yumuşak geçişli.
 *
 * [active] false iken süzülme animasyonu durur ve kare üretimi kesilir — kullanıcı
 * sadece okurken pil yakmamak için. Değer dondurulduğu yerde kalır, geri dönünce
 * oradan devam eder; zıplama olmaz.
 */
@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val drift = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        // Donduğu yerden ters yöne devam et, sonra sonsuz salınıma gir.
        drift.animateTo(
            targetValue = if (drift.value < 0.5f) 1f else 0f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = MESH_DRIFT_MS, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
        )
    }

    // drawBehind composable olmayan bir lambda: paleti dışarıda okuyup kapatıyoruz.
    val palette = LocalEchoPalette.current
    val spotAlpha = if (palette.isLight) 0.75f else 1f

    Box(modifier.fillMaxSize().background(palette.spaceBlack)) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val drift = drift.value
                    if (size.minDimension <= 0f) return@drawBehind
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(palette.nightBlue.copy(alpha = 0.55f * spotAlpha), Color.Transparent),
                            center = Offset(size.width * (0.12f + 0.18f * drift), size.height * 0.18f),
                            radius = size.width * 0.75f,
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(palette.deepPurple.copy(alpha = 0.45f * spotAlpha), Color.Transparent),
                            center = Offset(size.width * (0.92f - 0.22f * drift), size.height * (0.82f - 0.12f * drift)),
                            radius = size.width * 0.70f,
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(palette.abyssTeal.copy(alpha = 0.35f * spotAlpha), Color.Transparent),
                            center = Offset(size.width * 0.45f, size.height * (0.55f + 0.18f * drift)),
                            radius = size.width * 0.55f,
                        )
                    )
                }
        )
        content()
    }
}

/** Süzülmenin bir uçtan diğerine geçiş süresi. */
private const val MESH_DRIFT_MS = 26_000

/**
 * Cam görünümlü, basınca yumuşakça küçülen buton.
 *
 * [icon] verildiğinde metnin soluna vektör ikon koyar. Eskiden burada emoji vardı
 * (✨ ← ↩); emoji platformdan platforma farklı çiziliyor ve ekran okuyucu onu
 * "sola ok" gibi okuyordu. Net karşılığı olmayan yerlerde ikon yerine düz metin.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = EchoColors.neonCyan,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .glass(
                shape = RoundedCornerShape(24.dp),
                fill = accent.copy(alpha = if (enabled) 0.10f else 0.04f),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        val contentColor = if (enabled) accent else EchoColors.textSecondary.copy(alpha = 0.5f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    // Metin zaten eylemi söylüyor; ikon dekoratif, iki kez okunmasın.
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp).padding(end = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}
