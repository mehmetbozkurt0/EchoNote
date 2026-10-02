package com.echonote.echonote.data

import com.echonote.echonote.data.local.NotesLocalStore
import com.echonote.echonote.model.Note
import com.echonote.echonote.model.nowIsoUtc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Offline-first senkron motoru. **Yerel depo tek gerçek kaynaktır**, Supabase senkron
 * katmanıdır: [observeNotes] ağı hiç beklemez, yazmalar anında diske iner ve bekleyen
 * senkron işleri cihaz yeniden başlasa bile DB'de durur.
 *
 * Tüm DB mutasyonları tek tüketicili [mutations] kanalından geçer — kullanıcı yazmaları,
 * uzaktan gelen merge ve push sonrası temizleme aynı sırada işlenir. Böylece iki yazar
 * arasındaki oku-sonra-yaz yarışları yapısal olarak imkânsız hale gelir.
 *
 * [remote] null ise çevrimdışı mod: yerel depo tam çalışır, senkron döngüleri kurulmaz.
 */
class OfflineFirstNotesRepository(
    private val local: NotesLocalStore,
    private val remote: RemoteNotesSource?,
    private val scope: CoroutineScope,
    /**
     * Senkron yalnızca oturum açıkken çalışır.
     *
     * Bu kapı olmadan: RLS açıkken kimliği olmayan bir sorgu boş liste döner, [applyRemote]
     * bunu "her şey uzaktan silinmiş" diye yorumlar ve YEREL VERİYİ SİLER. Giriş ekranı
     * görünürken senkronun koşması, kullanıcının yerel kopyasını yok etmeye yeter.
     */
    private val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(true),
    /** Testlerin zaman penceresini kontrol edebilmesi için enjekte edilebilir. */
    private val now: () -> Instant = { @OptIn(ExperimentalTime::class) Clock.System.now() },
) : NotesRepository {

    private val connection = MutableStateFlow(
        if (remote == null) ConnectionState.OfflineMode else ConnectionState.Connecting
    )
    private val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Tek yazar kuyruğu. UNLIMITED: [saveNote] asla bloke olmaz ve hiçbir yazma düşmez. */
    private val mutations = Channel<Mutation>(Channel.UNLIMITED)

    /** "Outbox'a bak" sinyali; CONFLATED çünkü birikmesi anlamsız. */
    private val pushSignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * Sunucuya yazılmış ama henüz bir uzak anlık görüntüde **teyit edilmemiş** satırlar:
     * id → push anı.
     *
     * Neden var: uzak anlık görüntü, sorgunun koştuğu andaki sunucu durumunu taşır. O
     * andan sonra push edilen bir not görüntüde yoktur; [ClearDirty] satırı temiz yaptığı
     * an [applyRemote] onu "başka cihazda silinmiş" sayıp KALICI SİLER. Gerçekte görülen
     * bir veri kaybıydı: açılışta bekleyen not sunucuya ve diğer cihaza ulaştı, bu
     * cihazdan kayboldu.
     *
     * Yalnızca tek yazar coroutine'inden (processMutations) okunup yazılır; eşzamanlılık
     * koruması gerekmez.
     */
    private val pushedAwaitingConfirm = mutableMapOf<String, Instant>()

    init {
        scope.launch { processMutations() }
        // Açılışta bir kez: çöpte çok eskimiş notları kalıcı sil.
        mutations.trySend(Mutation.PurgeOldTrash)
        if (remote != null) {
            scope.launch { pullLoop(remote) }
            scope.launch { pushLoop(remote) }
        }
    }

    override fun observeNotes(): Flow<List<Note>> = local.observeVisible()

    override fun observeTrash(): Flow<List<Note>> = local.observeTrashed()

    override fun observeSyncState(): Flow<SyncState> =
        combine(connection, local.observePendingPushCount()) { conn, pending ->
            SyncState(connection = conn, pendingCount = pending)
        }

    override fun observeErrors(): Flow<String> = errors.asSharedFlow()

    override fun saveNote(note: Note) {
        mutations.trySend(Mutation.Save(note))
    }

    override fun deleteNote(id: String) {
        mutations.trySend(Mutation.Trash(id))
    }

    override fun restoreNote(id: String) {
        mutations.trySend(Mutation.Restore(id))
    }

    override fun setPinned(id: String, pinned: Boolean) {
        mutations.trySend(Mutation.Pin(id, pinned))
    }

    override fun deleteForever(id: String) {
        mutations.trySend(Mutation.Delete(id))
    }

    override suspend fun flushOutbox() {
        val source = remote ?: return
        drainOutbox(source)
    }

    override suspend fun clearLocalData() {
        local.clearAll()
    }

    // --- Tek yazar ---

    private suspend fun processMutations() {
        for (mutation in mutations) {
            try {
                when (mutation) {
                    is Mutation.Save -> local.upsertLocal(mutation.note)
                    is Mutation.Delete -> local.markPendingDelete(mutation.id)
                    is Mutation.Trash -> {
                        val now = nowIsoUtc()
                        local.moveToTrash(id = mutation.id, deletedAt = now, updatedAt = now)
                    }
                    is Mutation.Restore -> local.restoreFromTrash(mutation.id, nowIsoUtc())
                    is Mutation.Pin -> local.setPinned(mutation.id, mutation.pinned, nowIsoUtc())
                    is Mutation.PurgeOldTrash -> purgeOldTrash()
                    is Mutation.ApplyRemote -> applyRemote(mutation.notes)
                    is Mutation.ClearDirty -> {
                        local.clearDirtyIfUnchanged(mutation.id, mutation.sentUpdatedAt)
                        // Bu satır artık temiz; bayat bir anlık görüntü onu silmesin.
                        pushedAwaitingConfirm[mutation.id] = now()
                    }
                    is Mutation.DeleteHard -> local.deleteHard(mutation.id)
                }
                pushSignal.trySend(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Yerel yazma hatası gerçek bir sorundur (disk, bozuk DB) — bildir.
                errors.tryEmit("Yerel kayıt hatası: ${e.message}")
            }
        }
    }

    /**
     * Çöpte [TRASH_RETENTION_DAYS] günden fazla duran notları kalıcı siler. Kalıcı silme
     * normal yoldan gider (tombstone → uzak DELETE), yani diğer cihazdan da düşer.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun purgeOldTrash() {
        val cutoff = Clock.System.now().minus(TRASH_RETENTION_DAYS.days).toString()
        local.trashedBefore(cutoff).forEach { id -> local.markPendingDelete(id) }
    }

    private suspend fun applyRemote(remoteNotes: List<Note>) {
        remoteNotes.forEach { local.upsertFromRemote(it) }
        val remoteIds = remoteNotes.mapTo(mutableSetOf()) { it.id }
        // Uzakta olmayan senkron satırlar başka cihazda silinmiş demektir — ama anlık
        // görüntü bayat olabilir, az önce push ettiklerimizi silmekten koru.
        local.deleteCleanNotIn(remoteIds + protectedFromDeletion(remoteIds))
    }

    /**
     * Silinmekten korunacak id'ler ve haritanın bakımı.
     *
     * Bir id iki yoldan düşer:
     * - **teyit:** anlık görüntüde görünüyor, tur tamamlandı;
     * - **zaman aşımı:** [CONFIRM_WINDOW] doldu.
     *
     * Zaman aşımı şart: push'tan hemen sonra diğer cihaz notu kalıcı silerse, not bir
     * daha hiçbir anlık görüntüde görünmez ve koruma kalkmazsa bu cihazdan asla
     * silinmez. Pencere dolunca normal silme işler, yani durum kendi kendini toparlar.
     */
    private fun protectedFromDeletion(remoteIds: Set<String>): Set<String> {
        val cutoff = now() - CONFIRM_WINDOW
        pushedAwaitingConfirm.entries.removeAll { (id, pushedAt) ->
            id in remoteIds || pushedAt < cutoff
        }
        return pushedAwaitingConfirm.keys.toSet()
    }

    // --- Pull ---

    /**
     * Uzak akışı kalıcı olarak dinler; hata akışı sonlandırmaz. Geçici bağlantı
     * sorunları hata bannerı olarak gösterilmez — durum göstergesi bunu zaten
     * anlatıyor ve her yeniden denemede banner açmak gürültü olurdu.
     */
    private suspend fun pullLoop(source: RemoteNotesSource) {
        // collectLatest: oturum kapanınca içerideki abonelik iptal edilir, böylece
        // kimliksiz bir yayın yerel veriyi silemez.
        isAuthenticated.collectLatest { authed ->
            if (!authed) {
                connection.value = ConnectionState.Connecting
                return@collectLatest
            }
            subscribeToRemote(source)
        }
    }

    private suspend fun subscribeToRemote(source: RemoteNotesSource) {
        source.observeNotes()
            .onEach {
                connection.value = ConnectionState.Live
                // Uzaktan veri gelebiliyorsa ağ ayakta demektir: push döngüsünü dürt.
                // Aksi halde bekleyen değişiklik, backoff'u dolana kadar (30 sn'ye
                // varabilir) gönderilmeden bekliyordu.
                pushSignal.trySend(Unit)
            }
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                connection.value = ConnectionState.Reconnecting
                delay(reconnectBackoffMs(attempt))
                true
            }
            .collect { notes -> mutations.send(Mutation.ApplyRemote(notes)) }
    }

    // --- Push ---

    private suspend fun pushLoop(source: RemoteNotesSource) {
        var failures = 0L
        // Açılışta bir kez dene: önceki oturumdan kalan outbox varsa hemen gitsin.
        pushSignal.trySend(Unit)
        for (signal in pushSignal) {
            delay(PUSH_DEBOUNCE_MS)
            failures = if (drainOutbox(source)) {
                0L
            } else {
                // Başarısız: bayraklar DB'de duruyor. Backoff beklenir ama bekleme
                // KESİLEBİLİR: araya bir sinyal girerse (ör. bağlantının geri gelmesi)
                // hemen yeniden denenir, kullanıcı backoff'un dolmasını beklemez.
                withTimeoutOrNull(reconnectBackoffMs(failures)) { pushSignal.receive() }
                pushSignal.trySend(Unit)
                failures + 1
            }
        }
    }

    /** Outbox'ı boşaltmayı dener; hepsi başarılıysa true. */
    private suspend fun drainOutbox(source: RemoteNotesSource): Boolean {
        // Oturum yokken göndermenin anlamı yok: RLS reddeder ve bayraklar boşuna
        // temizlenme riskine girer. Bekleyen iş DB'de durur, giriş sonrası gider.
        if (!isAuthenticated.value) return false
        val pending = try {
            local.pendingPush()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.tryEmit("Outbox okunamadı: ${e.message}")
            return false
        }
        var allSucceeded = true
        for (entry in pending) {
            try {
                if (entry.isDelete) {
                    source.deleteNote(entry.note.id)
                    mutations.send(Mutation.DeleteHard(entry.note.id))
                } else {
                    source.upsertNote(entry.note)
                    // Sürüm korumalı: uzağa yazarken kullanıcı yazmaya devam ettiyse
                    // satır kirli kalır ve yeni hâli sonraki push'ta gider.
                    mutations.send(Mutation.ClearDirty(entry.note.id, entry.note.updatedAt))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    /** 1s, 2s, 4s, 8s, 16s → 30s tavanı. */
    private fun reconnectBackoffMs(attempt: Long): Long =
        (INITIAL_BACKOFF_MS shl attempt.coerceAtMost(5).toInt()).coerceAtMost(MAX_BACKOFF_MS)

    private sealed interface Mutation {
        data class Save(val note: Note) : Mutation
        data class Delete(val id: String) : Mutation
        data class Trash(val id: String) : Mutation
        data class Restore(val id: String) : Mutation
        data class Pin(val id: String, val pinned: Boolean) : Mutation
        data object PurgeOldTrash : Mutation
        data class ApplyRemote(val notes: List<Note>) : Mutation
        data class ClearDirty(val id: String, val sentUpdatedAt: String) : Mutation
        data class DeleteHard(val id: String) : Mutation
    }

    private companion object {
        const val PUSH_DEBOUNCE_MS = 600L
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val TRASH_RETENTION_DAYS = 30

        /** Push edilen satırın uzak anlık görüntüde teyit edilmesi için tanınan süre. */
        val CONFIRM_WINDOW = 60.seconds
    }
}
