package com.echonote.echonote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * "Kullanıcı şu an bir şey yapıyor mu" takibi. Arka plan animasyonunu yalnızca
 * etkileşim varken çalıştırmak için: kimse ekranla uğraşmıyorken kare üretmenin
 * pil harcamaktan başka getirisi yok.
 */
class ActivityState internal constructor(internal val timeoutMs: Long) {

    /** Her etkileşimde artar; boşta sayacını yeniden başlatmak için kullanılır. */
    internal var pulse by mutableLongStateOf(0L)
        private set

    var isActive by mutableStateOf(true)
        internal set

    /** Herhangi bir kullanıcı eylemi: işaretçi, tuş, metin değişimi. */
    fun touch() {
        pulse++
        isActive = true
    }
}

@Composable
fun rememberActivityState(timeoutMs: Long = IDLE_TIMEOUT_MS): ActivityState {
    val state = remember { ActivityState(timeoutMs) }
    LaunchedEffect(state.pulse) {
        delay(state.timeoutMs)
        state.isActive = false
    }
    return state
}

/**
 * İşaretçi olaylarını **Initial** geçişte dinler: olay tüketilmez, altındaki
 * tıklama ve kaydırma davranışları olduğu gibi çalışmaya devam eder.
 */
fun Modifier.trackActivity(state: ActivityState): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            state.touch()
        }
    }
}

/** Boşta sayılmadan önce geçmesi gereken süre. */
const val IDLE_TIMEOUT_MS = 5_000L
