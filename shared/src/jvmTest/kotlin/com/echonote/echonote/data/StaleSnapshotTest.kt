package com.echonote.echonote.data

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Uzak anlık görüntü, sorgunun koştuğu andaki sunucu durumunu taşır. O andan sonra push
 * edilen bir not görüntüde yoktur ve `ClearDirty` satırı temiz yapar yapmaz `applyRemote`
 * onu "başka cihazda silinmiş" sayıp kalıcı siler.
 *
 * **Sahada yaşandı (2026-10-02):** masaüstünde oluşturulan not Supabase'e ve telefona
 * ulaştı, masaüstünden kayboldu.
 *
 * Buradaki testler hem korumayı hem de korumanın **silmeyi büsbütün bozmadığını**
 * doğruluyor: koruma ya teyitle ya da zaman aşımıyla düşmeli.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class StaleSnapshotTest {

    /** Sanal saat: `CONFIRM_WINDOW` testten sürülebilsin. */
    private fun TestScope.repository(driver: SqlDriver, remote: FakeRemoteNotesSource) =
        OfflineFirstNotesRepository(
            local = storeFor(driver),
            remote = remote,
            scope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
            isAuthenticated = MutableStateFlow(true),
            now = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
        )

    private suspend fun OfflineFirstNotesRepository.ids() = observeNotes().first().map { it.id }

    /** Sunucuda zaten duran, bu testlerin konusu olmayan not. */
    private val sunucudaki = testNote("sunucudaki")

    /**
     * Açılış senaryosu birebir: diskte bekleyen (kirli) bir not var, push ediliyor,
     * ardından push'tan önce üretilmiş bayat bir anlık görüntü geliyor.
     */
    @Test
    fun bayatAnlikGoruntuPushEdilenNotuSILMEZ() = runTest {
        val remote = FakeRemoteNotesSource(initial = listOf(sunucudaki))
        val driver = inMemoryDriver()
        storeFor(driver).upsertLocal(testNote("yeni"))

        val repo = repository(driver, remote)
        advanceTimeBy(5_000)
        assertTrue(remote.upserted.any { it.id == "yeni" }, "Not önce sunucuya gitmeli")

        // Bayat: sorgu push'tan önce koştu, "yeni" içinde yok.
        remote.emitRemote(listOf(sunucudaki, testNote("baska")))
        advanceTimeBy(2_000)

        assertTrue("yeni" in repo.ids(), "Push edilmiş not bayat görüntü yüzünden silinmemeli")
    }

    /**
     * Koruma yapışkan olmamalı: sunucu notu bir kez teyit ettikten sonra, sonraki
     * görüntüde yoksa bu gerçek bir uzak silmedir ve uygulanmalı.
     */
    @Test
    fun teyitSonrasiGercekUzakSilmeUygulanir() = runTest {
        val remote = FakeRemoteNotesSource(initial = listOf(sunucudaki))
        val driver = inMemoryDriver()
        storeFor(driver).upsertLocal(testNote("yeni"))

        val repo = repository(driver, remote)
        advanceTimeBy(5_000)

        // Sunucu notu teyit ediyor → koruma düşer.
        remote.emitRemote(listOf(sunucudaki, testNote("yeni")))
        advanceTimeBy(2_000)
        assertTrue("yeni" in repo.ids())

        // Artık yok: diğer cihazda kalıcı silinmiş.
        remote.emitRemote(listOf(sunucudaki))
        advanceTimeBy(2_000)

        assertFalse("yeni" in repo.ids(), "Teyit sonrası uzak silme uygulanmalı")
    }

    /**
     * Teyit hiç gelmezse koruma sonsuza kadar sürmemeli; yoksa push'tan hemen sonra
     * uzaktan silinen bir not bu cihazdan asla düşmez.
     */
    @Test
    fun korumaPenceresiDoluncaSilmeIsler() = runTest {
        val remote = FakeRemoteNotesSource(initial = listOf(sunucudaki))
        val driver = inMemoryDriver()
        storeFor(driver).upsertLocal(testNote("yeni"))

        val repo = repository(driver, remote)
        advanceTimeBy(5_000)

        remote.emitRemote(listOf(sunucudaki, testNote("baska")))
        advanceTimeBy(2_000)
        assertTrue("yeni" in repo.ids(), "Pencere içinde korunmalı")

        advanceTimeBy(61_000)
        remote.emitRemote(listOf(sunucudaki))
        advanceTimeBy(2_000)

        assertFalse("yeni" in repo.ids(), "Pencere dolunca normal silme işlemeli")
    }

    /** Koruma yalnızca *bizim push ettiğimiz* satırlar için; silme yolu bozulmamalı. */
    @Test
    fun uzaktanGelipUzaktanSilinenNotSilinir() = runTest {
        val remote = FakeRemoteNotesSource(initial = listOf(sunucudaki, testNote("uzak")))
        val driver = inMemoryDriver()

        val repo = repository(driver, remote)
        advanceTimeBy(2_000)
        assertTrue("uzak" in repo.ids(), "Önce uzaktan gelmeli")

        remote.emitRemote(listOf(sunucudaki))
        advanceTimeBy(2_000)

        assertFalse("uzak" in repo.ids(), "Hiç push etmediğimiz satır normal silinmeli")
    }
}
