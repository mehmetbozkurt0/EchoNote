package com.echonote.echonote.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseAuthService(private val client: SupabaseClient) : AuthService {

    override val state: Flow<AuthState> = client.auth.sessionStatus.map { it.toAuthState() }

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }
}

/**
 * Supabase oturum durumunu uygulama kapısına çevirir.
 *
 * [SessionStatus.RefreshFailure] bilerek **SignedIn** sayılır: bu durum çevrimdışıyken
 * saklı oturumun yenilenememesi demektir. Kullanıcıyı giriş ekranına atmak onu KENDİ
 * cihazındaki yerel notlarından kilitlerdi — offline-first vaadini doğrudan bozardı.
 * Ağ gelince senkron kendiliğinden toparlar.
 */
internal fun SessionStatus.toAuthState(): AuthState = when (this) {
    is SessionStatus.Initializing -> AuthState(gate = AuthGate.Loading)
    is SessionStatus.Authenticated -> AuthState(gate = AuthGate.SignedIn, email = session.user?.email)
    is SessionStatus.RefreshFailure -> AuthState(gate = AuthGate.SignedIn)
    is SessionStatus.NotAuthenticated -> AuthState(gate = AuthGate.SignedOut)
}
