package com.echonote.echonote

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "EchoNote",
        icon = painterResource("icon.png"),
    ) {
        App()
    }
}