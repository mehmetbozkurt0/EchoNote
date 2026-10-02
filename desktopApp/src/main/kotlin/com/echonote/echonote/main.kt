package com.echonote.echonote

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Dimension

/** Kapanışta bekleyen yazmalar için tanınan en uzun süre. */
private const val FLUSH_TIMEOUT_MS = 3_000L

private val DefaultSize = DpSize(1100.dp, 760.dp)
private val MinimumSize = Dimension(560, 420)

fun main() = application {
    val saved = remember { AppServices.settings.windowState() }
    val windowState = rememberWindowState(
        width = saved?.width?.dp ?: DefaultSize.width,
        height = saved?.height?.dp ?: DefaultSize.height,
        position = if (saved?.x != null && saved.y != null) {
            WindowPosition(saved.x!!.dp, saved.y!!.dp)
        } else {
            WindowPosition.PlatformDefault
        },
    )

    // Boyut/konum değiştikçe kaydet; debounce ile sürükleme boyunca yazmayı engelliyoruz.
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.size to windowState.position }
            .debounce(500)
            .collect { (size, position) ->
                AppServices.settings.saveWindowState(
                    WindowPlacement(
                        width = size.width.value.toInt(),
                        height = size.height.value.toInt(),
                        x = (position as? WindowPosition.Absolute)?.x?.value?.toInt(),
                        y = (position as? WindowPosition.Absolute)?.y?.value?.toInt(),
                    )
                )
            }
    }

    Window(
        onCloseRequest = {
            // exitApplication JVM'i ağ çağrısı bitmeden kapattığı için debounce'lu
            // son düzenleme kaybolurdu. Kapanışı sınırlı bir süre bekletiyoruz —
            // uygulama zaten kapanıyor, bu yüzden AWT thread'ini bloke etmek kabul edilebilir.
            runBlocking { withTimeoutOrNull(FLUSH_TIMEOUT_MS) { SaveCoordinator.flushAll() } }
            exitApplication()
        },
        state = windowState,
        title = "EchoNote",
        icon = painterResource("icon.png"),
    ) {
        // Pencere çok küçültülünce iki panelli yerleşim okunmaz hale geliyordu.
        window.minimumSize = MinimumSize
        App()
    }
}
