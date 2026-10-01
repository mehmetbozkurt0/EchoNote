package com.echonote.echonote.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.echonote.echonote.db.EchoDatabase
import java.io.File

actual fun createSqlDriver(): SqlDriver {
    val file = File(appDataDirectory(), "echonote.db")
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    driver.applySchema()
    return driver
}

/**
 * JDBC sürücüsü, Android'dekinin aksine şemayı kendi kurmaz ve sürüm takibi yapmaz.
 * `PRAGMA user_version` ile ilk kurulum / migration ayrımını burada yapıyoruz.
 */
private fun JdbcSqliteDriver.applySchema() {
    val schema = EchoDatabase.Schema
    val current = currentVersion()
    when {
        current == 0L -> {
            schema.create(this).value
            setVersion(schema.version)
        }
        current < schema.version -> {
            schema.migrate(this, current, schema.version).value
            setVersion(schema.version)
        }
    }
}

private fun JdbcSqliteDriver.currentVersion(): Long = executeQuery(
    identifier = null,
    sql = "PRAGMA user_version",
    mapper = { cursor ->
        app.cash.sqldelight.db.QueryResult.Value(
            if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
        )
    },
    parameters = 0,
).value

private fun JdbcSqliteDriver.setVersion(version: Long) {
    execute(identifier = null, sql = "PRAGMA user_version = $version", parameters = 0)
}

/**
 * Kullanıcının uygulama verisi dizini. Notlar kullanıcının gerçek verisi olduğu için
 * geçici dizine değil, platformun kalıcı app-data konumuna yazılır.
 */
private fun appDataDirectory(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home").orEmpty()
    val base = when {
        os.contains("win") ->
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: "$home\\AppData\\Roaming"
        os.contains("mac") || os.contains("darwin") ->
            "$home/Library/Application Support"
        else ->
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
    }
    return File(base, "EchoNote").apply { mkdirs() }
}
