package com.echonote.echonote

import android.content.Intent

actual fun exportNoteAsMarkdown(fileName: String, content: String) {
    val context = EchoNoteAndroid.requireContext()
    // Dosya yazıp FileProvider kurmak yerine metni doğrudan paylaşıyoruz: kullanıcı
    // hedefi (Drive, not uygulaması, e-posta) kendi seçiyor ve ek izin gerekmiyor.
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_TITLE, fileName)
        putExtra(Intent.EXTRA_SUBJECT, fileName.removeSuffix(".md"))
        putExtra(Intent.EXTRA_TEXT, content)
    }
    val chooser = Intent.createChooser(share, "Notu paylaş").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
