package com.echonote.echonote

import com.echonote.echonote.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Yazma artık her tuş vuruşunda değil, yazmaya ara verilince diske iniyor.
 *
 * Ölçüm bunu gerektirdi: her vuruştaki `saveNote` tüm not listesini yeniden
 * sorgulatıp besteletiyordu ve 12 karakterlik boş bir notta bile p90 = 36 ms
 * kare maliyeti çıkıyordu.
 *
 * Buradaki testler asıl riski koruyor: geciktirme **veri kaybettirmemeli**.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersistDebounceTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun note(id: String, content: String = "icerik") =
        Note(id = id, title = "Not $id", content = content, updatedAt = "2026-01-01T00:00:00Z", deviceId = "t")

    private suspend fun hazirla(repo: FakeNotesRepository, vararg notes: Note): NotesViewModel {
        val vm = NotesViewModel(repository = repo, aiService = FakeAiService(), deviceId = "test-cihaz")
        repo.emitNotes(notes.toList())
        return vm
    }

    @Test
    fun ardArdaYazmaTekKayitUretir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = hazirla(repo, note("a"))
        advanceUntilIdle()
        vm.selectNote("a")
        repo.saved.clear()

        "merhaba".forEachIndexed { i, _ -> vm.updateContent("merhaba".take(i + 1)) }
        runCurrent()
        assertTrue(repo.saved.isEmpty(), "Yazarken her harf diske inmemeli")

        advanceTimeBy(500)
        runCurrent()

        assertEquals(1, repo.saved.size, "Ara verilince tek yazma olmalı")
        assertEquals("merhaba", repo.saved.single().content)
    }

    @Test
    fun aradaBeklenirseIkiKayitOlur() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = hazirla(repo, note("a"))
        advanceUntilIdle()
        vm.selectNote("a")
        repo.saved.clear()

        vm.updateContent("ilk")
        advanceTimeBy(500)
        runCurrent()
        vm.updateContent("ilk ve ikinci")
        advanceTimeBy(500)
        runCurrent()

        assertEquals(listOf("ilk", "ilk ve ikinci"), repo.saved.map { it.content })
    }

    @Test
    fun notDegistirmedenOnceBekleyenYazmaDiskeIner() = runTest(dispatcher) {
        // En tehlikeli senaryo: yazıp hemen başka nota geçmek. Bekleyen yazma
        // boşaltılmazsa ya kaybolur ya da yeni notun üstüne yazar.
        val repo = FakeNotesRepository()
        val vm = hazirla(repo, note("a"), note("b"))
        advanceUntilIdle()
        vm.selectNote("a")
        repo.saved.clear()

        vm.updateContent("a notuna yazildi")
        runCurrent()
        vm.selectNote("b")
        runCurrent()

        val yazilan = repo.saved.single()
        assertEquals("a", yazilan.id, "Yazma eski nota gitmeli")
        assertEquals("a notuna yazildi", yazilan.content)
    }

    @Test
    fun silinenNotBekleyenYazmaylaDirilmez() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = hazirla(repo, note("a"))
        advanceUntilIdle()
        vm.selectNote("a")
        repo.saved.clear()

        vm.updateContent("silinmek uzere")
        runCurrent()
        vm.requestDelete("a")
        vm.confirmDelete()
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(repo.saved.isEmpty(), "Silinen not geri yazılmamalı")
        assertEquals(listOf("a"), repo.deleted)
    }

    @Test
    fun gorevKutusuHemenYazar() = runTest(dispatcher) {
        // Okuma modunda bir görev kutusuna dokunmak ayrık bir eylem: yazmak gibi
        // beklememeli, dokunur dokunmaz diske inmeli.
        val repo = FakeNotesRepository()
        val vm = hazirla(repo, note("a", content = "- [ ] madde"))
        advanceUntilIdle()
        vm.selectNote("a")
        repo.saved.clear()

        vm.toggleTask(lineIndex = 0)
        runCurrent()

        assertEquals("- [x] madde", repo.saved.single().content)
    }
}
