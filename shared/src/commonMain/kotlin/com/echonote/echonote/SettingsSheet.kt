package com.echonote.echonote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
            .background(EchoColors.SpaceBlack.copy(alpha = 0.94f), RoundedCornerShape(24.dp))
            .glass(RoundedCornerShape(24.dp))
            .imePadding()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ayarlar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EchoColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            GlassButton(text = "Kapat", onClick = onClose, accent = EchoColors.TextSecondary)
        }

        HorizontalDivider(color = EchoColors.GlassBorder)

        // --- Hesap ---
        Text(
            text = "Hesap",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.NeonCyan,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = accountEmail ?: "Çevrimdışı mod — hesap yok",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (accountEmail != null) {
                GlassButton(text = "Çıkış yap", onClick = onSignOut, accent = EchoColors.NeonRose)
            }
        }
        if (accountEmail != null) {
            Text(
                text = "Çıkış yapınca bu cihazdaki yerel kopya silinir; notların sunucuda kalır.",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.TextSecondary.copy(alpha = 0.7f),
            )
        }

        HorizontalDivider(color = EchoColors.GlassBorder)

        TrashEntryRow(count = trashCount, onOpen = onOpenTrash)

        HorizontalDivider(color = EchoColors.GlassBorder)

        // --- Gemini ---
        Text(
            text = "Gemini API anahtarı",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.NeonLavender,
        )
        Text(
            text = "Boş bırakılırsa AI eylemleri sahte modda çalışır. Anahtar yalnızca bu cihazda saklanır.",
            style = MaterialTheme.typography.labelSmall,
            color = EchoColors.TextSecondary.copy(alpha = 0.7f),
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
                    color = EchoColors.TextSecondary.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                textStyle = TextStyle(color = EchoColors.TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(EchoColors.NeonLavender),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Kaydet",
                onClick = { onGeminiApiKeyChange(keyDraft) },
                accent = EchoColors.NeonMint,
                enabled = keyDraft != geminiApiKey,
            )
            if (geminiApiKey.isNotEmpty()) {
                GlassButton(
                    text = "Sil",
                    onClick = {
                        keyDraft = ""
                        onGeminiApiKeyChange("")
                    },
                    accent = EchoColors.NeonRose,
                )
            }
        }
    }
}
