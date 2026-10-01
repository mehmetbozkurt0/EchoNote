package com.echonote.echonote.data

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class TrashTest {

    private fun TestScope.repository(driver: SqlDriver, remote: FakeRemoteNotesSource?) =
        OfflineFirstNotesRepository(
            local = storeFor(driver),
            remote = remote,
            scope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
            isAuthenticated = MutableStateFlow(true),
        )

    @Test
    fun silmeNotuYokEtmezCopKutusunaTasir() = runTest {
        val driver = inMemoryDriver()
        val repo = repository(driver, remote = null)
        repo.saveNote(testNote("a"))
        advanceUntilIdle()

        repo.deleteNote("a")
        advanceUntilIdle()

        assertTrue(repo.observeNotes().first().isEmpty(), "Ana listede görünmemeli")
        assertContentEquals(listOf("a"), repo.observeTrash().first().map { it.id })
        assertTrue(
            storeFor(driver).findById("a") != null,
            "Satır yok edilmemeli — çöp kutusu geri alınabilir olmalı",
        )
    }

    @Test
    fun geriYuklemeNotuListeyeDondurur() = runTest {
        val repo = repository(inMemoryDriver(), remote = null)
        repo.saveNote(testNote("a"))
        advanceUntilIdle()
        repo.deleteNote("a")
        advanceUntilIdle()

        repo.restoreNote("a")
        advanceUntilIdle()

        assertContentEquals(listOf("a"), repo.observeNotes().first().map { it.id })
        assertTrue(repo.observeTrash().first().isEmpty())
    }

    @Test
    fun copKutusuUzagaSenkronlanir() = runTest {
        val remote = FakeRemoteNotesSource()
        val repo = repository(inMemoryDriver(), remote)
        repo.saveNote(testNote("a"))
        advanceTimeBy(1_000)
        advanceUntilIdle()
        remote.upserted.clear()

        repo.deleteNote("a")
        advanceTimeBy(1_000)
        advanceUntilIdle()

        // Silme gerçek DELETE değil, deleted_at damgalı bir güncelleme olarak gider.
        assertTrue(remote.deleted.isEmpty(), "Çöpe taşıma uzaktan silme OLMAMALI")
        assertEquals("a", remote.upserted.last().id)
        assertTrue(remote.upserted.last().deletedAt != null, "deleted_at senkronlanmalı")
    }

    @Test
    fun kaliciSilmeUzaktanDaSiler() = runTest {
        val remote = FakeRemoteNotesSource()
        val driver = inMemoryDriver()
        val repo = repository(driver, remote)
        repo.saveNote(testNote("a"))
        advanceTimeBy(1_000)
        advanceUntilIdle()

        repo.deleteForever("a")
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertContentEquals(listOf("a"), remote.deleted)
        assertTrue(storeFor(driver).findById("a") == null, "Onaydan sonra yerelden de gitmeli")
    }

    @Test
    fun uzaktanGelenCopDamgasiYereldeDeCopeDuser() = runTest {
        val remote = FakeRemoteNotesSource(initial = listOf(testNote("a")))
        val repo = repository(inMemoryDriver(), remote)
        advanceUntilIdle()
        assertEquals(1, repo.observeNotes().first().size)

        // Diğer cihaz notu çöpe attı.
        remote.emitRemote(
            listOf(testNote("a", updatedAt = "2026-02-01T00:00:00Z").copy(deletedAt = "2026-02-01T00:00:00Z"))
        )
        advanceUntilIdle()

        assertTrue(repo.observeNotes().first().isEmpty())
        assertContentEquals(listOf("a"), repo.observeTrash().first().map { it.id })
    }

    @Test
    fun copteCokEskiyenNotlarAcilistaKaliciSilinir() = runTest {
        val driver = inMemoryDriver()
        val store = storeFor(driver)
        // 40 gün önce çöpe atılmış bir not + bugün atılmış bir not.
        store.upsertFromRemote(testNote("eski"))
        store.upsertFromRemote(testNote("yeni"))
        store.moveToTrash("eski", deletedAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z")
        store.moveToTrash("yeni", deletedAt = "2099-01-01T00:00:00Z", updatedAt = "2099-01-01T00:00:00Z")

        val remote = FakeRemoteNotesSource()
        repository(driver, remote)
        advanceTimeBy(2_000)
        advanceUntilIdle()

        assertContentEquals(listOf("eski"), remote.deleted, "Yalnızca eskiyen kalıcı silinmeli")
        assertContentEquals(listOf("yeni"), store.observeTrashed().first().map { it.id })
    }
}
