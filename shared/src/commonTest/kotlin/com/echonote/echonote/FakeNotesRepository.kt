package com.echonote.echonote

import com.echonote.echonote.ai.AiAction
import com.echonote.echonote.ai.AiService
import com.echonote.echonote.data.NotesRepository
import com.echonote.echonote.data.SyncState
import com.echonote.echonote.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel testleri için depo ikizi. Yayınlar bilinçli olarak **otomatik değil**:
 * yerel yazmanın UI'a dönüşü gerçekte asenkron olduğu için testler bu gecikmeyi
 * [emitNotes] ile kendileri kurgular.
 */
class FakeNotesRepository : NotesRepository {

    private val notes = MutableStateFlow<List<Note>>(emptyList())
    private val sync = MutableStateFlow(SyncState())
    private val trash = MutableStateFlow<List<Note>>(emptyList())
    private val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val saved = mutableListOf<Note>()
    val deleted = mutableListOf<String>()
    val restored = mutableListOf<String>()
    val pinnedCalls = mutableListOf<Pair<String, Boolean>>()
    val deletedForever = mutableListOf<String>()
    var flushCount = 0
        private set

    fun emitNotes(list: List<Note>) {
        notes.value = list
    }

    fun emitSync(state: SyncState) {
        sync.value = state
    }

    suspend fun emitError(message: String) {
        errors.emit(message)
    }

    override fun observeNotes(): Flow<List<Note>> = notes.asStateFlow()
    override fun observeSyncState(): Flow<SyncState> = sync.asStateFlow()
    override fun observeErrors(): Flow<String> = errors.asSharedFlow()

    override fun saveNote(note: Note) {
        saved += note
    }

    override fun observeTrash(): Flow<List<Note>> = trash.asStateFlow()

    fun emitTrash(list: List<Note>) {
        trash.value = list
    }

    override fun deleteNote(id: String) {
        deleted += id
    }

    override fun restoreNote(id: String) {
        restored += id
    }

    override fun setPinned(id: String, pinned: Boolean) {
        pinnedCalls += id to pinned
    }

    override fun deleteForever(id: String) {
        deletedForever += id
    }

    override suspend fun flushOutbox() {
        flushCount++
    }

    override suspend fun clearLocalData() {
        clearCount++
        notes.value = emptyList()
    }

    var clearCount = 0
        private set
}

/** Ağa çıkmayan, deterministik AI. */
class FakeAiService(
    private val result: String = "# AI sonucu",
    private val failWith: Throwable? = null,
) : AiService {
    override suspend fun transform(action: AiAction, title: String, content: String): String {
        failWith?.let { throw it }
        return result
    }
}
