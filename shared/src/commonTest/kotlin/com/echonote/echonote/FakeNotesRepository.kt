package com.echonote.echonote

import com.echonote.echonote.ai.AiAction
import com.echonote.echonote.ai.AiService
import com.echonote.echonote.data.NotesRepository
import com.echonote.echonote.model.Note
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Testler için denetlenebilir depo: aboneliği sayar, istenen sayıda abonelikte hata
 * fırlatır ve [updateGate] ile yazmayı ağda askıda kalmış gibi bekletebilir.
 */
class FakeNotesRepository(initial: List<Note> = emptyList()) : NotesRepository {

    private val emissions = MutableStateFlow(initial)

    /** Sıfırdan büyükse [observeNotes] o kadar abonelikte hata ile biter. */
    var failuresRemaining: Int = 0

    /** [observeNotes]'un kaç kez abone olunduğu; retry davranışının kanıtı. */
    var subscriptionCount: Int = 0
        private set

    /** Doluysa [updateNote] bu kapı açılana kadar askıda kalır. */
    var updateGate: CompletableDeferred<Unit>? = null

    /** [updateNote]'a kaç kez girildiği (kapıda beklemeye başlamak dahil). */
    var updateEnteredCount: Int = 0
        private set

    val created = mutableListOf<Note>()
    val updated = mutableListOf<Note>()
    val deleted = mutableListOf<String>()

    /** Uzak yayını simüle eder (Realtime'dan gelen liste). */
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

    override suspend fun createNote(note: Note) {
        created += note
    }

    override suspend fun updateNote(note: Note) {
        updateEnteredCount++
        updateGate?.await()
        updated += note
    }

    override suspend fun deleteNote(id: String) {
        deleted += id
    }
}

/** Ağa çıkmayan, deterministik AI. */
class FakeAiService(private val result: String = "# AI sonucu") : AiService {
    override suspend fun transform(action: AiAction, title: String, content: String): String = result
}
