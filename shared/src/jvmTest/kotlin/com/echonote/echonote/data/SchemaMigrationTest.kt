package com.echonote.echonote.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.echonote.echonote.db.EchoDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Migration bu projedeki en riskli değişiklik türü: yanlış giderse kullanıcının yerel
 * notları gider. Bu test, v1 şemasıyla kurulmuş ve içinde veri olan bir veritabanının
 * v2'ye geçerken **hiçbir satırı kaybetmediğini** doğrular.
 */
class SchemaMigrationTest {

    /** Adım 2'de yayınlanan v1 şeması — deleted_at kolonu yok. */
    private val v1Schema = """
        CREATE TABLE note (
            id             TEXT NOT NULL PRIMARY KEY,
            title          TEXT NOT NULL,
            content        TEXT NOT NULL,
            updated_at     TEXT NOT NULL,
            device_id      TEXT NOT NULL,
            dirty          INTEGER NOT NULL DEFAULT 0,
            pending_delete INTEGER NOT NULL DEFAULT 0
        )
    """.trimIndent()

    @Test
    fun v1denGuncelSurumeGecisteSatirlarKorunur() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // Eski sürümdeki gibi kur ve gerçek veri koy.
        driver.execute(null, v1Schema, 0)
        driver.execute(null, "CREATE INDEX note_updated_at ON note(updated_at DESC)", 0)
        repeat(3) { i ->
            driver.execute(
                null,
                "INSERT INTO note(id,title,content,updated_at,device_id,dirty,pending_delete) " +
                    "VALUES ('id$i','Baslik $i','Icerik $i','2026-01-0${i + 1}T00:00:00Z','eski',1,0)",
                0,
            )
        }

        EchoDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = EchoDatabase.Schema.version).value

        val queries = EchoDatabase(driver).noteQueries
        val rows = queries.selectPendingPush().executeAsList()
        assertEquals(3, rows.size, "Migration satır kaybetmemeli")
        assertEquals("Icerik 1", rows.first { it.id == "id1" }.content)
        assertTrue(rows.all { it.dirty }, "Senkron bayrakları korunmalı")
        assertTrue(rows.all { it.deleted_at == null }, "Mevcut notlar çöpe düşmemeli")
        assertTrue(rows.all { it.tags == "[]" }, "Etiketler boş varsayılanla gelmeli")
        assertTrue(rows.none { it.pinned }, "Hiçbir not sabitlenmiş olmamalı")
    }

    /** Kullanıcının cihazı v2'de kalmış olabilir: ara basamağın da veri koruduğunu doğrula. */
    @Test
    fun v2denV3eGecisteSatirlarKorunur() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, v1Schema, 0)
        driver.execute(null, "ALTER TABLE note ADD COLUMN deleted_at TEXT", 0)
        driver.execute(
            null,
            "INSERT INTO note(id,title,content,updated_at,device_id,dirty,pending_delete,deleted_at) " +
                "VALUES ('v2','Baslik','Icerik','2026-01-01T00:00:00Z','eski',0,0,NULL)",
            0,
        )

        EchoDatabase.Schema.migrate(driver, oldVersion = 2, newVersion = 3).value

        val row = EchoDatabase(driver).noteQueries.selectById("v2").executeAsOne()
        assertEquals("Icerik", row.content)
        assertEquals("[]", row.tags)
        assertTrue(!row.pinned)
    }

    @Test
    fun sifirdanKurulumDogrudanGuncelSurumOlur() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EchoDatabase.Schema.create(driver).value

        assertEquals(3L, EchoDatabase.Schema.version)

        val queries = EchoDatabase(driver).noteQueries
        queries.insertNote(
            id = "a", title = "t", content = "c", updated_at = "2026-01-01T00:00:00Z",
            device_id = "d", dirty = false, pending_delete = false, deleted_at = null,
            tags = "[]", pinned = false,
        )
        assertNull(queries.selectById("a").executeAsOne().deleted_at)
    }
}
