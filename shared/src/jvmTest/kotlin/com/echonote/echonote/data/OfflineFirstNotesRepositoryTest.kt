package com.echonote.echonote.data

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstNotesRepositoryTest {

    /**
     * Repository'nin kendi scope'u.
     *
     * `UnconfinedTestDispatcher`: mutation kuyruğu anında işlenir, böylece testler
     * yazmanın DB'ye inmesi için zaman ilerletmek zorunda kalmaz.
     *
     * `backgroundScope.coroutineContext` korunur çünkü içindeki `BackgroundWork` işareti
     * [advanceUntilIdle]'ın gecikmeli arka plan işlerini atlamasını sağlıyor — aksi halde
     * sonsuz retry döngüleri (ör. writesFail) testi kilitlerdi. Düz `backgroundScope`
     * ise hiç koşmazdı: advanceUntilIdle arka plan işini bilerek yürütmüyor.
     */
    private fun TestScope.repository(
        driver: SqlDriver,
        remote: FakeRemoteNotesSource?,
        isAuthenticated: StateFlow<Boolean> = MutableStateFlow(true),
    ) = OfflineFirstNotesRepository(
        local = storeFor(driver),
        remote = remote,
        scope = CoroutineScope(
            backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
        ),
        isAuthenticated = isAuthenticated,
    )

    /**
     * Sahada yaşandı: giriş ekranı açıkken senkron motoru çalışıyordu. RLS açık olduğu
     * için kimliksiz sorgu boş liste döndü, motor bunu "hepsi uzaktan silinmiş" sanıp
     * yereldeki 22 notu sildi.
     */
    @Test
    fun oturumYokkenGelenBosUzakListeYereliSILMEZ() = runTest {
        val auth = MutableStateFlow(false)
        val remote = FakeRemoteNotesSource(initial = emptyList())
        val driver = inMemoryDriver()
        val store = storeFor(driver)
        // Önceki oturumdan kalan yerel notlar
        store.upsertFromRemote(testNote("a"))
        store.upsertFromRemote(testNote("b"))

        val repo = repository(driver, remote, isAuthenticated = auth)
        advanceTimeBy(5_000)

        assertEquals(
            2,
            repo.observeNotes().first().size,
            "Giriş yapılmadan gelen boş uzak liste yerel veriyi silmemeli",
        )

        // Giriş yapılınca senkron devreye girer ve gerçek uzak durum uygulanır.
        remote.emitRemote(listOf(testNote("a"), testNote("b")))
        auth.value = true
        advanceTimeBy(2_000)

        assertEquals(2, repo.observeNotes().first().size)
    }

    @Test
    fun oturumYokkenOutboxGonderilmez() = runTest {
        val auth = MutableStateFlow(false)
        val remote = FakeRemoteNotesSource()
        val repo = repository(inMemoryDriver(), remote, isAuthenticated = auth)

        repo.saveNote(testNote("a"))
        advanceTimeBy(5_000)

        assertTrue(remote.upserted.isEmpty(), "Oturum yokken uzağa yazma denenmemeli")
        assertEquals(1, repo.observeSyncState().first().pendingCount)

        auth.value = true
        advanceTimeBy(2_000)
        assertContentEquals(listOf("a"), remote.upserted.map { it.id })
    }

    // --- Kalıcılık: offline-first'ün asıl vaadi ---

    @Test
    fun yerelYazmaUygulamaYenidenBaslatilsaDaDurur() = runTest {
        val driver = inMemoryDriver() // aynı sürücü = aynı "disk"
        val first = repository(driver, remote = null)

        first.saveNote(testNote("a", content = "kapanmadan önce yazılan"))
        advanceUntilIdle()

        // Yeni repository örneği: süreç yeniden başlamış gibi.
        val second = repository(driver, remote = null)
        advanceUntilIdle()

        val notes = second.observeNotes().first()
        assertContentEquals(listOf("a"), notes.map { it.id })
        assertEquals("kapanmadan önce yazılan", notes.single().content)
    }

    @Test
    fun uzakKaynakHicErisilemezkenYerelVeriYineYayinlanir() = runTest {
        val remote = FakeRemoteNotesSource().apply { failuresRemaining = Int.MAX_VALUE }
        val repo = repository(inMemoryDriver(), remote)

        repo.saveNote(testNote("a"))
        advanceTimeBy(2_000)

        assertContentEquals(listOf("a"), repo.observeNotes().first().map { it.id })
        assertEquals(
            ConnectionState.Reconnecting,
            repo.observeSyncState().first().connection,
        )
    }

    @Test
    fun cevrimdisiModdaBaglantiDurumuOfflineKalir() = runTest {
        val repo = repository(inMemoryDriver(), remote = null)
        advanceUntilIdle()

        assertEquals(ConnectionState.OfflineMode, repo.observeSyncState().first().connection)
    }

    // --- Push ---

    @Test
    fun bekleyenYazmalarBaglantiGelinceUzagaGider() = runTest {
        val remote = FakeRemoteNotesSource()
        val repo = repository(inMemoryDriver(), remote)

        repo.saveNote(testNote("a", content = "senkronlanacak"))
        advanceTimeBy(1_000) // push debounce'u geç
        advanceUntilIdle()

        assertContentEquals(listOf("a"), remote.upserted.map { it.id })
        assertEquals(0, repo.observeSyncState().first().pendingCount)
    }

    @Test
    fun pushBasarisizOlursaIsOutboxtaKalirVeYenidenDenenir() = runTest {
        val remote = FakeRemoteNotesSource().apply { writesFail = true }
        val repo = repository(inMemoryDriver(), remote)

        repo.saveNote(testNote("a"))
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertTrue(remote.upserted.isEmpty())
        assertEquals(
            1,
            repo.observeSyncState().first().pendingCount,
            "Başarısız push'tan sonra iş outbox'ta kalmalı",
        )

        // Ağ geri geldi: backoff sonrası kendiliğinden gitmeli.
        remote.writesFail = false
        advanceTimeBy(5_000)
        advanceUntilIdle()

        assertContentEquals(listOf("a"), remote.upserted.map { it.id })
        assertEquals(0, repo.observeSyncState().first().pendingCount)
    }

    /**
     * Cihazda yakalandı: ağ geri geldikten sonra bekleyen değişiklik, push döngüsü
     * backoff'unda uyuduğu için 30 sn'ye kadar gönderilmeden bekliyordu.
     */
    @Test
    fun baglantiGeriGelinceBekleyenIsBackoffuBeklemedenGonderilir() = runTest {
        val remote = FakeRemoteNotesSource().apply { writesFail = true }
        val repo = repository(inMemoryDriver(), remote)

        repo.saveNote(testNote("a"))
        advanceTimeBy(20_000) // birkaç başarısız deneme: backoff tavana yaklaşsın
        assertTrue(remote.upserted.isEmpty())

        // Ağ geri geldi ve uzak akış yeniden veri yayınladı. (Başlangıç değerinden
        // farklı bir liste olmalı: StateFlow eşit değeri yeniden yaymaz.)
        remote.writesFail = false
        remote.emitRemote(listOf(testNote("uzaktan-gelen")))
        advanceTimeBy(1_000) // yalnızca push debounce'u kadar

        assertContentEquals(
            listOf("a"),
            remote.upserted.map { it.id },
            "Bağlantı dönünce backoff beklenmeden gönderilmeli",
        )
    }

    @Test
    fun silmeUzagaGiderVeOnaylandiktanSonraYereldenSilinir() = runTest {
        val remote = FakeRemoteNotesSource()
        val driver = inMemoryDriver()
        val repo = repository(driver, remote)

        repo.saveNote(testNote("a"))
        advanceTimeBy(1_000)
        advanceUntilIdle()

        repo.deleteNote("a")
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertContentEquals(listOf("a"), remote.deleted)
        assertTrue(storeFor(driver).findById("a") == null, "Onaydan sonra tombstone kalkmalı")
        assertEquals(0, repo.observeSyncState().first().pendingCount)
    }

    // --- Pull ---

    @Test
    fun uzakYayinYerelKirliSatiriEzmez() = runTest {
        val remote = FakeRemoteNotesSource()
        remote.writesFail = true // push gitmesin, satır kirli kalsın
        val repo = repository(inMemoryDriver(), remote)

        repo.saveNote(testNote("a", content = "yerelde yazılan", updatedAt = "2026-01-02T00:00:00Z"))
        advanceUntilIdle()

        // Bayat uzak sürüm gelir.
        remote.emitRemote(listOf(testNote("a", content = "bayat uzak", updatedAt = "2026-01-01T00:00:00Z")))
        advanceUntilIdle()

        assertEquals("yerelde yazılan", repo.observeNotes().first().single().content)
    }

    @Test
    fun uzakAkisHataVerinceYenidenAboneOlurVeVeriGelir() = runTest {
        val remote = FakeRemoteNotesSource(initial = listOf(testNote("a")))
        remote.failuresRemaining = 1
        val repo = repository(inMemoryDriver(), remote)

        advanceUntilIdle() // ilk abonelik patlar, backoff başlar
        assertEquals(ConnectionState.Reconnecting, repo.observeSyncState().first().connection)

        advanceTimeBy(1_500) // ilk backoff 1 sn
        advanceUntilIdle()

        assertEquals(2, remote.subscriptionCount)
        assertEquals(ConnectionState.Live, repo.observeSyncState().first().connection)
        assertContentEquals(listOf("a"), repo.observeNotes().first().map { it.id })
    }

    @Test
    fun baskaCihazdaSilinenNotYereldenDuser() = runTest {
        val remote = FakeRemoteNotesSource(initial = listOf(testNote("a"), testNote("b")))
        val repo = repository(inMemoryDriver(), remote)
        advanceUntilIdle()
        assertEquals(2, repo.observeNotes().first().size)

        remote.emitRemote(listOf(testNote("b")))
        advanceUntilIdle()

        assertContentEquals(listOf("b"), repo.observeNotes().first().map { it.id })
    }

    @Test
    fun flushOutboxBekleyenIsiHemenGonderir() = runTest {
        val remote = FakeRemoteNotesSource()
        val repo = repository(inMemoryDriver(), remote)

        repo.saveNote(testNote("a"))
        advanceUntilIdle() // mutation işlendi, push debounce'u henüz dolmadı
        remote.upserted.clear()

        repo.flushOutbox()
        advanceUntilIdle()

        assertContentEquals(listOf("a"), remote.upserted.map { it.id })
    }
}
