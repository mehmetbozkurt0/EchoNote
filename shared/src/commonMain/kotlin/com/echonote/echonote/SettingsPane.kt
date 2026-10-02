package com.echonote.echonote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.parseTimestampOrNull
import com.echonote.echonote.model.relativeTime
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/**
 * Ayarlar — tasarımdaki "Ayarlar ve Kasa" sayfası. Ölçüler HTML'inden: başlık 28sp,
 * kart dolgusu 16dp, kartlar arası 24dp, kart içi 16dp.
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
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(
            start = ScreenMargin, end = ScreenMargin, top = 16.dp, bottom = 24.dp + bottomPadding,
        ),
        modifier = modifier.fillMaxSize().imePadding(),
    ) {
        item(key = "baslik") { PageTitle() }
        item(key = "hesap") { AccountCard(accountEmail, onSyncNow, onSignOut) }
        item(key = "gorunum") {
            AppearanceCard(themeMode, onThemeModeChange, fontScale, onFontScaleChange)
        }
        item(key = "cop") { TrashCard(trashed, onRestore, onDeleteForever) }
        item(key = "ai") { AiCard(geminiApiKey, onGeminiApiKeyChange) }
        item(key = "footer") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "EchoNote · ${trashed.size} not çöpte",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.textMuted,
                )
                Text(
                    text = "notlar cihazda saklanır · hesabınla eşitlenir",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.textMuted.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PageTitle() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Ayarlar ve Kasa",
                style = MaterialTheme.typography.displayMedium,
                color = EchoColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(40.dp).background(EchoColors.surfaceHigh, EchoShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = EchoColors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = "Kişisel yerel arşiv ve tercihler",
            style = MaterialTheme.typography.bodySmall,
            color = EchoColors.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Gruplanmış ayar kartı: ikon + başlık, sağda etiket, sonra içerik. */
