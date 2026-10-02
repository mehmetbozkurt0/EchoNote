package com.echonote.echonote

/**
 * Notu Markdown olarak dışarı verir. Masaüstünde dosya kaydetme penceresi açar,
 * Android'de paylaşım sayfasını (Intent.ACTION_SEND) gösterir — her platformda
 * oranın alışık olduğu davranış.
 */
expect fun exportNoteAsMarkdown(fileName: String, content: String)
