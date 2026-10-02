package com.echonote.echonote

import com.echonote.echonote.model.Note
import com.echonote.echonote.model.normalizeTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TagsAndPinTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun note(id: String, tags: List<String> = emptyList(), pinned: Boolean = false) =
        Note(
            id = id, title = "Not $id", content = "içerik $id",
            updatedAt = "2026-01-01T00:00:00Z", deviceId = "t",
            tags = tags, pinned = pinned,
        )

    // --- Normalleştirme ---

    @Test
    fun etiketNormallestirilir() {
        assertEquals("iş", normalizeTag("  #İş  "))
        assertEquals("yapılacaklar", normalizeTag("Yapılacaklar"))
        assertEquals("iki-kelime", normalizeTag("iki kelime"))
        assertEquals("", normalizeTag("   "))
    }

    // --- Filtreleme ---

    @Test
    fun etiketFiltresiListeyiDaraltir() {
        val notes = listOf(note("a", listOf("iş")), note("b", listOf("ev")), note("c"))
        val state = NotesUiState(notes = notes, activeTag = "iş")
        assertContentEquals(listOf("a"), state.visibleNotes.map { it.id })
    }

    @Test
    fun etiketFiltresiAramaylaBirlikteCalisir() {
        val notes = listOf(
            note("a", listOf("iş")).copy(title = "rapor"),
            note("b", listOf("iş")).copy(title = "toplantı"),
        )
        val state = NotesUiState(notes = notes, activeTag = "iş", searchQuery = "rapor")
        assertContentEquals(listOf("a"), state.visibleNotes.map { it.id })
    }

    @Test
    fun tumEtiketlerTekilVeSirali() {
        val notes = listOf(note("a", listOf("zebra", "ev")), note("b", listOf("ev", "araba")))
        assertContentEquals(listOf("araba", "ev", "zebra"), NotesUiState(notes = notes).allTags)
    }

    @Test
    fun filtreYokkenTumNotlarGorunur() {
        val notes = listOf(note("a", listOf("iş")), note("b"))
        assertEquals(2, NotesUiState(notes = notes).visibleNotes.size)
    }

    // --- ViewModel eylemleri ---

    @Test
    fun ayniEtiketIkiKezEklenmez() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = NotesViewModel(repo, FakeAiService())
        repo.emitNotes(listOf(note("a", listOf("iş"))))
        advanceUntilIdle()
        vm.selectNote("a")

        vm.addTag("#İş")
        runCurrent()

        assertTrue(repo.saved.isEmpty(), "Zaten var olan etiket yeniden yazılmamalı")
    }

    @Test
    fun etiketEklenirVeNormallestirilmisHaliyleKaydedilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = NotesViewModel(repo, FakeAiService())
        repo.emitNotes(listOf(note("a")))
        advanceUntilIdle()
        vm.selectNote("a")

        vm.addTag("  Yapılacaklar ")
        runCurrent()

        assertContentEquals(listOf("yapılacaklar"), repo.saved.last().tags)
    }

    @Test
    fun etiketKaldirilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = NotesViewModel(repo, FakeAiService())
        repo.emitNotes(listOf(note("a", listOf("iş", "ev"))))
        advanceUntilIdle()
        vm.selectNote("a")

        vm.removeTag("iş")
        runCurrent()

        assertContentEquals(listOf("ev"), repo.saved.last().tags)
    }

    @Test
    fun sabitlemeDepoyaIletilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = NotesViewModel(repo, FakeAiService())
        repo.emitNotes(listOf(note("a", pinned = false)))
        advanceUntilIdle()

        vm.togglePinned("a")
        runCurrent()

        assertEquals(listOf("a" to true), repo.pinnedCalls)
    }

    @Test
    fun yazarkenEtiketVeSabitlemeKaybolmaz() = runTest(dispatcher) {
        // persistEditor yeni bir Note kuruyor; etiketleri taşımazsa yazmak onları siler.
        val repo = FakeNotesRepository()
        val vm = NotesViewModel(repo, FakeAiService())
        repo.emitNotes(listOf(note("a", listOf("iş"), pinned = true)))
        advanceUntilIdle()
        vm.selectNote("a")

        vm.updateContent("yeni metin")
        runCurrent()

        val saved = repo.saved.last()
        assertContentEquals(listOf("iş"), saved.tags)
        assertTrue(saved.pinned)
    }
}
