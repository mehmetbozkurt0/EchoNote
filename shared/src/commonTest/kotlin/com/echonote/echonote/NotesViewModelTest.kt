package com.echonote.echonote

import com.echonote.echonote.model.Note
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun note(
        id: String,
        content: String = "içerik-$id",
        updatedAt: String = "2026-01-01T00:00:00Z",
    ) = Note(id = id, title = "Not $id", content = content, updatedAt = updatedAt, deviceId = "test")

    private fun viewModel(repository: FakeNotesRepository) =
        NotesViewModel(repository = repository, aiService = FakeAiService())

    // --- 1 & 2: Realtime akışı hatada ölmez, bağlantı durumu doğru raporlanır ---

    @Test
    fun akisHataVerinceYenidenAboneOlurVeSonrakiListeUiyaUlasir() = runTest(dispatcher) {
        val repo = FakeNotesRepository(listOf(note("a")))
        repo.failuresRemaining = 1 // ilk abonelik patlasın

        val vm = viewModel(repo)
        runCurrent()

        // İlk abonelik hata ile bitti; eski davranışta akış burada kalıcı olarak ölüyordu.
        assertEquals(1, repo.subscriptionCount)
        assertEquals(ConnectionState.Reconnecting, vm.uiState.value.connection)
        assertTrue(vm.uiState.value.notes.isEmpty())

        advanceTimeBy(1_100) // ilk backoff 1 sn
        runCurrent()

        assertEquals(2, repo.subscriptionCount)
        assertEquals(ConnectionState.Live, vm.uiState.value.connection)
        assertContentEquals(listOf("a"), vm.uiState.value.notes.map { it.id })

        // Yeniden abone olunan akış canlı: sonraki uzak yayın da UI'a düşer.
        repo.emitRemote(listOf(note("a"), note("b", updatedAt = "2026-02-01T00:00:00Z")))
        runCurrent()
        assertContentEquals(listOf("b", "a"), vm.uiState.value.notes.map { it.id })
    }

    @Test
    fun cevrimdisiModdaBaglantiDurumuOfflineKalir() = runTest(dispatcher) {
        // InMemoryNotesRepository ile kurulan VM çevrimdışı moddadır; Live'a geçmemeli.
        val vm = NotesViewModel(
            repository = com.echonote.echonote.data.InMemoryNotesRepository(),
            aiService = FakeAiService(),
        )
        advanceUntilIdle()

        assertEquals(ConnectionState.OfflineMode, vm.uiState.value.connection)
        assertTrue(vm.uiState.value.notes.isNotEmpty()) // örnek notlar yüklendi
    }

    // --- 3: Kapanışta flush debounce'u atlar ---

    @Test
    fun flushPendingSavesDebounceBeklemedenYazar() = runTest(dispatcher) {
        val repo = FakeNotesRepository(listOf(note("a")))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.updateContent("a", "kapanmadan hemen önce yazılan")
        assertTrue(vm.uiState.value.hasUnsavedChanges)

        // Debounce (600 ms) dolmadan boşalt — uygulama kapanıyor senaryosu.
        vm.flushPendingSaves()

        assertEquals(1, repo.updated.size)
        assertEquals("kapanmadan hemen önce yazılan", repo.updated.single().content)
        assertFalse(vm.uiState.value.hasUnsavedChanges)

        // İptal edilen debounce işi sonradan ikinci bir yazma tetiklemesin.
        advanceUntilIdle()
        assertEquals(1, repo.updated.size)
    }

    // --- 4: Yazma ağda askıdayken yazmaya devam edilirse not kirli kalır ---

    @Test
    fun yazmaAskidaykenEklenenHarflerUzakYankiylaKaybolmaz() = runTest(dispatcher) {
        val original = note("a", content = "ilk")
        val repo = FakeNotesRepository(listOf(original))
        val vm = viewModel(repo)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repo.updateGate = gate

        vm.updateContent("a", "ilk hali")
        val flush = launch { vm.flushPendingSaves() }
        runCurrent()
        assertEquals(1, repo.updateEnteredCount) // yazma kapıda askıda

        // Ağ beklerken kullanıcı yazmaya devam ediyor.
        vm.updateContent("a", "ilk hali + eklenen")
        runCurrent()

        gate.complete(Unit)
        flush.join()

        // Gönderilen anlık görüntü eski içerikti; not hâlâ kirli sayılmalı.
        assertEquals("ilk hali", repo.updated.single().content)
        assertTrue(
            vm.uiState.value.hasUnsavedChanges,
            "Yazma sırasında eklenen harfler kalıcılaşmadı; not temiz işaretlenmemeli",
        )

        // Bayat uzak yankı yeni harfleri geri almamalı.
        repo.emitRemote(listOf(original))
        runCurrent()
        assertEquals("ilk hali + eklenen", vm.uiState.value.notes.single { it.id == "a" }.content)
    }

    // --- 5: İyimser silme uzak yankıyla geri dirilmez ---

    @Test
    fun iyimserSilinenNotBayatUzakYankiylaGeriDirilmez() = runTest(dispatcher) {
        val a = note("a")
        val b = note("b", updatedAt = "2026-02-01T00:00:00Z")
        val repo = FakeNotesRepository(listOf(a, b))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.deleteNote("a")
        advanceUntilIdle()

        assertContentEquals(listOf("a"), repo.deleted)
        assertContentEquals(listOf("b"), vm.uiState.value.notes.map { it.id })

        // Silme henüz yayına düşmemiş: eski liste bir kez daha gelir.
        repo.emitRemote(listOf(a, b))
        runCurrent()
        assertContentEquals(listOf("b"), vm.uiState.value.notes.map { it.id })

        // Yayın silmeyi onayladıktan sonra koruma kalkar ve liste uzaktan sürülür.
        repo.emitRemote(listOf(b))
        runCurrent()
        assertContentEquals(listOf("b"), vm.uiState.value.notes.map { it.id })
    }

    // --- 6: Sıralama ve çözümlenemeyen zaman damgası ---

    @Test
    fun listeUpdatedAtAzalanSiralanirVeBozukDamgaYereliEzmez() = runTest(dispatcher) {
        val repo = FakeNotesRepository(
            listOf(
                note("eski", updatedAt = "2026-01-01T00:00:00Z"),
                note("yeni", updatedAt = "2026-03-01T00:00:00Z"),
                note("orta", updatedAt = "2026-02-01T00:00:00Z"),
            )
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertContentEquals(listOf("yeni", "orta", "eski"), vm.uiState.value.notes.map { it.id })

        // Yerelde kaydedilmemiş bir not varken uzak sürüm çözümlenemeyen damga ile gelirse
        // (Supabase bozuk timestamp döndürürse) yerel yazı korunmalı.
        vm.updateContent("orta", "yerelde yazılmış, henüz kaydedilmemiş")
        runCurrent()
        repo.emitRemote(listOf(note("orta", content = "uzak içerik", updatedAt = "bozuk-damga")))
        runCurrent()

        assertEquals(
            "yerelde yazılmış, henüz kaydedilmemiş",
            vm.uiState.value.notes.single { it.id == "orta" }.content,
        )
    }
}
