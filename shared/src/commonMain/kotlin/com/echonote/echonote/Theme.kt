package com.echonote.echonote

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * **Fluid Humanist Sanctuary** paleti.
 *
 * Derinlik tonla kuruluyor, gölgeyle değil: zemin → düşük yüzey → kart → yüksek yüzey
 * basamakları ve saç teli inceliğinde bir kenarlık. Saf siyah ve sert ayraç yok; bu bir
 * gece günlüğü uygulaması, yorgun gözle okunuyor.
 *
 * Eski glassmorphism paletinin yerini aldı. O palet her yüzeye yarı saydam dolgu +
 * degrade çerçeve koyuyordu ve cihazda ölçüldüğünde kare bütçesinin kayda değer bir
 * kısmını yiyordu; bu palet düz yüzeylerle aynı derinlik hissini çok daha ucuza veriyor.
 */
data class EchoPalette(
    /** En arka zemin. */
    val canvas: Color,
    /** Gruplanmış listeler, gezinme çubuğu, giriş alanları. */
    val surfaceLow: Color,
    /** Not kartları, sayfalar. */
    val surface: Color,
    /** Kalkık yüzeyler: diyalog, açılır menü, seçili durum. */
    val surfaceHigh: Color,
    /** Birincil eylem rengi (periwinkle). */
    val primary: Color,
    /** Birincil rengin üzerindeki metin. */
    val onPrimary: Color,
    /** Birincil rengin parlak tonu: bağlantı, vurgulu metin. */
    val primaryBright: Color,
    /** Sıcak karşı ağırlık (terracotta): sabitlenen not, önemli işaret. */
    val accent: Color,
    /** Senkron nabzı (seafoam). */
    val sync: Color,
    /** Yıkıcı eylem. */
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Saç teli kenarlık; sert çizgi yerine hafif bir çevre parıltısı. */
    val outline: Color,
    /** Odaklı/sabitlenmiş öğelerin kenarlığı. */
    val outlineStrong: Color,
    val codeBackground: Color,
    val isLight: Boolean,
)

val DarkPalette = EchoPalette(
    canvas = Color(0xFF0D1323),
    surfaceLow = Color(0xFF151B2B),
    surface = Color(0xFF191F30),
    surfaceHigh = Color(0xFF242A3B),
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF0C1222),
    primaryBright = Color(0xFFBDC2FF),
    accent = Color(0xFFFFB783),
    sync = Color(0xFF2DD4BF),
    danger = Color(0xFFFFB4AB),
    textPrimary = Color(0xFFDDE2F9),
    textSecondary = Color(0xFF94A3B8),
    outline = Color.White.copy(alpha = 0.06f),
    outlineStrong = Color(0xFFA5B4FC).copy(alpha = 0.25f),
    codeBackground = Color.White.copy(alpha = 0.07f),
    isLight = false,
)

/**
 * Açık varyant. Tasarım sistemi yalnızca koyu temayı tanımlıyor; bu, aynı tonal mantığın
 * ters çevrilmişi: zemin en açık, yüzeyler basamak basamak koyulaşıyor. Periwinkle açık
 * zeminde okunmadığı için koyulaştırıldı, terracotta da öyle.
 */
val LightPalette = EchoPalette(
    canvas = Color(0xFFF7F8FC),
    surfaceLow = Color(0xFFEFF1F8),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE6E9F4),
    primary = Color(0xFF4953BC),
    onPrimary = Color(0xFFFFFFFF),
    primaryBright = Color(0xFF2F3AA3),
    accent = Color(0xFFB25A12),
    sync = Color(0xFF0F766E),
    danger = Color(0xFFB3261E),
    textPrimary = Color(0xFF151A28),
    textSecondary = Color(0xFF5A6478),
    outline = Color.Black.copy(alpha = 0.08f),
    outlineStrong = Color(0xFF4953BC).copy(alpha = 0.35f),
    codeBackground = Color.Black.copy(alpha = 0.06f),
    isLight = true,
)

val LocalEchoPalette = staticCompositionLocalOf { DarkPalette }

/** `EchoColors.primary` yazımını sürdüren composable erişimci. */
val EchoColors: EchoPalette
    @Composable @ReadOnlyComposable get() = LocalEchoPalette.current

/**
 * Okuma/yazma ölçüsü: yaklaşık 65 karakterlik satır.
 *
 * Tasarımın kuralı — geniş ekranda metin panel boyunca uzamaz, ortalanır. Uzun satırda
 * göz bir sonraki satırın başını bulmakta zorlanıyor.
 */
val ProseMeasure = 620.dp

