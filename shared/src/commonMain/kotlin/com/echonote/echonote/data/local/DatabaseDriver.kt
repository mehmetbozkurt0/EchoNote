package com.echonote.echonote.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Platforma özgü SQLite sürücüsü. Veritabanı dosyasının konumu ve şema kurulumu/
 * migration'ı actual gerçeklemelerde yapılır:
 * - Android: framework SQLite, create/migrate'i sürücü kendi halleder.
 * - JVM (desktop): JDBC sürücüsü şemayı kendi kurmadığı için PRAGMA user_version
 *   ile create/migrate ayrımı elle yapılır.
 */
expect fun createSqlDriver(): SqlDriver
