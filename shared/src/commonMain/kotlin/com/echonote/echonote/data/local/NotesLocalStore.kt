package com.echonote.echonote.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import com.echonote.echonote.db.EchoDatabase
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.parseTimestampOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime
import com.echonote.echonote.db.Note as DbNote

/**
 * Yerel SQLite deposu — uygulamanın tek gerçek kaynağı.
 *
 * Senkron defteri (`dirty`, `pending_delete`) burada kalıcıdır; Adım 1'de bu bilgi
 * ViewModel belleğindeydi ve süreç ölünce kayboluyordu.
 *
 * Silme işlemi satırı hemen yok etmez: `pending_delete = 1` ile **tombstone**'a
 * dönüştürür. Tombstone UI'da görünmez ama uzak silme onaylanana kadar durur, yoksa
 * bayat bir uzak yayın notu geri diriltir.
 */
class NotesLocalStore(
    driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher,
) {
    private val queries = EchoDatabase(driver).noteQueries

    /** UI'ın gördüğü liste: tombstone'lar hariç, updated_at'e göre yeniden eskiye. */
    fun observeVisible(): Flow<List<Note>> =
        queries.selectVisible().asFlow().mapToList(dispatcher).map { rows -> rows.map(DbNote::toDomain) }

    /**
     * Çöp kutusu listesi.
     *
     * Kendi mapper'ı var: `deleted_at IS NOT NULL` koşulu yüzünden SQLDelight bu sorgu
     * için ayrı bir satır tipi üretiyor (orada kolon non-null çıkarımı yapıyor). Domain
     * tipine doğrudan çevirerek o ara tipi hiç kullanmıyoruz.
     */
    fun observeTrashed(): Flow<List<Note>> =
        queries.selectTrashed { id, title, content, updatedAt, deviceId, _, _, deletedAt ->
            Note(
                id = id,
                title = title,
                content = content,
                updatedAt = updatedAt,
                deviceId = deviceId,
                deletedAt = deletedAt,
            )
        }.asFlow().mapToList(dispatcher)

    fun observeTrashedCount(): Flow<Long> =
        queries.countTrashed().asFlow().mapToOneOrNull(dispatcher).map { it ?: 0L }

    /** Outbox'ta bekleyen iş sayısı; UI'daki senkron göstergesini besler. */
    fun observePendingPushCount(): Flow<Long> =
        queries.countPendingPush().asFlow().mapToOneOrNull(dispatcher).map { it ?: 0L }

    suspend fun pendingPush(): List<PendingNote> = withContext(dispatcher) {
        queries.selectPendingPush().executeAsList().map { row ->
            PendingNote(note = row.toDomain(), isDelete = row.pending_delete)
        }
    }

    suspend fun findById(id: String): Note? = withContext(dispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    /**
     * Uzak listede artık bulunmayan senkron satırları siler — bunlar başka bir cihazda
     * silinmiş demektir. Bekleyen iş taşıyan satırlara dokunulmaz: henüz push edilmemiş
     * yeni bir not uzakta zaten yoktur, silinmiş sayılamaz.
     */
    suspend fun deleteCleanNotIn(remoteIds: Set<String>): Unit = withContext(dispatcher) {
        queries.transaction {
            queries.selectCleanIds().executeAsList()
                .filterNot { it in remoteIds }
                .forEach { queries.deleteHard(it) }
        }
    }

    /**
     * Kullanıcı düzenlemesi: satırı yazar ve kirli işaretler. Çağrıldığı anda diske
     * iner — dayanıklılık buradan gelir, ağdan değil.
     */
    suspend fun upsertLocal(note: Note): Unit = withContext(dispatcher) {
        queries.transaction {
            if (queries.selectById(note.id).executeAsOneOrNull() == null) {
                queries.insertNote(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    updated_at = note.updatedAt,
                    device_id = note.deviceId,
                    dirty = true,
                    pending_delete = false,
                    deleted_at = note.deletedAt,
                )
            } else {
                queries.updateNoteRow(
                    title = note.title,
                    content = note.content,
                    updated_at = note.updatedAt,
                    device_id = note.deviceId,
                    dirty = true,
                    deleted_at = note.deletedAt,
                    id = note.id,
                )
            }
        }
    }

    /**
     * Uzaktan gelen satır. Çakışma çözümü last-write-wins ve kararın tamamı tek
     * transaction içinde verilir (oku-sonra-yaz yarışı olmasın):
     *
     * - satır yoksa eklenir,
     * - silinmeyi bekliyorsa dokunulmaz (yerel silme niyeti korunur),
     * - yerel temizse uzak sürüm kazanır,
     * - yerel kirliyse yalnızca uzak sürüm daha yeniyse kazanır; damga
     *   çözümlenemiyorsa yerel korunur.
     */
    suspend fun upsertFromRemote(note: Note): Unit = withContext(dispatcher) {
        queries.transaction {
            val existing = queries.selectById(note.id).executeAsOneOrNull()
            val remoteWins = when {
                existing == null -> null // ekleme yolu
                existing.pending_delete -> false
                !existing.dirty -> true
                else -> isStrictlyNewer(note.updatedAt, existing.updated_at)
            }
            when (remoteWins) {
                null -> queries.insertNote(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    updated_at = note.updatedAt,
                    device_id = note.deviceId,
                    dirty = false,
                    pending_delete = false,
                    deleted_at = note.deletedAt,
                )
                true -> queries.updateNoteRow(
                    title = note.title,
                    content = note.content,
                    updated_at = note.updatedAt,
                    device_id = note.deviceId,
                    dirty = false,
                    deleted_at = note.deletedAt,
                    id = note.id,
                )
                false -> Unit // yerel kazandı
            }
        }
    }

    /**
     * Çöp kutusuna taşır. Satır silinmez, `deleted_at` damgalanır ve kirli işaretlenir —
     * normal bir alan gibi senkronlanır, yani diğer cihazda da çöpte görünür.
     */
    suspend fun moveToTrash(id: String, deletedAt: String, updatedAt: String): Unit =
        withContext(dispatcher) {
            queries.moveToTrash(deleted_at = deletedAt, updated_at = updatedAt, id = id)
        }

    suspend fun restoreFromTrash(id: String, updatedAt: String): Unit = withContext(dispatcher) {
        queries.restoreFromTrash(updated_at = updatedAt, id = id)
    }

    /** Çöpte [before] tarihinden eski notların kimlikleri (otomatik temizlik için). */
    suspend fun trashedBefore(before: String): List<String> = withContext(dispatcher) {
        queries.selectTrashedBefore(before).executeAsList()
    }

    /** Silmeyi tombstone olarak kaydeder; uzak onay gelince [deleteHard] çağrılır. */
    suspend fun markPendingDelete(id: String): Unit = withContext(dispatcher) {
        queries.markPendingDelete(id)
    }

    suspend fun deleteHard(id: String): Unit = withContext(dispatcher) {
        queries.deleteHard(id)
    }

    /**
     * Tüm yerel veriyi siler. Çıkış yapılırken çağrılır: aksi halde bir sonraki hesap,
     * öncekinin notlarını yerelde görürdü — RLS'i uygulama içinden delen bir boşluk.
     */
    suspend fun clearAll(): Unit = withContext(dispatcher) {
        queries.deleteAll()
    }

    /**
     * Uzağa yazma başarılı olduktan sonra kirli bayrağını kaldırır — ama yalnızca
     * satır o sırada değişmediyse. Kullanıcı ağ beklerken yazmaya devam ettiyse
     * `updated_at` farklı olur ve satır kirli kalır, yeni harfler sonraki push'ta gider.
     */
    suspend fun clearDirtyIfUnchanged(id: String, sentUpdatedAt: String): Unit =
        withContext(dispatcher) {
            queries.clearDirtyIfUnchanged(id = id, updated_at = sentUpdatedAt)
        }
}

/** Outbox girdisi: ya uzağa yazılacak bir not ya da uzaktan silinecek bir tombstone. */
data class PendingNote(val note: Note, val isDelete: Boolean)

/**
 * [candidate] gerçekten [reference]'tan yeni mi. Damgalardan biri çözümlenemiyorsa
 * false döner — bu da "yereli koru" anlamına gelir, veri kaybetmemeyi tercih ederiz.
 */
@OptIn(ExperimentalTime::class)
private fun isStrictlyNewer(candidate: String, reference: String): Boolean {
    val candidateTime = parseTimestampOrNull(candidate) ?: return false
    val referenceTime = parseTimestampOrNull(reference) ?: return false
    return candidateTime > referenceTime
}

private fun DbNote.toDomain() = Note(
    id = id,
    title = title,
    content = content,
    updatedAt = updated_at,
    deviceId = device_id,
    deletedAt = deleted_at,
)
