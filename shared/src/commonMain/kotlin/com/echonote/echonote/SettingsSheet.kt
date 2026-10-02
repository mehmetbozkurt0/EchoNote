package com.echonote.echonote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Yazı boyutu adımı; uçlar AppSettings'te sınırlı. */
private const val FONT_STEP = 0.1f

/**
 * Hesap ve anahtar ayarları. Gemini anahtarı artık burada yaşıyor — eskiden
 * `Secrets.kt`'de derlenip APK'nın içinde gidiyordu.
 */
@Composable
fun SettingsSheet(
    accountEmail: String?,
    geminiApiKey: String,
    onGeminiApiKeyChange: (String) -> Unit,
    trashCount: Int,
    onOpenTrash: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyDraft by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            // Cam katman %5 opak: tek basina altindaki liste icinden gecer ve panel
            // okunmaz olur. Once koyu bir zemin, sonra cam.
            .background(EchoColors.spaceBlack.copy(alpha = 0.94f), RoundedCornerShape(24.dp))
            .glass(RoundedCornerShape(24.dp))
            .imePadding()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ayarlar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EchoColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            GlassButton(text = "Kapat", onClick = onClose, accent = EchoColors.textSecondary)
        }

        HorizontalDivider(color = EchoColors.glassBorder)

        // --- Hesap ---
        Text(
            text = "Hesap",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.neonCyan,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = accountEmail ?: "Çevrimdışı mod — hesap yok",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (accountEmail != null) {
                GlassButton(text = "Çıkış yap", onClick = onSignOut, accent = EchoColors.neonRose)
            }
        }
        if (accountEmail != null) {
            Text(
                text = "Çıkış yapınca bu cihazdaki yerel kopya silinir; notların sunucuda kalır.",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.textSecondary.copy(alpha = 0.7f),
            )
        }

        HorizontalDivider(color = EchoColors.glassBorder)

        // --- Görünüm ---
        Text(
            text = "Görünüm",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.neonCyan,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                GlassButton(
                    text = when (mode) {
                        ThemeMode.System -> "Sistem"
                        ThemeMode.Dark -> "Koyu"
                        ThemeMode.Light -> "Açık"
                    },
                    onClick = { onThemeModeChange(mode) },
                    accent = if (mode == themeMode) EchoColors.neonCyan else EchoColors.textSecondary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Yazı boyutu  ${(fontScale * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = "A−",
                onClick = { onFontScaleChange(fontScale - FONT_STEP) },
                accent = EchoColors.textPrimary,
                enabled = fontScale > MIN_FONT_SCALE,
            )
            Spacer(Modifier.width(8.dp))
            GlassButton(
                text = "A+",
                onClick = { onFontScaleChange(fontScale + FONT_STEP) },
                accent = EchoColors.textPrimary,
                enabled = fontScale < MAX_FONT_SCALE,
            )
        }

        HorizontalDivider(color = EchoColors.glassBorder)

        TrashEntryRow(count = trashCount, onOpen = onOpenTrash)

        HorizontalDivider(color = EchoColors.glassBorder)

        // --- Gemini ---
        Text(
            text = "Gemini API anahtarı",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.neonLavender,
        )
        Text(
            text = "Boş bırakılırsa AI eylemleri sahte modda çalışır. Anahtar yalnızca bu cihazda saklanır.",
            style = MaterialTheme.typography.labelSmall,
            color = EchoColors.textSecondary.copy(alpha = 0.7f),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (keyDraft.isEmpty()) {
                Text(
                    text = "aistudio.google.com/apikey",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoColors.textSecondary.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                textStyle = TextStyle(color = EchoColors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(EchoColors.neonLavender),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Kaydet",
                onClick = { onGeminiApiKeyChange(keyDraft) },
                accent = EchoColors.neonMint,
                enabled = keyDraft != geminiApiKey,
            )
            if (geminiApiKey.isNotEmpty()) {
                GlassButton(
                    text = "Sil",
                    onClick = {
                        keyDraft = ""
                        onGeminiApiKeyChange("")
                    },
                    accent = EchoColors.neonRose,
                )
            }
        }
    }
}
