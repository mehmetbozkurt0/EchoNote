package com.echonote.echonote

import com.echonote.echonote.model.Note
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Var olan bir not okumak için, yeni bir not yazmak için açılır.
 *
 * Boş içerikli not istisnadır: okunacak bir şey yoksa okuma modu boş bir ekran
 * gösterirdi, o yüzden doğrudan düzenleyici açılır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadModeDefaultTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun note(id: String, content: String) = Note(
        id = id, title = "Not $id", content = content,
        updatedAt = "2026-01-01T00:00:00Z", deviceId = "t",
    )

    private fun viewModel(repo: FakeNotesRepository) =
        NotesViewModel(repository = repo, aiService = FakeAiService(), deviceId = "test-cihaz")

    @Test
    fun varOlanNotOkumaModundaAcilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        repo.emitNotes(listOf(note("a", "dolu içerik"), note("b", "başka not")))
        advanceUntilIdle()

        vm.selectNote("b")
        runCurrent()

        assertTrue(vm.uiState.value.readMode, "Var olan not okuma modunda açılmalı")
    }

    @Test
    fun yeniNotDuzenlemeModundaAcilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        repo.emitNotes(listOf(note("a", "dolu içerik")))
        advanceUntilIdle()
        vm.selectNote("a")
        runCurrent()
        assertTrue(vm.uiState.value.readMode)

        vm.createNote()
        runCurrent()

        assertFalse(vm.uiState.value.readMode, "Yeni not yazmak için açılmalı")
    }

    @Test
    fun bosNotDuzenlemeModundaAcilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        repo.emitNotes(listOf(note("a", "dolu"), note("bos", "   ")))
        advanceUntilIdle()

        vm.selectNote("bos")
        runCurrent()

        assertFalse(vm.uiState.value.readMode, "Okunacak içerik yoksa düzenleyici açılmalı")
    }

    @Test
    fun kullanicininSectigiModNotDegisenekadarKorunur() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        repo.emitNotes(listOf(note("a", "dolu"), note("b", "başka")))
        advanceUntilIdle()
        vm.selectNote("a")
        runCurrent()

        vm.toggleReadMode()
        runCurrent()
        assertFalse(vm.uiState.value.readMode, "Kullanıcı düzenleyiciye geçebilmeli")

        // Başka nota geçince varsayılan yeniden uygulanır.
        vm.selectNote("b")
        runCurrent()
        assertTrue(vm.uiState.value.readMode)
    }
}
