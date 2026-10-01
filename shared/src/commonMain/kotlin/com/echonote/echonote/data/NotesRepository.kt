package com.echonote.echonote.data

import com.echonote.echonote.model.Note
import kotlinx.coroutines.flow.Flow

/** Uzak depoyla kurulan canlı bağlantının durumu. */
enum class ConnectionState {
    /** Secrets boş bırakıldı: senkron yok, yalnızca yerel depo. */
    OfflineMode,
    Connecting,
    Live,
    Reconnecting,
}

/**
 * Senkron durumu. [pendingCount], yerelde kalıcı olup henüz uzağa gönderilmemiş
 * değişiklik sayısı — "kaydedildi" ile "senkronlandı" artık farklı şeyler.
 */
data class SyncState(
    val connection: ConnectionState = ConnectionState.Connecting,
    val pendingCount: Long = 0,
)

/**
 * ViewModel'in gördüğü sözleşme. Yazma işlemleri **suspend değil**: çağrıldıkları anda
 * sıralı bir kuyruğa girer ve yerel depoya yazılır. ViewModel'in her tuş vuruşu için
 * coroutine başlatması gerekmez ve yazma sırası garanti edilir.
 */
interface NotesRepository {

    /** Yerel depodan beslenen liste; ağı hiç beklemez. */
    fun observeNotes(): Flow<List<Note>>

    fun observeSyncState(): Flow<SyncState>

    /** Gerçek hatalar (yerel yazma başarısızlığı gibi); geçici ağ sorunları değil. */
    fun observeErrors(): Flow<String>

    /** Oluşturma ve güncelleme aynı yol: yerel yazma + senkron kuyruğuna alma. */
    fun saveNote(note: Note)

    fun deleteNote(id: String)

    /** Bekleyen senkronu uzağa göndermeyi dener (kapanış kancası). */
    suspend fun flushOutbox()
}

/**
 * Uzak kaynak soyutlaması. Outbox tek bir "upsert" ile çalışır: yerelde oluşturulmuş
 * bir not uzakta var da olabilir yok da, ayrım yapmak gerekmez.
 */
interface RemoteNotesSource {
    fun observeNotes(): Flow<List<Note>>
    suspend fun upsertNote(note: Note)
    suspend fun deleteNote(id: String)
}
