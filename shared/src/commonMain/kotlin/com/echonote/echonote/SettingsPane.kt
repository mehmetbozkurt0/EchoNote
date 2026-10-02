package com.echonote.echonote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.relativeTime

/** Yazı boyutu adımı; uçlar [MIN_FONT_SCALE] / [MAX_FONT_SCALE] ile sınırlı. */
private const val FONT_STEP = 0.1f

/**
 * Ayarlar — tasarımdaki "Ayarlar ve Kasa" sayfası. Artık bir panel değil tam ekran bir
 * sekme; çöp kutusu da ayrı bir pencere yerine burada, kendi kartında.
 */
@Composable
fun SettingsPane(
    accountEmail: String?,
    geminiApiKey: String,
    onGeminiApiKeyChange: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    trashed: List<Note>,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp + bottomPadding),
        modifier = modifier.fillMaxSize().imePadding(),
    ) {
        item(key = "hesap") { AccountCard(accountEmail, onSignOut) }
        item(key = "gorunum") {
            AppearanceCard(themeMode, onThemeModeChange, fontScale, onFontScaleChange)
        }
        item(key = "cop") { TrashCard(trashed, onRestore, onDeleteForever) }
        item(key = "ai") { AiCard(geminiApiKey, onGeminiApiKeyChange) }
        item(key = "footer") {
            Text(
                text = "EchoNote · notlar cihazda saklanır ve hesabınla eşitlenir",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** Gruplanmış ayar kartı: ikon + başlık, sonra içerik. */
@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().echoSurface(EchoShapes.sheet).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = EchoColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        content()
    }
}

/** `Column` içeriği için takma ad; kart gövdesi dikey yığın olarak yazılsın diye. */
private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun AccountCard(accountEmail: String?, onSignOut: () -> Unit) {
    SettingsCard(title = "Hesap", icon = Icons.Default.Person, tint = EchoColors.primaryBright) {
        Text(
            text = accountEmail ?: "Çevrimdışı mod — hesap yok",
            style = MaterialTheme.typography.bodyMedium,
            color = EchoColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (accountEmail != null) {
            Text(
                // Tasarımda burada "AES-256 şifreleme" yazıyordu; doğru değil, notlar
                // sunucuya düz metin gidiyor ve yerel veritabanı da şifreli değil.
                text = "Notlar cihazdaki SQLite veritabanında tutulur ve hesabınla " +
                    "eşitlenir. Çıkış yapınca bu cihazdaki yerel kopya silinir; " +
                    "notların sunucuda kalır.",
                style = MaterialTheme.typography.labelMedium,
                color = EchoColors.textSecondary.copy(alpha = 0.8f),
            )
            GhostButton(
                text = "Oturumu kapat",
                icon = Icons.Default.Logout,
                onClick = onSignOut,
                accent = EchoColors.danger,
            )
        }
    }
}

@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
) {
    SettingsCard(title = "Görünüm", icon = Icons.Default.Palette, tint = EchoColors.primaryBright) {
        Text(
            text = "Uygulama teması",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.System -> "Sistem"
                    ThemeMode.Dark -> "Koyu"
                    ThemeMode.Light -> "Açık"
                }
                if (mode == themeMode) {
                    PrimaryButton(text = label, onClick = { onThemeModeChange(mode) })
                } else {
                    GhostButton(text = label, onClick = { onThemeModeChange(mode) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Yazı boyutu",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%${(fontScale * 100).toInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = EchoColors.primaryBright,
                modifier = Modifier.padding(end = 10.dp),
            )
            GhostButton(
                text = "A−",
                onClick = { onFontScaleChange(fontScale - FONT_STEP) },
                enabled = fontScale > MIN_FONT_SCALE,
            )
            Spacer(Modifier.width(8.dp))
            GhostButton(
                text = "A+",
                onClick = { onFontScaleChange(fontScale + FONT_STEP) },
                enabled = fontScale < MAX_FONT_SCALE,
            )
        }
    }
}

@Composable
private fun TrashCard(
    trashed: List<Note>,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
) {
    var pendingForever by remember { mutableStateOf<Note?>(null) }

    SettingsCard(
        title = "Çöp Kutusu",
        icon = Icons.Default.DeleteSweep,
        tint = EchoColors.accent,
        trailing = {
            if (trashed.isNotEmpty()) {
                Box(
                    Modifier
                        .echoSurface(EchoShapes.pill, fill = EchoColors.accent.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${trashed.size} not",
                        style = MaterialTheme.typography.labelSmall,
                        color = EchoColors.accent,
                    )
                }
            }
        },
    ) {
        if (trashed.isEmpty()) {
            Text(
                text = "Çöp kutusu boş.",
                style = MaterialTheme.typography.bodyMedium,
                color = EchoColors.textSecondary,
            )
            return@SettingsCard
        }
        Text(
            text = "Çöp kutusundaki notlar 30 gün sonra kalıcı olarak silinir.",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.textSecondary.copy(alpha = 0.8f),
        )
        trashed.forEach { note ->
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .echoSurface(EchoShapes.card, fill = EchoColors.surfaceLow)
                    .padding(16.dp),
            ) {
                Text(
                    text = note.title.ifBlank { "Adsız not" },
                    style = MaterialTheme.typography.titleSmall,
                    color = EchoColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val age = relativeTime(note.deletedAt.orEmpty())
                if (age.isNotEmpty()) {
                    Text(
                        text = "Silindi: $age",
                        style = MaterialTheme.typography.labelSmall,
                        color = EchoColors.textSecondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(
                        text = "Geri yükle",
                        icon = Icons.Default.RestoreFromTrash,
                        onClick = { onRestore(note.id) },
                        accent = EchoColors.sync,
                    )
                    GhostButton(
                        text = "Kalıcı sil",
                        onClick = { pendingForever = note },
                        accent = EchoColors.danger,
                    )
                }
            }
        }
    }

    pendingForever?.let { note ->
        DeleteForeverDialog(
            note = note,
            onConfirm = {
                onDeleteForever(note.id)
                pendingForever = null
            },
            onDismiss = { pendingForever = null },
        )
    }
}

@Composable
private fun AiCard(geminiApiKey: String, onGeminiApiKeyChange: (String) -> Unit) {
    var keyDraft by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }

    SettingsCard(
        title = "Yapay Zekâ",
        icon = Icons.Default.SmartToy,
        tint = EchoColors.primary,
        trailing = {
            Text(
                text = "isteğe bağlı",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.textSecondary,
            )
        },
    ) {
        Text(
            text = "Boş bırakılırsa AI eylemleri sahte modda çalışır. Anahtar yalnızca bu cihazda saklanır.",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.textSecondary.copy(alpha = 0.8f),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .echoSurface(EchoShapes.field, fill = EchoColors.surfaceLow)
                .padding(horizontal = 14.dp, vertical = 13.dp),
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
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = EchoColors.textPrimary),
                cursorBrush = SolidColor(EchoColors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (geminiApiKey.isNotEmpty()) {
                GhostButton(
                    text = "Temizle",
                    onClick = {
                        keyDraft = ""
                        onGeminiApiKeyChange("")
                    },
                )
            }
            PrimaryButton(
                text = "Kaydet",
                onClick = { onGeminiApiKeyChange(keyDraft) },
                enabled = keyDraft != geminiApiKey,
            )
        }
    }
}
