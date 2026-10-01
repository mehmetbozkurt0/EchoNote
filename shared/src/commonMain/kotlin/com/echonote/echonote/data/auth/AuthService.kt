package com.echonote.echonote.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Uygulamanın hangi kapıyı göstereceği. */
enum class AuthGate { Loading, SignedIn, SignedOut }

data class AuthState(
    val gate: AuthGate = AuthGate.Loading,
    val email: String? = null,
)

interface AuthService {
    val state: Flow<AuthState>
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun signOut()
}

/**
 * Secrets boşken (çevrimdışı mod) devreye girer: kimlik doğrulama yok, uygulama
 * doğrudan açılır ve yalnızca yerel depoyla çalışır.
 */
object NoAuthService : AuthService {
    override val state: Flow<AuthState> = flowOf(AuthState(gate = AuthGate.SignedIn))
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String) = Unit
    override suspend fun signOut() = Unit
}
