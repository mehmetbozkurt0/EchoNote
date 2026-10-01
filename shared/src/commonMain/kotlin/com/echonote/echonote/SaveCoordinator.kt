package com.echonote.echonote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Uygulama kapanırken bekleyen yazmaların boşaltılması için kanca kaydı.
 *
 * Desktop'ta pencere kapanışı (`onCloseRequest`) JVM'i ağ çağrısı bitmeden sonlandırdığı
 * için kapanışın bekleyen kayıtları beklemesi gerekir; ViewModel ise composition içinde
 * kurulduğu için `main.kt` ona doğrudan erişemez. Bu nesne aradaki tek bağ.
 *
 * Android'de asıl yol [androidx.lifecycle.compose.LifecycleEventEffect] ile ON_STOP'tur;
 * burası yedek kalır. Çifte boşaltma zararsızdır: kayıt idempotenttir.
 */
object SaveCoordinator {

    // Kayıt Main thread'de, flushAll desktop'ta AWT thread'inde koşuyor; StateFlow'un
    // CAS tabanlı update'i commonMain'de ek bağımlılık olmadan güvenli erişim verir.
    private val hooks = MutableStateFlow<List<suspend () -> Unit>>(emptyList())

    /** Kancayı kaydeder; dönen lambda kaydı iptal eder (ViewModel `onCleared` içinde çağırır). */
    fun register(hook: suspend () -> Unit): () -> Unit {
        hooks.update { it + hook }
        return { hooks.update { current -> current - hook } }
    }

    /**
     * Kayıtlı tüm kancaları sırayla çalıştırır. Biri patlarsa diğerleri yine koşar:
     * kapanış yolunda tek bir başarısız yazma yüzünden kalan notları kaybetmek istemeyiz.
     */
    suspend fun flushAll() {
        hooks.value.forEach { hook -> runCatching { hook() } }
    }
}
