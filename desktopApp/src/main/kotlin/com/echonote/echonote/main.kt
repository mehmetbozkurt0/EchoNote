package com.echonote.echonote

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Kapanışta bekleyen yazmalar için tanınan en uzun süre. */
private const val FLUSH_TIMEOUT_MS = 3_000L

fun main() = application {
    Window(
        onCloseRequest = {
            // exitApplication JVM'i ağ çağrısı bitmeden kapattığı için debounce'lu
            // son düzenleme kaybolurdu. Kapanışı sınırlı bir süre bekletiyoruz —
            // uygulama zaten kapanıyor, bu yüzden AWT thread'ini bloke etmek kabul edilebilir.
            runBlocking { withTimeoutOrNull(FLUSH_TIMEOUT_MS) { SaveCoordinator.flushAll() } }
            exitApplication()
        },
        title = "EchoNote",
        icon = painterResource("icon.png"),
    ) {
        App()
    }
}
