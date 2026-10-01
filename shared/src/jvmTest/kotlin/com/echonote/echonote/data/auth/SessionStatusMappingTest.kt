package com.echonote.echonote.data.auth

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Supabase oturum durumunun uygulama kapısına eşlenmesi. Offline-first mimarinin
 * can damarı burada: yanlış eşleme kullanıcıyı kendi cihazındaki notlarından kilitler.
 */
class SessionStatusMappingTest {

    @Test
    fun cevrimdisindaYenilenemeyenOturumKullaniciyiIceriAlir() {
        val status = SessionStatus.RefreshFailure(
            RefreshFailureCause.NetworkError(IOException("ağ yok"))
        )

        assertEquals(
            AuthGate.SignedIn,
            status.toAuthState().gate,
            "RefreshFailure giriş ekranına düşerse kullanıcı çevrimdışıyken kendi " +
                "yerel notlarından kilitlenir — offline-first vaadi kırılır",
        )
    }

    @Test
    fun oturumYokkenGirisEkraniGosterilir() {
        assertEquals(AuthGate.SignedOut, SessionStatus.NotAuthenticated(false).toAuthState().gate)
        assertEquals(AuthGate.SignedOut, SessionStatus.NotAuthenticated(true).toAuthState().gate)
    }

    @Test
    fun baslangictaYuklemeGosterilir() {
        assertEquals(AuthGate.Loading, SessionStatus.Initializing.toAuthState().gate)
    }
}
