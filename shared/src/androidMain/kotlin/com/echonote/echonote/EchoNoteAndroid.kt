package com.echonote.echonote

import android.content.Context

/**
 * Android'de SQLite sürücüsünün Application Context'e ihtiyacı var, ama shared modül
 * Android'e özgü bir giriş noktasına sahip değil. [init], `MainActivity.onCreate`
 * içinde `setContent`'ten **önce** çağrılır.
 *
 * Sıra güvenli: repository `viewModel { NotesViewModel() }` içinde tembel kurulduğu
 * için ilk erişim her zaman `setContent`'ten sonra olur.
 */
object EchoNoteAndroid {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal fun requireContext(): Context = appContext ?: error(
        "EchoNoteAndroid.init(context) çağrılmadı. MainActivity.onCreate içinde, " +
            "setContent'ten önce çağır."
    )
}
