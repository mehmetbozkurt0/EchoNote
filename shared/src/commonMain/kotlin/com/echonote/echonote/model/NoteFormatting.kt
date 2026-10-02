package com.echonote.echonote.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Not başlığını içerikten türetir. Listede birden fazla "Yeni Not" birikmesinin sebebi
 * başlığın elle yazılmayı beklemesiydi; artık ilk anlamlı satırdan geliyor.
 *
 * Öncelik: ilk `#` başlığı → ilk boş olmayan satır → "Adsız not".
 */
fun deriveTitle(content: String, fallback: String = "Adsız not"): String {
    val heading = content.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("#") }
        ?.trimStart('#', ' ')
        ?.trim()
    val candidate = heading?.takeIf { it.isNotBlank() }
        ?: content.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
        ?: return fallback

    // Markdown işaretlerini başlıktan temizle; liste bunları ham göstermemeli.
    // Baştaki '#'ler de gider: "###" gibi içeriği boş bir başlık satırı yedek aday
    // olarak seçildiğinde başlık "###" olarak kalıyordu.
    val cleaned = candidate
        .replace(Regex("""^\s*#+\s*"""), "")
        .replace(Regex("""[*_`>]"""), "")
        .replace(Regex("""^\s*[-+]\s+"""), "")
        .replace(Regex("""^\s*\d+[.)]\s+"""), "")
        .trim()

    if (cleaned.isBlank()) return fallback
    return if (cleaned.length <= MAX_TITLE) cleaned else cleaned.take(MAX_TITLE).trimEnd() + "…"
}

private const val MAX_TITLE = 60

/** Editördeki sayaç: kelime ve karakter. */
data class TextStats(val words: Int, val characters: Int)

fun textStats(content: String): TextStats = TextStats(
    words = content.split(Regex("""\s+""")).count { it.isNotBlank() },
    characters = content.length,
)

/**
 * Listede "ne zaman" bilgisi. Veri zaten elimizdeydi ama hiç gösterilmiyordu, bu yüzden
 * hangi notun taze olduğu anlaşılmıyordu.
 */
@OptIn(ExperimentalTime::class)
fun relativeTime(updatedAt: String, now: Instant = Clock.System.now()): String {
    val then = parseTimestampOrNull(updatedAt) ?: return ""
    val seconds = (now - then).inWholeSeconds
    return when {
        seconds < 0 -> "az önce"
        seconds < 60 -> "az önce"
        seconds < 3_600 -> "${seconds / 60} dk önce"
        seconds < 86_400 -> "${seconds / 3_600} saat önce"
        seconds < 172_800 -> "dün"
        seconds < 2_592_000 -> "${seconds / 86_400} gün önce"
        seconds < 31_536_000 -> "${seconds / 2_592_000} ay önce"
        else -> "${seconds / 31_536_000} yıl önce"
    }
}

/** Notu Markdown dosyası olarak dışa aktarmak için içerik + güvenli dosya adı. */
fun exportFileName(title: String): String {
    val safe = title
        .replace(Regex("""[\\/:*?"<>|]"""), "")
        .replace(Regex("""\s+"""), "-")
        .trim('-', '.')
        .take(50)
    return (safe.ifBlank { "not" }) + ".md"
}