/** Tasarım sisteminin köşe yarıçapları. */
object EchoShapes {
    /** Çipler, durum rozetleri, FAB. */
    val pill = RoundedCornerShape(percent = 50)
    /** Giriş alanları. */
    val field = RoundedCornerShape(12.dp)
    /** Not kartları, gruplanmış kaplar. */
    val card = RoundedCornerShape(16.dp)
    /** Sayfalar, diyaloglar, tam ekran yüzeyler. */
    val sheet = RoundedCornerShape(24.dp)
}

private fun colorSchemeFor(p: EchoPalette) = if (p.isLight) {
    lightColorScheme(
        primary = p.primary, onPrimary = p.onPrimary,
        secondary = p.primaryBright, tertiary = p.accent,
        background = p.canvas, onBackground = p.textPrimary,
        surface = p.surface, onSurface = p.textPrimary,
        surfaceVariant = p.surfaceLow, onSurfaceVariant = p.textSecondary,
        outline = p.outline, error = p.danger,
        errorContainer = p.danger.copy(alpha = 0.12f), onErrorContainer = p.danger,
    )
} else {
    darkColorScheme(
        primary = p.primary, onPrimary = p.onPrimary,
        secondary = p.primaryBright, onSecondary = p.canvas,
        tertiary = p.accent, onTertiary = Color(0xFF4F2500),
        background = p.canvas, onBackground = p.textPrimary,
        surface = p.surface, onSurface = p.textPrimary,
        surfaceVariant = p.surfaceLow, onSurfaceVariant = p.textSecondary,
        outline = p.outline, error = p.danger, onError = Color(0xFF690005),
        errorContainer = p.danger.copy(alpha = 0.12f), onErrorContainer = p.danger,
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
        MaterialTheme(colorScheme = colorSchemeFor(palette), typography = echoTypography()) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = palette.primary,
                    backgroundColor = palette.primary.copy(alpha = 0.25f),
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
        heading = p.primaryBright,
        marker = p.textSecondary.copy(alpha = 0.65f),
        code = p.accent,
        codeBackground = p.codeBackground,
        quote = p.primary,
    )
}

/**
 * Tasarımın temel yüzeyi: düz dolgu + saç teli kenarlık.
 *
 * Eski `Modifier.glass()`in yerini aldı. O, yarı saydam dolgu ve degrade çerçeve
 * çiziyordu; bu, her yüzeyde fazladan bir harman katmanı demekti. Derinlik artık
 * dolgunun **tonundan** geliyor, saydamlıktan değil.
 */
@Composable
fun Modifier.echoSurface(
    shape: Shape = EchoShapes.card,
    fill: Color = LocalEchoPalette.current.surface,
    border: Color = LocalEchoPalette.current.outline,
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(width = 1.dp, color = border, shape = shape)

/** Basılıyken yumuşakça küçülen dokunma geri bildirimi (ripple yerine). */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Uygulamanın zemini: düz, derin gece lacivertı.
 *
 * Eskiden burada süzülen üç katmanlı bir mesh gradient vardı. Galaxy S22'de
 * `dumpsys gfxinfo` ile ölçüldüğünde her yeniden çizimde tam ekran üç radial gradient
 * rasterize ediliyordu ("slow issue draw commands" 17/17, GPU 8-9 ms). Tasarım sistemi
 * zaten düz zemin istiyor — ikisi aynı yöne bakıyor.
 *
 * [active] parametresi çağrı yerleri için korundu; artık animasyon olmadığı için etkisiz.
 */
@Composable
fun EchoBackground(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") active: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(LocalEchoPalette.current.canvas), content = content)
}

/**
 * Birincil eylem: dolu periwinkle hap, zemin lacivertı metin.
 * Basınca hafifçe küçülür (`scale(0.97)`).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val p = LocalEchoPalette.current
    EchoButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        fill = if (enabled) p.primary else p.surfaceHigh,
        border = Color.Transparent,
        content = if (enabled) p.onPrimary else p.textSecondary.copy(alpha = 0.6f),
    )
}

/**
 * İkincil eylem: yüzey dolgulu hap, ince kenarlık, periwinkle metin.
 *
 * [accent] verilirse metin ve kenarlık o renge döner — yıkıcı eylemlerde `danger`.
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accent: Color? = null,
) {
    val p = LocalEchoPalette.current
    val tint = accent ?: p.primaryBright
    EchoButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        fill = if (accent != null) accent.copy(alpha = 0.10f) else p.surfaceLow,
        border = if (accent != null) accent.copy(alpha = 0.25f) else p.outline,
        content = if (enabled) tint else p.textSecondary.copy(alpha = 0.5f),
    )
}

@Composable
private fun EchoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    fill: Color,
    border: Color,
    content: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .echoSurface(shape = EchoShapes.pill, fill = fill, border = border)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    // Metin zaten eylemi söylüyor; ikon dekoratif, iki kez okunmasın.
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
        }
    }
}
