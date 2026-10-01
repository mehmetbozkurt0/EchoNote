package com.echonote.echonote

import com.echonote.echonote.model.Note
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Supabase'e giden JSON'un biçimi. Sahada yakalandı: `deletedAt`'in varsayılanı null
 * olduğu için `encodeDefaults = false` ile alan JSON'a hiç yazılmıyordu; Supabase
 * upsert'i o kolona dokunmuyor ve **çöpten geri yükleme sunucuya ulaşmıyordu** —
 * not bir sonraki pull'da sessizce yeniden çöpe düşüyordu.
 */
class NoteSerializationTest {

    /** Services.kt'deki Supabase istemcisiyle aynı yapılandırma. */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val note = Note(
        id = "a",
        title = "Baslik",
        content = "Icerik",
        updatedAt = "2026-01-01T00:00:00Z",
        deviceId = "d",
    )

    @Test
    fun coptenGeriYuklemeIcinDeletedAtNullOlarakGonderilir() {
        val encoded = json.encodeToString(Note.serializer(), note.copy(deletedAt = null))

        assertTrue(
            encoded.contains("\"deleted_at\":null"),
            "deleted_at JSON'a null olarak yazılmalı, yoksa sunucudaki eski damga silinmez: $encoded",
        )
    }

    @Test
    fun copeTasimaDamgasiGonderilir() {
        val encoded = json.encodeToString(Note.serializer(), note.copy(deletedAt = "2026-02-01T00:00:00Z"))

        assertTrue(encoded.contains("\"deleted_at\":\"2026-02-01T00:00:00Z\""), encoded)
    }

    @Test
    fun sunucudanGelenEksikAlanlarVeFazlaKolonlarSorunCikarmaz() {
        // user_id gibi modelde olmayan kolonlar yok sayılmalı; deleted_at yoksa null olmalı.
        val fromServer = """
            {"id":"a","title":"t","content":"c","updated_at":"2026-01-01T00:00:00Z",
             "device_id":"d","user_id":"00000000-0000-0000-0000-000000000000"}
        """.trimIndent()

        val decoded = json.decodeFromString(Note.serializer(), fromServer)

        assertEquals("a", decoded.id)
        assertEquals(null, decoded.deletedAt)
    }
}
