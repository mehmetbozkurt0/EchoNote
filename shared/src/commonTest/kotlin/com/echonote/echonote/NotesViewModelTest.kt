package com.echonote.echonote

import com.echonote.echonote.data.ConnectionState
import com.echonote.echonote.data.SyncState
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ViewModel artık yalnızca UI durumundan sorumlu: senkron defteri veri katmanına taşındı
 * (bkz. OfflineFirstNotesRepositoryTest, NotesLocalStoreTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun note(id: String, content: String = "içerik-$id", title: String = "Not $id") =
        Note(id = id, title = title, content = content, updatedAt = "2026-01-01T00:00:00Z", deviceId = "test")

    private fun viewModel(repo: FakeNotesRepository, ai: FakeAiService = FakeAiService()) =
        NotesViewModel(repository = repo, aiService = ai)

    // --- Editör draft'ı: yerel depoya yazıp geri okumanın gecikmesini gizler ---

    @Test
    fun depodanGelenListeKullaniciYazarkenEditoruEzmez() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        repo.emitNotes(listOf(note("a", content = "eski")))
        advanceUntilIdle()

        vm.updateContent("kullanıcının yazdığı")
        runCurrent()

        // DB akışı bir an geride: henüz eski içeriği yayınlıyor.
        repo.emitNotes(listOf(note("a", content = "eski")))
        runCurrent()

        assertEquals(
            "kullanıcının yazdığı",
            vm.uiState.value.editorContent,
            "Depo yayını editördeki metni geri almamalı (imleç zıplaması / karakter kaybı)",
        )
    }

    @Test
    fun yazmaDepoyaDamgalıNotOlarakGider() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        repo.emitNotes(listOf(note("a")))
        advanceUntilIdle()

        vm.updateTitle("Yeni Başlık")
        vm.updateContent("yeni içerik")
        runCurrent()

        val last = repo.saved.last()
        assertEquals("a", last.id)
        assertEquals("Yeni Başlık", last.title)
        assertEquals("yeni içerik", last.content)
        // Her yazma updated_at damgasını yeniler; LWW buna dayanıyor.
        assertTrue(last.updatedAt.isNotBlank())
    }

    @Test
    fun yeniNotListeyeDusmedenEditorAcilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.createNote()
        runCurrent()

        // Not henüz DB akışıyla listeye düşmedi, ama editör açık olmalı.
        assertTrue(vm.uiState.value.notes.isEmpty())
        assertTrue(vm.uiState.value.hasSelection)
        assertEquals("Yeni Not", vm.uiState.value.editorTitle)
        assertEquals(1, repo.saved.size)
    }

    // --- Seçim yönetimi ---

    @Test
    fun seciliNotKaybolursaKomsuyaGecilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        repo.emitNotes(listOf(note("a"), note("b")))
        advanceUntilIdle()

        vm.selectNote("a")
        runCurrent()
        assertEquals("a", vm.uiState.value.selectedNoteId)

        // Başka bir cihazda silindi: artık listede yok.
        repo.emitNotes(listOf(note("b")))
        runCurrent()

        assertEquals("b", vm.uiState.value.selectedNoteId)
        assertEquals("içerik-b", vm.uiState.value.editorContent)
    }

    @Test
    fun silmeDepoyaIletilirVeSecimBirakilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        repo.emitNotes(listOf(note("a")))
        advanceUntilIdle()
        vm.selectNote("a")

        vm.deleteNote("a")
        runCurrent()

        assertContentEquals(listOf("a"), repo.deleted)
        assertFalse(vm.uiState.value.hasSelection)
    }

    // --- AI akışı ---

    @Test
    fun aiAkisiTamamlanincaIcerikKalicilasirVeUndoDolar() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo, FakeAiService(result = "# Genişletilmiş\n\nmetin"))
        repo.emitNotes(listOf(note("a", content = "kısa")))
        advanceUntilIdle()
        vm.selectNote("a")
        val savesBefore = repo.saved.size

        vm.expandSelected()
        advanceUntilIdle()

        assertEquals("# Genişletilmiş\n\nmetin", vm.uiState.value.editorContent)
        assertTrue(vm.uiState.value.canUndo, "AI değişikliği geri alınabilir olmalı")
        assertTrue(repo.saved.size > savesBefore, "AI sonucu depoya yazılmalı")
        assertEquals(null, vm.uiState.value.streamingNoteId)

        vm.undoSelected()
        runCurrent()
        assertEquals("kısa", vm.uiState.value.editorContent)
    }

    @Test
    fun aiHatasiUndoYiginiKirletmez() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo, FakeAiService(failWith = IllegalStateException("kota doldu")))
        repo.emitNotes(listOf(note("a", content = "kısa")))
        advanceUntilIdle()
        vm.selectNote("a")

        vm.expandSelected()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canUndo, "Başarısız AI çağrısı undo yığınına yazmamalı")
        assertEquals("kısa", vm.uiState.value.editorContent)
        assertTrue(vm.uiState.value.errorMessage?.contains("kota doldu") == true)
        assertEquals(null, vm.uiState.value.streamingNoteId)
    }

    @Test
    fun aiYazarkenKullaniciDuzenlemesiYoksayilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo, FakeAiService(result = "bir iki üç dört beş"))
        repo.emitNotes(listOf(note("a", content = "kısa")))
        advanceUntilIdle()
        vm.selectNote("a")

        vm.expandSelected()
        runCurrent() // akış başladı, henüz bitmedi
        assertEquals("a", vm.uiState.value.streamingNoteId)

        vm.updateContent("araya sıkışan düzenleme")
        runCurrent()

        assertFalse(vm.uiState.value.editorContent == "araya sıkışan düzenleme")
        advanceUntilIdle()
    }

    // --- Veri katmanından akan durum ---

    @Test
    fun senkronDurumuVeHatalarUiStateyeAkar() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        repo.emitSync(SyncState(connection = ConnectionState.Reconnecting, pendingCount = 3))
        runCurrent()
        assertEquals(ConnectionState.Reconnecting, vm.uiState.value.sync.connection)
        assertEquals(3, vm.uiState.value.sync.pendingCount)

        repo.emitError("Yerel kayıt hatası: disk dolu")
        runCurrent()
        assertEquals("Yerel kayıt hatası: disk dolu", vm.uiState.value.errorMessage)

        vm.dismissError()
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun outboxFlushIstegiDepoyaIletilir() = runTest(dispatcher) {
        val repo = FakeNotesRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.requestOutboxFlush()
        advanceUntilIdle()

        assertEquals(1, repo.flushCount)
    }
}
