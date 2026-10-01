package com.echonote.echonote

import com.echonote.echonote.ai.AiService
import com.echonote.echonote.ai.GeminiAiService
import com.echonote.echonote.ai.MockAiService
import com.echonote.echonote.data.NotesRepository
import com.echonote.echonote.data.OfflineFirstNotesRepository
import com.echonote.echonote.data.RemoteNotesSource
import com.echonote.echonote.data.SupabaseNotesSource
import com.echonote.echonote.data.local.NotesLocalStore
import com.echonote.echonote.data.local.createSqlDriver
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/**
 * Servis fabrikaları. Yerel depo **her zaman** kurulur — Secrets'ın dolu olup olmaması
 * yalnızca senkron katmanının var olup olmadığını belirler. Çevrimdışı modda da notlar
 * artık kalıcıdır (eski InMemoryNotesRepository her açılışta sıfırlanıyordu).
 */

fun createNotesRepository(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): NotesRepository = OfflineFirstNotesRepository(
    local = NotesLocalStore(driver = createSqlDriver(), dispatcher = Dispatchers.IO),
    remote = createRemoteNotesSource(),
    scope = scope,
)

/** Supabase bilgileri girilmemişse null: uygulama yalnızca yerel depoyla çalışır. */
private fun createRemoteNotesSource(): RemoteNotesSource? =
    if (Secrets.SUPABASE_URL.isBlank() || Secrets.SUPABASE_ANON_KEY.isBlank()) {
        null
    } else {
        SupabaseNotesSource(
            createSupabaseClient(
                supabaseUrl = Secrets.SUPABASE_URL,
                supabaseKey = Secrets.SUPABASE_ANON_KEY,
            ) {
                install(Postgrest)
                install(Realtime)
            }
        )
    }

fun createAiService(): AiService =
    if (Secrets.GEMINI_API_KEY.isBlank()) {
        MockAiService()
    } else {
        GeminiAiService(
            apiKey = Secrets.GEMINI_API_KEY,
            client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                // Gemini uzun notlarda 10-30+ sn düşünebiliyor; varsayılan zaman
                // aşımları buna takıldığı için 90 sn'ye genişletildi.
                install(HttpTimeout) {
                    requestTimeoutMillis = 90_000
                    connectTimeoutMillis = 60_000
                    socketTimeoutMillis = 90_000
                }
            },
        )
    }
