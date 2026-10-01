package com.echonote.echonote.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.echonote.echonote.EchoNoteAndroid
import com.echonote.echonote.db.EchoDatabase

/** AndroidSqliteDriver şema kurulumunu ve migration'ları kendisi yürütür. */
actual fun createSqlDriver(): SqlDriver = AndroidSqliteDriver(
    schema = EchoDatabase.Schema,
    context = EchoNoteAndroid.requireContext(),
    name = DATABASE_NAME,
)

internal const val DATABASE_NAME = "echonote.db"
