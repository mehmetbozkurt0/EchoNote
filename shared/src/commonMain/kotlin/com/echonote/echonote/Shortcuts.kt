package com.echonote.echonote

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Masaüstü klavye kısayolları. Android'de de zararsız: fiziksel klavye bağlıysa çalışır,
 * yoksa hiç tetiklenmez.
 *
 * `onPreviewKeyEvent` kullanılıyor ki metin alanları odaktayken de yakalansın; her dal
 * `true` döndürerek olayı tüketiyor, böylece kısayol editöre harf olarak düşmüyor.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.appShortcuts(
    onNewNote: () -> Unit,
    onFocusSearch: () -> Unit,
    onToggleRead: () -> Unit,
    onEscape: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val command = event.isCtrlPressed || event.isMetaPressed
    when {
        command && event.key == Key.N -> { onNewNote(); true }
        command && event.key == Key.F -> { onFocusSearch(); true }
        command && event.key == Key.E -> { onToggleRead(); true }
        event.key == Key.Escape -> { onEscape(); true }
        else -> false
    }
}
