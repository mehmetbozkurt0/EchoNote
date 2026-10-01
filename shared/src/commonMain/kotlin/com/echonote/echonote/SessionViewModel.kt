package com.echonote.echonote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echonote.echonote.data.NotesRepository
import com.echonote.echonote.data.auth.AuthGate
import com.echonote.echonote.data.auth.AuthService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val gate: AuthGate = AuthGate.Loading,
    val email: String? = null,
    /** Giriş/kayıt isteği sürüyor; butonlar kilitlenir. */
    val busy: Boolean = false,
    val errorMessage: String? = null,
)

class SessionViewModel(
    private val authService: AuthService = createAuthService(),
    /**
     * Sağlayıcı olarak alınır, hazır örnek olarak DEĞİL: varsayılan parametre olarak
     * `createNotesRepository()` yazılırsa depo ve senkron motoru, giriş ekranı daha
     * ekrandayken kurulur ve kimliksiz bir uzak sorgu yerel veriyi silebilir.
     */
    private val repositoryProvider: () -> NotesRepository = ::createNotesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authService.state.collect { auth ->
                _uiState.update { it.copy(gate = auth.gate, email = auth.email) }
            }
        }
    }

    fun signIn(email: String, password: String) = authCall { authService.signIn(email, password) }

    fun signUp(email: String, password: String) = authCall { authService.signUp(email, password) }

    fun signOut() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                authService.signOut()
                // Yerel veriyi oturum kapandıktan SONRA sil: silme sırasında senkron
                // motoru hâlâ yazıyor olsaydı, silinenleri uzağa silme olarak gönderebilirdi.
                repositoryProvider().clearLocalData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Çıkış yapılamadı: ${e.message}") }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun authCall(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        // busy coroutine İÇİNDE set edilirse guard işe yaramaz: çift tıklamada iki
        // istek de başlamadan önce bayrağı false görür.
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Bilinmeyen hata") }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
}
