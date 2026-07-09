package com.echonote.echonote.data

import com.echonote.echonote.model.Note
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.Flow

/**
 * Supabase "notes" tablosu üzerinde CRUD + Realtime.
 * [observeNotes], selectAsFlow ile tablodaki her değişikliği (insert/update/delete) anlık yayınlar;
 * bunun için tablonun Supabase panelinde Realtime yayınına ekli olması gerekir.
 * Sıralama ve çakışma çözümü (last-write-wins) ViewModel katmanında updated_at ile yapılır.
 */
class SupabaseNotesRepository(private val client: SupabaseClient) : NotesRepository {

    @OptIn(SupabaseExperimental::class)
    override fun observeNotes(): Flow<List<Note>> =
        client.from(TABLE).selectAsFlow(Note::id)

    override suspend fun createNote(note: Note) {
        client.from(TABLE).insert(note)
    }

    override suspend fun updateNote(note: Note) {
        client.from(TABLE).update(note) {
            filter { Note::id eq note.id }
        }
    }

    override suspend fun deleteNote(id: String) {
        client.from(TABLE).delete {
            filter { Note::id eq id }
        }
    }

    private companion object {
        const val TABLE = "notes"
    }
}
