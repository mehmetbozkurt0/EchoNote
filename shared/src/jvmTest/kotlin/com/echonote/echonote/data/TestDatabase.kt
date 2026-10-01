package com.echonote.echonote.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.echonote.echonote.data.local.NotesLocalStore
import com.echonote.echonote.db.EchoDatabase
import com.echonote.echonote.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Gerçek SQLite, bellek içinde. SQLDelight'ın güzel yanı: veri katmanı testlerinde
 * sahte bir depo taklit etmek yerine asıl motoru koşturuyoruz.
 */
fun inMemoryDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { driver ->
    EchoDatabase.Schema.create(driver).value
}

fun storeFor(driver: SqlDriver): NotesLocalStore =
    NotesLocalStore(driver = driver, dispatcher = Dispatchers.Unconfined)

fun testNote(
    id: String,
    content: String = "içerik-$id",
    title: String = "Not $id",
    updatedAt: String = "2026-01-01T00:00:00Z",
    deviceId: String = "test",
) = Note(id = id, title = title, content = content, updatedAt = updatedAt, deviceId = deviceId)

/** Denetlenebilir uzak kaynak: yayını, hatayı ve yazma başarısızlığını testler sürer. */
class FakeRemoteNotesSource(initial: List<Note> = emptyList()) : RemoteNotesSource {

    private val emissions = MutableStateFlow(initial)

    /** Sıfırdan büyükse [observeNotes] o kadar abonelikte hata ile biter. */
    var failuresRemaining: Int = 0

    var subscriptionCount: Int = 0
        private set

    /** true ise [upsertNote] ve [deleteNote] hata fırlatır (ağ yok benzetimi). */
    var writesFail: Boolean = false

    val upserted = mutableListOf<Note>()
    val deleted = mutableListOf<String>()

    fun emitRemote(notes: List<Note>) {
        emissions.value = notes
    }

    override fun observeNotes(): Flow<List<Note>> = flow {
        subscriptionCount++
        if (failuresRemaining > 0) {
            failuresRemaining--
            throw IllegalStateException("fake bağlantı koptu")
        }
        emitAll(emissions)
    }

    override suspend fun upsertNote(note: Note) {
        if (writesFail) throw IllegalStateException("fake ağ yok")
        upserted += note
    }

    override suspend fun deleteNote(id: String) {
        if (writesFail) throw IllegalStateException("fake ağ yok")
        deleted += id
    }
}
