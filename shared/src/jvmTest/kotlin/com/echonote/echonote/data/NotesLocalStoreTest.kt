package com.echonote.echonote.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesLocalStoreTest {

    @Test
    fun yerelYazmaKirliIsaretlenirVeGorunurListeyeGirer() = runTest {
        val store = storeFor(inMemoryDriver())

        store.upsertLocal(testNote("a"))

        assertContentEquals(listOf("a"), store.observeVisible().first().map { it.id })
        assertEquals(1, store.observePendingPushCount().first())
        assertTrue(store.pendingPush().single().let { !it.isDelete && it.note.id == "a" })
    }

    @Test
    fun silmeTombstoneOlurGorunurListedenDuserAmaSatirKalir() = runTest {
        val store = storeFor(inMemoryDriver())
        store.upsertLocal(testNote("a"))
        store.clearDirtyIfUnchanged("a", "2026-01-01T00:00:00Z")

        store.markPendingDelete("a")

        // UI'da görünmez…
        assertTrue(store.observeVisible().first().isEmpty())
        // …ama uzak silme onaylanana kadar satır durur, yoksa bayat yayın geri diriltir.
        assertEquals("a", store.pendingPush().single().note.id)
        assertTrue(store.pendingPush().single().isDelete)

        store.deleteHard("a")
        assertNull(store.findById("a"))
    }

    @Test
    fun clearDirtyDegismisSatiriTemizlemez() = runTest {
        val store = storeFor(inMemoryDriver())
        store.upsertLocal(testNote("a", content = "ilk", updatedAt = "2026-01-01T00:00:00Z"))

        // Uzağa "ilk" gönderilirken kullanıcı yazmaya devam etti: damga değişti.
        store.upsertLocal(testNote("a", content = "ilk + eklenen", updatedAt = "2026-01-01T00:00:05Z"))
        store.clearDirtyIfUnchanged("a", sentUpdatedAt = "2026-01-01T00:00:00Z")

        assertEquals(
            1,
            store.observePendingPushCount().first(),
            "Gönderilenden sonra yazılan hâli kirli kalmalı, yoksa yeni harfler hiç gitmez",
        )

        // Güncel damgayla temizleme geçer.
        store.clearDirtyIfUnchanged("a", sentUpdatedAt = "2026-01-01T00:00:05Z")
        assertEquals(0, store.observePendingPushCount().first())
    }

    @Test
    fun uzakSurumYerelKirliSatiriSadeceDahaYeniyseEzer() = runTest {
        val store = storeFor(inMemoryDriver())
        store.upsertLocal(testNote("a", content = "yerel", updatedAt = "2026-01-02T00:00:00Z"))

        // Daha eski uzak sürüm: yerel kazanır.
        store.upsertFromRemote(testNote("a", content = "eski uzak", updatedAt = "2026-01-01T00:00:00Z"))
        assertEquals("yerel", store.findById("a")?.content)

        // Daha yeni uzak sürüm: last-write-wins, uzak kazanır.
        store.upsertFromRemote(testNote("a", content = "yeni uzak", updatedAt = "2026-01-03T00:00:00Z"))
        assertEquals("yeni uzak", store.findById("a")?.content)
        assertEquals(0, store.observePendingPushCount().first())
    }

    @Test
    fun cozumlenemeyenDamgadaYerelKorunur() = runTest {
        val store = storeFor(inMemoryDriver())
        store.upsertLocal(testNote("a", content = "yerel", updatedAt = "2026-01-02T00:00:00Z"))

        store.upsertFromRemote(testNote("a", content = "bozuk uzak", updatedAt = "bozuk-damga"))

        assertEquals("yerel", store.findById("a")?.content)
    }

    @Test
    fun uzakSurumSilmeNiyetiniEzmez() = runTest {
        val store = storeFor(inMemoryDriver())
        store.upsertLocal(testNote("a"))
        store.markPendingDelete("a")

        store.upsertFromRemote(testNote("a", content = "uzakta hâlâ var", updatedAt = "2026-09-09T00:00:00Z"))

        assertTrue(store.observeVisible().first().isEmpty(), "Silme niyeti korunmalı")
        assertTrue(store.pendingPush().single().isDelete)
    }

    @Test
    fun clearAllTumYerelVeriyiSilerCikisIcin() = runTest {
        val store = storeFor(inMemoryDriver())
        store.upsertLocal(testNote("a"))
        store.upsertFromRemote(testNote("b"))
        store.markPendingDelete("b")

        store.clearAll()

        assertTrue(store.observeVisible().first().isEmpty())
        assertEquals(0, store.observePendingPushCount().first(), "Tombstone'lar da gitmeli")
        assertNull(store.findById("a"))
    }

    @Test
    fun uzaktaOlmayanSenkronSatirlarSilinirBekleyenlerKorunur() = runTest {
        val store = storeFor(inMemoryDriver())
        // Senkron olmuş satır
        store.upsertFromRemote(testNote("senkron"))
        // Henüz push edilmemiş yerel satır
        store.upsertLocal(testNote("yeni"))

        store.deleteCleanNotIn(remoteIds = emptySet())

        assertNull(store.findById("senkron"), "Uzakta yok + temiz → başka cihazda silinmiş")
        assertEquals("yeni", store.findById("yeni")?.id, "Push edilmemiş not silinmemeli")
    }
}
