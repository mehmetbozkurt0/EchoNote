package com.echonote.echonote.model

import com.echonote.echonote.getPlatform
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Supabase "notes" tablosuyla birebir eşleşir:
 * id: uuid (pk), title: text, content: text, updated_at: timestamp, device_id: text.
 * updated_at + device_id, dağıtık senaryoda last-write-wins çakışma çözümü içindir.
 */
@Serializable
data class Note(
    val id: String,
    val title: String,
    val content: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("device_id") val deviceId: String,
    /**
     * Dolu ise not çöp kutusunda. Normal bir alan gibi senkronlanır: silme iki cihazda
     * da "çöpe taşındı" olarak görünür ve geri alınabilir. Kalıcı silme bundan ayrı bir
     * yol (yerel `pending_delete` tombstone'u → uzaktan gerçek DELETE).
     */
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    val isTrashed: Boolean get() = deletedAt != null
}

@OptIn(ExperimentalUuidApi::class)
fun newNoteId(): String = Uuid.random().toString()

/** ISO-8601 UTC zaman damgası; Postgres timestamp/timestamptz doğrudan kabul eder. */
@OptIn(ExperimentalTime::class)
fun nowIsoUtc(): String = Clock.System.now().toString()

/**
 * Bu süreç boyunca sabit cihaz kimliği; her yerel yazmada device_id kolonuna işlenir.
 * (Kalıcı depolama katmanı eklendiğinde diske taşınabilir.)
 */
@OptIn(ExperimentalUuidApi::class)
val localDeviceId: String by lazy {
    val platform = getPlatform().name.filter { it.isLetterOrDigit() }.take(16)
    "$platform-${Uuid.random().toString().take(8)}"
}

/**
 * Supabase'in döndürdüğü zaman damgalarını toleranslı çözümler:
 * timestamptz "+00:00" ofsetiyle, düz timestamp ise ofsetsiz gelir.
 */
@OptIn(ExperimentalTime::class)
fun parseTimestampOrNull(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { Instant.parse(value + "Z") }.getOrNull()

/**
 * Sahte AI: verilen içeriği "genişletilmiş" bir sürüme dönüştürür.
 * Gerçek bir LLM çağrısının yerini tutar; deterministiktir.
 */
fun mockAiExpand(title: String, content: String): String = buildString {
    appendLine(content.trimEnd())
    appendLine()
    appendLine("## Ayrıntılı Açıklama")
    appendLine()
    appendLine("Bu bölüm **$title** notunu derinleştirmek için üretildi. Yukarıdaki maddelerin her biri, projenin uzun vadeli hedefleriyle doğrudan ilişkilidir ve *önceliklendirilmiş* bir yol haritasına oturtulmalıdır.")
    appendLine()
    appendLine("### Sonraki Adımlar")
    appendLine()
    appendLine("- Ana başlıkları alt görevlere böl")
    appendLine("- Her görev için bir `zaman kutusu` belirle")
    appendLine("- Haftalık gözden geçirme ritmi kur")
    appendLine()
    append("> Unutma: iyi bir not, gelecekteki sana yazılmış bir mektuptur.")
}

/**
 * Sahte AI: verilen içeriği kısa bir özete indirger.
 */
fun mockAiCondense(title: String, content: String): String = buildString {
    appendLine("# $title — Özet")
    appendLine()
    val bullets = content
        .lineSequence()
        .map { it.trim().trimStart('#', '-', '*', '>', ' ') }
        .filter { it.isNotBlank() }
        .distinct()
        .take(3)
        .toList()
    bullets.forEach { appendLine("- ${it.take(60)}") }
    appendLine()
    append("*Bu özet sahte AI tarafından üretildi.*")
}