@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(EchoShapes.card)
            .background(EchoColors.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = EchoColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun AccountCard(accountEmail: String?, onSyncNow: () -> Unit, onSignOut: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(EchoShapes.card)
            .background(EchoColors.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(EchoColors.primary.copy(alpha = 0.20f), EchoShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = EchoColors.primaryBright,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = accountEmail ?: "Çevrimdışı mod",
                    style = MaterialTheme.typography.labelLarge,
                    color = EchoColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(Modifier.size(6.dp).background(EchoColors.secondaryBright, EchoShapes.pill))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Çevrimdışı öncelikli",
                        style = MaterialTheme.typography.labelSmall,
                        color = EchoColors.secondary,
                    )
                }
            }
            if (accountEmail != null) {
                GhostButton(text = "Şimdi Eşitle", icon = Icons.Default.Sync, onClick = onSyncNow)
            }
        }

        // Tasarımda burada "Donanımsal AES-256 Şifreleme" yazıyordu; doğru değil —
        // notlar sunucuya düz metin gidiyor ve yerel veritabanı da şifreli değil.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoShapes.field)
                .background(EchoColors.surfaceLow)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SettingsSuggest,
                    contentDescription = null,
                    tint = EchoColors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Yerel SQLite deposu",
                    style = MaterialTheme.typography.labelLarge,
                    color = EchoColors.textPrimary,
                )
            }
            Text(
                text = "Notlar önce cihazdaki veritabanına yazılır, sonra hesabınla eşitlenir. " +
                    "Çevrimdışıyken de yazabilirsin.",
                style = MaterialTheme.typography.labelMedium,
                color = EchoColors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Cihaz: ${AppServices.settings.deviceId}",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (accountEmail != null) {
                GhostButton(
                    text = "Oturumu Kapat",
                    icon = Icons.Default.Logout,
                    onClick = onSignOut,
                    accent = EchoColors.danger,
                )
            }
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
    SettingsCard(
        title = "Görünüm & Tipografi",
        icon = Icons.Default.Palette,
        tint = EchoColors.primary,
        trailing = {
            Text(
                text = "Zen modu",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.accent,
            )
        },
    ) {
        Text(
            text = "Uygulama Teması",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.textSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoShapes.field)
                .background(EchoColors.surfaceLow)
                .padding(4.dp),
        ) {
            ThemeTab("Sistem", Icons.Default.SettingsSuggest, themeMode == ThemeMode.System, Modifier.weight(1f)) {
                onThemeModeChange(ThemeMode.System)
            }
            ThemeTab("Koyu", Icons.Default.DarkMode, themeMode == ThemeMode.Dark, Modifier.weight(1f)) {
                onThemeModeChange(ThemeMode.Dark)
            }
            ThemeTab("Açık", Icons.Default.LightMode, themeMode == ThemeMode.Light, Modifier.weight(1f)) {
                onThemeModeChange(ThemeMode.Light)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Yazı Karakter Boyutu",
                style = MaterialTheme.typography.labelMedium,
                color = EchoColors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Metin Ölçeği: %${(fontScale * 100).roundToInt()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = EchoColors.primaryBright,
            )
        }
        Slider(
            value = fontScale,
            onValueChange = { onFontScaleChange((it * 20f).roundToInt() / 20f) },
            valueRange = MIN_FONT_SCALE..MAX_FONT_SCALE,
            colors = SliderDefaults.colors(
                thumbColor = EchoColors.primary,
                activeTrackColor = EchoColors.primary,
                inactiveTrackColor = EchoColors.surfaceLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoShapes.field)
                .background(EchoColors.surfaceLow)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Okuma Önizlemesi",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = EchoColors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = "\"Duraklar ve sokaklar birer taslak gibi; zihin, yalnızca " +
                    "silinmeyen kelimelerin gölgesinde dinlenir.\"",
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = EchoColors.textPrimary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ThemeTab(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(EchoShapes.field)
            .background(if (active) EchoColors.surfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) EchoColors.textPrimary else EchoColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) EchoColors.textPrimary else EchoColors.textSecondary,
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun TrashCard(
    trashed: List<Note>,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
) {
    var pendingForever by remember { mutableStateOf<Note?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    SettingsCard(
        title = "Çöp Kutusu",
        icon = Icons.Default.DeleteSweep,
        tint = EchoColors.accent,
        trailing = {
            if (trashed.isNotEmpty()) {
                Box(
                    Modifier
                        .clip(EchoShapes.pill)
                        .background(EchoColors.accent.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${trashed.size} Not Bekliyor",
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
            color = EchoColors.textSecondary,
        )

        trashed.forEach { note ->
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(EchoShapes.field)
                    .background(EchoColors.surfaceLow)
                    .padding(14.dp),
            ) {
                Text(
                    text = note.title.ifBlank { "Adsız not" },
                    style = MaterialTheme.typography.titleSmall,
                    color = EchoColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = trashLine(note),
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(
                        text = "Geri Yükle",
                        icon = Icons.Default.Restore,
                        onClick = { onRestore(note.id) },
                        accent = EchoColors.primaryBright,
                    )
                    GhostButton(
                        text = "Kalıcı Sil",
                        icon = Icons.Default.DeleteForever,
                        onClick = { pendingForever = note },
                        accent = EchoColors.danger,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoShapes.field)
                .background(EchoColors.surfaceLow)
                .clickable { confirmEmpty = true }
                .padding(vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CleaningServices,
                contentDescription = null,
                tint = EchoColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Tüm Çöp Kutusunu Boşalt",
                style = MaterialTheme.typography.labelMedium,
                color = EchoColors.textMuted,
            )
        }
    }

    pendingForever?.let { note ->
        DeleteForeverDialog(
            note = note,
            onConfirm = { onDeleteForever(note.id); pendingForever = null },
            onDismiss = { pendingForever = null },
        )
    }

    if (confirmEmpty) {
        EmptyTrashDialog(
            count = trashed.size,
            onConfirm = {
                trashed.forEach { onDeleteForever(it.id) }
                confirmEmpty = false
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}

/** "Silindi: 4 gün önce • 26 gün sonra silinecektir" */
@OptIn(ExperimentalTime::class)
private fun trashLine(note: Note): String {
    val deleted = relativeTime(note.deletedAt.orEmpty())
    val removedAt = parseTimestampOrNull(note.deletedAt.orEmpty())?.plus(30.days)
    val left = removedAt?.let {
        val remaining = (it - Clock.System.now()).inWholeDays
        if (remaining <= 0) "yakında silinecek" else "$remaining gün sonra silinecek"
    }
    return listOfNotNull(deleted.takeIf { it.isNotEmpty() }?.let { "Silindi: $it" }, left)
        .joinToString(" • ")
}

@Composable
private fun AiCard(geminiApiKey: String, onGeminiApiKeyChange: (String) -> Unit) {
    var keyDraft by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var visible by remember { mutableStateOf(false) }

    SettingsCard(
        title = "Yapay Zekâ Modeli",
        icon = Icons.Default.SmartToy,
        tint = EchoColors.primary,
        trailing = {
            Text(
                text = "İsteğe Bağlı",
                style = MaterialTheme.typography.labelSmall,
                color = EchoColors.textMuted,
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Özel API Anahtarı",
                style = MaterialTheme.typography.labelMedium,
                color = EchoColors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { visible = !visible },
            ) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = EchoColors.primaryBright,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (visible) "Gizle" else "Göster",
                    style = MaterialTheme.typography.labelSmall,
                    color = EchoColors.primaryBright,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(EchoShapes.field)
                .background(EchoColors.surfaceLow)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (keyDraft.isEmpty()) {
                Text(
                    text = "aistudio.google.com/apikey",
                    style = MaterialTheme.typography.bodySmall,
                    color = EchoColors.textMuted.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = EchoColors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                ),
                cursorBrush = SolidColor(EchoColors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = "Anahtar boşken AI eylemleri sahte modda çalışır ve yalnızca bu cihazda saklanır.",
            style = MaterialTheme.typography.labelMedium,
            color = EchoColors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            if (geminiApiKey.isNotEmpty()) {
                GhostButton(text = "Temizle", onClick = { keyDraft = ""; onGeminiApiKeyChange("") })
            }
            PrimaryButton(
                text = "Kaydet",
                onClick = { onGeminiApiKeyChange(keyDraft) },
                enabled = keyDraft != geminiApiKey,
            )
        }
    }
}
