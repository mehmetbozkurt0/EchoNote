package com.echonote.echonote.data

import com.echonote.echonote.model.Note
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.Flow

/**
 * Supabase "notes" tablosu — artık tek gerçek kaynak değil, [RemoteNotesSource]:
 * yerel depo ile Supabase arasındaki senkronu [OfflineFirstNotesRepository] yürütür.
 *
 * [observeNotes], selectAsFlow ile tablodaki her değişikliği anlık yayınlar; bunun için
 * tablonun Supabase panelinde Realtime yayınına ekli olması gerekir (supabase/schema.sql).
 */
class SupabaseNotesSource(private val client: SupabaseClient) : RemoteNotesSource {

    @OptIn(SupabaseExperimental::class)
    override fun observeNotes(): Flow<List<Note>> =
        client.from(TABLE).selectAsFlow(Note::id)

    /**
     * Outbox tek bir upsert ile çalışır: yerelde oluşturulmuş not uzakta var da olabilir
     * yok da (örn. push başarısız olup yeniden denenmişse), insert/update ayrımı yapmak
     * gereksiz bir hata kaynağı olurdu.
     */
    override suspend fun upsertNote(note: Note) {
        client.from(TABLE).upsert(note)
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
