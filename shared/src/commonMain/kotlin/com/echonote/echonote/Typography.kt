package com.echonote.echonote

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import echonote.shared.generated.resources.Res
import echonote.shared.generated.resources.jakarta_bold
import echonote.shared.generated.resources.jakarta_medium
import echonote.shared.generated.resources.jakarta_regular
import echonote.shared.generated.resources.jakarta_semibold
import org.jetbrains.compose.resources.Font

/**
 * Plus Jakarta Sans — tasarım sisteminin yazı ailesi.
 *
 * Google Fonts yalnızca değişken (variable) sürümü yayınlıyor; Compose'un ortak kod
 * tarafında eksen ayarı taşınabilir değil, bu yüzden dört ağırlık `fontTools` ile statik
 * örneklere ayrıştırılıp gömüldü. CDN'den çekilmiyor: uygulama çevrimdışı-önce, yazı
 * tipinin ağa bağlı olması baştaki tasarım ilkesiyle çelişirdi.
 */
@Composable
fun jakartaFamily(): FontFamily = FontFamily(
    Font(Res.font.jakarta_regular, FontWeight.Normal),
    Font(Res.font.jakarta_medium, FontWeight.Medium),
    Font(Res.font.jakarta_semibold, FontWeight.SemiBold),
    Font(Res.font.jakarta_bold, FontWeight.Bold),
)

/**
 * DESIGN.md'deki ölçek birebir.
 *
 * Material3'ün yuvalarına şöyle oturuyor: `headline*` → tasarımın başlıkları,
 * `body*` → gövde, `label*` → üstveri ve eylem metinleri. Boyutlar `sp`; yazı boyutu
 * ayarı [EchoTheme] içinde yoğunluğun `fontScale`'inden geçtiği için burada çarpan yok.
 *
 * Satır yüksekliği uzun metinde bilinçli olarak geniş (17/28 ≈ 1.65): bu bir günlük
 * uygulaması, gece uzun uzun okunuyor.
 */
@Composable
fun echoTypography(): Typography {
    val f = jakartaFamily()
    fun style(size: Int, line: Int, weight: FontWeight, tracking: Float) = TextStyle(
        fontFamily = f,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.em,
    )
    return Typography(
        displayLarge = style(34, 42, FontWeight.Bold, -0.03f),
        displayMedium = style(28, 36, FontWeight.Bold, -0.025f),
        headlineLarge = style(26, 34, FontWeight.SemiBold, -0.02f),
        headlineMedium = style(21, 28, FontWeight.SemiBold, -0.015f),
        headlineSmall = style(18, 24, FontWeight.SemiBold, -0.01f),
        titleLarge = style(21, 28, FontWeight.SemiBold, -0.015f),
        titleMedium = style(18, 24, FontWeight.SemiBold, -0.01f),
        titleSmall = style(15, 20, FontWeight.SemiBold, 0f),
        bodyLarge = style(17, 28, FontWeight.Normal, -0.01f),
        bodyMedium = style(15, 25, FontWeight.Normal, 0f),
        bodySmall = style(13, 20, FontWeight.Normal, 0.01f),
        labelLarge = style(14, 18, FontWeight.SemiBold, 0.01f),
        labelMedium = style(12, 16, FontWeight.Medium, 0.02f),
        labelSmall = style(10, 14, FontWeight.SemiBold, 0.04f),
    )
}
