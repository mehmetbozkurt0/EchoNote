package com.echonote.echonote.data

import com.echonote.echonote.data.local.monotonicStamp
import com.echonote.echonote.model.parseTimestampOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `updated_at` çakışma çözümünün tek girdisi. İstemci saatinden geldiği için geri
 * gidebiliyor; bu testler damganın bir satır için asla gerilemediğini doğruluyor.
 */
class MonotonicStampTest {

    @Test
    fun yeniDamgaOldugGibiGecer() {
        assertEquals(
            "2026-10-02T10:00:01Z",
            monotonicStamp("2026-10-02T10:00:01Z", "2026-10-02T10:00:00Z"),
        )
    }

    @Test
    fun yeniSatirdaKiyaslamaYok() {
        assertEquals("2026-10-02T10:00:00Z", monotonicStamp("2026-10-02T10:00:00Z", null))
    }

    @Test
    fun saatGeriGiderseDamgaIleriTasinir() {
        // Telefonun saati 5 dk geri alındı: yeni düzenleme eskisinden eski damgalanırdı.
        val sonuc = monotonicStamp("2026-10-02T09:55:00Z", "2026-10-02T10:00:00Z")
        val yeni = parseTimestampOrNull(sonuc)!!
        val eski = parseTimestampOrNull("2026-10-02T10:00:00Z")!!

        assertTrue(yeni > eski, "Damga geri gitmemeli: $sonuc")
    }

    @Test
    fun ayniAndaIkiYazmaCakismaz() {
        // Hızlı art arda iki kayıt aynı milisaniyeye düşerse ikincisi "daha yeni" olmalı,
        // yoksa uzak sürüm eşitlikte kazanır ve son harfler kaybolur.
        val ilk = "2026-10-02T10:00:00Z"
        val ikinci = monotonicStamp(ilk, ilk)

        assertTrue(parseTimestampOrNull(ikinci)!! > parseTimestampOrNull(ilk)!!)
    }

    @Test
    fun cozulemeyenMevcutDamgaYeniyiBozmaz() {
        assertEquals("2026-10-02T10:00:00Z", monotonicStamp("2026-10-02T10:00:00Z", "bozuk"))
    }
}
