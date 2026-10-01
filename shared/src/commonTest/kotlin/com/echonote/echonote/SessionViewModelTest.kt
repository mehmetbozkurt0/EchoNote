package com.echonote.echonote

import com.echonote.echonote.data.auth.AuthGate
import com.echonote.echonote.data.auth.AuthService
import com.echonote.echonote.data.auth.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeAuthService : AuthService {
        private val _state = MutableStateFlow(AuthState())
        override val state: Flow<AuthState> = _state.asStateFlow()
        var signInCalls = 0
            private set
        var signOutCalls = 0
            private set
        var failWith: Throwable? = null

        fun emit(state: AuthState) {
            _state.value = state
        }

        override suspend fun signIn(email: String, password: String) {
            signInCalls++
            failWith?.let { throw it }
            _state.value = AuthState(AuthGate.SignedIn, email)
        }

        override suspend fun signUp(email: String, password: String) {
            failWith?.let { throw it }
            _state.value = AuthState(AuthGate.SignedIn, email)
        }

        override suspend fun signOut() {
            signOutCalls++
            _state.value = AuthState(AuthGate.SignedOut)
        }
    }

    @Test
    fun oturumDurumuUiStateyeAkar() = runTest(dispatcher) {
        val auth = FakeAuthService()
        val vm = SessionViewModel(auth) { FakeNotesRepository() }
        advanceUntilIdle()

        assertEquals(AuthGate.Loading, vm.uiState.value.gate)

        auth.emit(AuthState(AuthGate.SignedIn, "a@b.com"))
        runCurrent()
        assertEquals(AuthGate.SignedIn, vm.uiState.value.gate)
        assertEquals("a@b.com", vm.uiState.value.email)
    }

    @Test
    fun girisHatasiKullaniciyaGosterilirVeBusyBirakilir() = runTest(dispatcher) {
        val auth = FakeAuthService().apply { failWith = IllegalStateException("Invalid login credentials") }
        val vm = SessionViewModel(auth) { FakeNotesRepository() }
        advanceUntilIdle()

        vm.signIn("a@b.com", "yanlisparola")
        advanceUntilIdle()

        assertEquals("Invalid login credentials", vm.uiState.value.errorMessage)
        assertEquals(false, vm.uiState.value.busy, "Hata sonrası butonlar kilitli kalmamalı")
    }

    @Test
    fun ayniAndaIkinciGirisIstegiYoksayilir() = runTest(dispatcher) {
        val auth = FakeAuthService()
        val vm = SessionViewModel(auth) { FakeNotesRepository() }
        advanceUntilIdle()

        vm.signIn("a@b.com", "parola123")
        vm.signIn("a@b.com", "parola123") // busy iken yok sayılmalı
        advanceUntilIdle()

        assertEquals(1, auth.signInCalls)
    }

    @Test
    fun cikisYerelVeriyiSiler() = runTest(dispatcher) {
        val auth = FakeAuthService()
        val repo = FakeNotesRepository()
        val vm = SessionViewModel(auth) { repo }
        advanceUntilIdle()

        vm.signOut()
        advanceUntilIdle()

        assertEquals(1, auth.signOutCalls)
        assertEquals(
            1,
            repo.clearCount,
            "Çıkışta yerel kopya silinmezse sonraki hesap öncekinin notlarını görür",
        )
        assertEquals(AuthGate.SignedOut, vm.uiState.value.gate)
        assertTrue(vm.uiState.value.email == null)
    }
}
