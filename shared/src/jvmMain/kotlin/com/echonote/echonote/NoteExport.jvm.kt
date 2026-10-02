package com.echonote.echonote

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun exportNoteAsMarkdown(fileName: String, content: String) {
    // AWT FileDialog, Swing'in JFileChooser'ına göre Windows'ta yerel pencereyi açar.
    val dialog = FileDialog(null as Frame?, "Notu dışa aktar", FileDialog.SAVE).apply {
        file = fileName
        isVisible = true
    }
    val directory = dialog.directory ?: return
    val chosen = dialog.file ?: return
    val target = File(directory, if (chosen.endsWith(".md")) chosen else "$chosen.md")
    target.writeText(content)
}
