package com.echonote.echonote

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Dark Glassmorphism paleti: uzay siyahı zemin üzerine pastel neon vurgular. */
object EchoColors {
    val SpaceBlack = Color(0xFF05060E)
    val NightBlue = Color(0xFF14306B)
    val DeepPurple = Color(0xFF311B55)
    val AbyssTeal = Color(0xFF0B3B44)

    val NeonCyan = Color(0xFF7DE2FF)
    val NeonMint = Color(0xFF9FFFCB)
    val NeonLavender = Color(0xFFC5B3FF)
    val NeonRose = Color(0xFFFF9EBB)

    val TextPrimary = Color(0xFFEAF0FF)
    val TextSecondary = Color(0xFF9AA6C3)

    val GlassFill = Color.White.copy(alpha = 0.05f)
    val GlassFillStrong = Color.White.copy(alpha = 0.10f)
    val GlassBorder = Color.White.copy(alpha = 0.10f)
}

/** Sistem temasını dinlemeyen, kalıcı karanlık renk şeması. */
private val EchoDarkColorScheme = darkColorScheme(
    primary = EchoColors.NeonCyan,
    onPrimary = Color(0xFF04222E),
    secondary = EchoColors.NeonLavender,
    onSecondary = Color(0xFF1E1438),
    tertiary = EchoColors.NeonMint,
    onTertiary = Color(0xFF073021),
    background = EchoColors.SpaceBlack,
    onBackground = EchoColors.TextPrimary,
    surface = EchoColors.SpaceBlack,
    onSurface = EchoColors.TextPrimary,
    surfaceVariant = Color(0xFF11141F),
    onSurfaceVariant = EchoColors.TextSecondary,
    outline = EchoColors.GlassBorder,
    error = EchoColors.NeonRose,
    onError = Color(0xFF3B0A1A),
    errorContainer = EchoColors.NeonRose.copy(alpha = 0.12f),
    onErrorContainer = EchoColors.NeonRose,
)

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EchoDarkColorScheme) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = EchoColors.NeonCyan,
                backgroundColor = EchoColors.NeonCyan.copy(alpha = 0.25f),
            ),
            content = content,
        )
    }
}

/** Yeni temaya uyumlu, yüksek kontrastlı pastel neon Markdown renkleri. */
fun echoMarkdownColors(): MarkdownColors = MarkdownColors(
    heading = EchoColors.NeonCyan,
    marker = EchoColors.TextSecondary.copy(alpha = 0.75f),
    code = EchoColors.NeonMint,
    codeBackground = Color.White.copy(alpha = 0.07f),
    quote = EchoColors.NeonLavender,
)

/** Buzlu cam yüzey: yuvarlatılmış köşe + yarı saydam dolgu + incecik degrade çerçeve. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = EchoColors.GlassFill,
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)),
        ),
        shape = shape,
    )

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
 * [blur] Android 12 altında sessizce no-op'tur (fallback: keskin ama zaten
 * yumuşak geçişli degradeler), desteklenen platformlarda lekeleri iyice eritir.
 */
@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "mesh")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 26000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshDrift",
    )

    Box(modifier.fillMaxSize().background(EchoColors.SpaceBlack)) {
        Box(
            Modifier
                .fillMaxSize()
                .blur(48.dp)
                .drawBehind {
                    if (size.minDimension <= 0f) return@drawBehind
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(EchoColors.NightBlue.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width * (0.12f + 0.18f * drift), size.height * 0.18f),
                            radius = size.width * 0.75f,
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(EchoColors.DeepPurple.copy(alpha = 0.45f), Color.Transparent),
                            center = Offset(size.width * (0.92f - 0.22f * drift), size.height * (0.82f - 0.12f * drift)),
                            radius = size.width * 0.70f,
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(EchoColors.AbyssTeal.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(size.width * 0.45f, size.height * (0.55f + 0.18f * drift)),
                            radius = size.width * 0.55f,
                        )
                    )
                }
        )
        content()
    }
}

/** Cam görünümlü, basınca yumuşakça küçülen buton. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = EchoColors.NeonCyan,
    enabled: Boolean = true,
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
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) accent else EchoColors.TextSecondary.copy(alpha = 0.5f),
        )
    }
}
