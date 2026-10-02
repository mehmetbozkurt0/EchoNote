package com.echonote.echonote

import com.echonote.echonote.ai.AiAction
import com.echonote.echonote.ai.AiService
import com.echonote.echonote.ai.GeminiAiService
import com.echonote.echonote.ai.MockAiService
import com.echonote.echonote.data.NotesRepository
import com.echonote.echonote.data.OfflineFirstNotesRepository
import com.echonote.echonote.data.SupabaseNotesSource
import com.echonote.echonote.data.auth.AuthGate
import com.echonote.echonote.data.auth.AuthService
import com.echonote.echonote.data.auth.NoAuthService
import com.echonote.echonote.data.auth.SupabaseAuthService
import com.echonote.echonote.data.local.NotesLocalStore
import com.echonote.echonote.data.local.createSqlDriver
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/**
 * Uygulama servislerinin tek sahibi.
 *
 * Tekil olmak zorunda: yerel SQLite sürücüsü ve senkron motoru tek örnek olmalı, ama
 * artık iki ViewModel (notlar ve oturum) aynı depoya ihtiyaç duyuyor. Her biri kendi
 * fabrikasını çağırsaydı iki ayrı veritabanı bağlantısı ve iki senkron döngüsü olurdu.
 *
 * Her şey `by lazy`: çevrimdışı modda Supabase istemcisi hiç kurulmaz, giriş yapılmadan
 * senkron döngüleri başlamaz.
 */
object AppServices {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: AppSettings by lazy { AppSettings() }

    /** Secrets boşsa null: uygulama yalnızca yerel depoyla, kimlik doğrulamasız çalışır. */
    private val supabase: SupabaseClient? by lazy {
        if (Secrets.SUPABASE_URL.isBlank() || Secrets.SUPABASE_ANON_KEY.isBlank()) {
            null
        } else {
            createSupabaseClient(
                supabaseUrl = Secrets.SUPABASE_URL,
                supabaseKey = Secrets.SUPABASE_ANON_KEY,
            ) {
                defaultSerializer = KotlinXSerializer(
                    Json {
                        // notes tablosuna user_id kolonu eklendi ama Note modelinde böyle
                        // bir alan yok; bu olmadan selectAsFlow'un döndürdüğü satırlar
                        // çözümlenirken çalışma zamanında patlar.
                        ignoreUnknownKeys = true
                        // ŞART: deletedAt'in varsayılanı null. encodeDefaults false iken
                        // null olan alan JSON'a hiç yazılmaz, Supabase upsert'i o kolona
                        // dokunmaz ve çöpten geri yükleme sunucuya ULAŞMAZ — not bir
                        // sonraki pull'da yeniden çöpe düşer.
                        encodeDefaults = true
                    }
                )
                install(Auth)
                install(Postgrest)
                install(Realtime)
            }
        }
    }

    val authService: AuthService by lazy {
        supabase?.let(::SupabaseAuthService) ?: NoAuthService
    }

    private val localStore: NotesLocalStore by lazy {
        NotesLocalStore(driver = createSqlDriver(), dispatcher = Dispatchers.IO)
    }

    val notesRepository: NotesRepository by lazy {
        OfflineFirstNotesRepository(
            local = localStore,
            remote = supabase?.let(::SupabaseNotesSource),
            scope = scope,
            isAuthenticated = authService.state
                .map { it.gate == AuthGate.SignedIn }
                .stateIn(scope, SharingStarted.Eagerly, false),
        )
    }

    val aiService: AiService by lazy { RuntimeKeyAiService(settings) }
}

/**
 * Gemini anahtarını **çalışma zamanında** okur: ayarlardan girilince uygulamayı yeniden
 * başlatmak gerekmez, anahtar silinince sahte AI'a geri düşer.
 */
private class RuntimeKeyAiService(private val settings: AppSettings) : AiService {

    private val mock = MockAiService()

    private val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
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
        }
    }

    override suspend fun transform(action: AiAction, title: String, content: String): String {
        val key = settings.geminiApiKey.value
        return if (key.isBlank()) {
            mock.transform(action, title, content)
        } else {
            GeminiAiService(apiKey = key, client = httpClient).transform(action, title, content)
        }
    }
}

// ViewModel varsayılan argümanlarının kullandığı erişimciler; testlerde enjeksiyon açık kalır.
fun createNotesRepository(): NotesRepository = AppServices.notesRepository

fun createAiService(): AiService = AppServices.aiService

fun createAuthService(): AuthService = AppServices.authService

fun createDeviceId(): String = AppServices.settings.deviceId
