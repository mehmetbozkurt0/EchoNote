package com.echonote.echonote.data

import com.echonote.echonote.model.Note
import com.echonote.echonote.model.sampleNotes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface NotesRepository {
    /** Not listesini reaktif olarak yayınlar; uzak kaynaklarda gerçek zamanlı değişiklikleri içerir. */
    fun observeNotes(): Flow<List<Note>>
    /** UUID, updated_at ve device_id çağıran tarafça doldurulmuş tam bir satır ekler. */
    suspend fun createNote(note: Note)
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(id: String)
}

/** Supabase bilgileri girilmemişken devreye giren bellek içi depo. */
class InMemoryNotesRepository : NotesRepository {
    private val notes = MutableStateFlow(sampleNotes())

    override fun observeNotes(): Flow<List<Note>> = notes.asStateFlow()

    override suspend fun createNote(note: Note) {
        notes.update { it + note }
    }

    override suspend fun updateNote(note: Note) {
        notes.update { list -> list.map { if (it.id == note.id) note else it } }
    }

    override suspend fun deleteNote(id: String) {
        notes.update { list -> list.filterNot { it.id == id } }
    }
}
