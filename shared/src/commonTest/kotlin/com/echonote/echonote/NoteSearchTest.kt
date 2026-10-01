package com.echonote.echonote

import com.echonote.echonote.model.Note
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteSearchTest {

    private fun note(id: String, title: String, content: String) =
        Note(id = id, title = title, content = content, updatedAt = "2026-01-01T00:00:00Z", deviceId = "t")

    private val notes = listOf(
        note("a", "Alışveriş", "Kahve ve süt alınacak"),
        note("b", "Toplantı", "Sprint planlama ÇOK önemli"),
        note("c", "Şiir", "İçimde bir uğultu var"),
    )

    private fun search(query: String) =
        NotesUiState(notes = notes, searchQuery = query).visibleNotes.map { it.id }

    @Test
    fun bosSorguTumListeyiDondurur() {
        assertContentEquals(listOf("a", "b", "c"), search(""))
        assertContentEquals(listOf("a", "b", "c"), search("   "))
    }

    @Test
    fun baslikVeIcerikteArar() {
        assertContentEquals(listOf("a"), search("Alışveriş"))
        assertContentEquals(listOf("a"), search("kahve"))
        assertContentEquals(listOf("b"), search("sprint"))
    }

    @Test
    fun buyukKucukHarfFarkEtmez() {
        assertContentEquals(listOf("b"), search("TOPLANTI"))
        assertContentEquals(listOf("b"), search("çok"))
        assertContentEquals(listOf("b"), search("ÇOK"))
    }

    /**
     * Türkçe'nin asıl tuzağı: "I/ı/İ/i" ve şapkalı harfler. SQLite LIKE bunları
     * yanlış eşleştirdiği için filtreleme Kotlin tarafında yapılıyor.
     */
    @Test
    fun turkceHarflerAsciiKarsiligiylaDaBulunur() {
        assertContentEquals(listOf("a"), search("alisveris"))
        assertContentEquals(listOf("c"), search("siir"))
        assertContentEquals(listOf("c"), search("icimde"))
        assertContentEquals(listOf("c"), search("ugultu"))
    }

    @Test
    fun eslesmeYoksaBosDoner() {
        assertTrue(search("bulunmayankelime").isEmpty())
    }

    @Test
    fun aramaSeciliNotuEtkilemez() {
        // Seçili not filtreden düşse bile editör açık kalmalı: yazarken arama yapmak
        // notu kapatmamalı.
        val state = NotesUiState(notes = notes, searchQuery = "sprint", selectedNoteId = "a")
        assertEquals("a", state.selectedNoteId)
        assertTrue(state.hasSelection)
        assertContentEquals(listOf("b"), state.visibleNotes.map { it.id })
    }

    @Test
    fun searchKeyTurkceHarfleriNormallestirir() {
        assertEquals("isgucu", "İŞGÜCÜ".searchKey())
        assertEquals("ioglu", "Ioğlu".searchKey())
        assertEquals("cocuk", "ÇOCUK".searchKey())
    }
}
