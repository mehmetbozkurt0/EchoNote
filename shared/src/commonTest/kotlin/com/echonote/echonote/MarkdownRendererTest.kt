package com.echonote.echonote

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownRendererTest {

    private val colors = MarkdownColors(
        heading = Color.Cyan, marker = Color.Gray, code = Color.Green,
        codeBackground = Color.DarkGray, quote = Color.Magenta,
    )

    @Test
    fun basliklarSeviyesiyleCozumlenir() {
        val blocks = parseMarkdown("# Bir\n## Iki\n###### Alti")
        assertEquals(3, blocks.size)
        assertEquals(MdBlock.Heading(1, "Bir"), blocks[0])
        assertEquals(MdBlock.Heading(2, "Iki"), blocks[1])
        assertEquals(MdBlock.Heading(6, "Alti"), blocks[2])
    }

    @Test
    fun ardisikSatirlarTekParagrafOlur() {
        val blocks = parseMarkdown("ilk satir\nikinci satir\n\nayri paragraf")
        assertEquals(2, blocks.size)
        assertEquals(MdBlock.Paragraph("ilk satir ikinci satir"), blocks[0])
        assertEquals(MdBlock.Paragraph("ayri paragraf"), blocks[1])
    }

    @Test
    fun listelerVeSiraliListeler() {
        val blocks = parseMarkdown("- bir\n* iki\n1. uc")
        assertEquals(MdBlock.Bullet("bir", "•"), blocks[0])
        assertEquals(MdBlock.Bullet("iki", "•"), blocks[1])
        assertEquals(MdBlock.Bullet("uc", "1."), blocks[2])
    }

    @Test
    fun gorevKutulariDurumuVeSatirNumarasiylaCozumlenir() {
        val blocks = parseMarkdown("# Liste\n- [ ] yapilacak\n- [x] bitti")
        val tasks = blocks.filterIsInstance<MdBlock.Task>()
        assertEquals(2, tasks.size)
        assertEquals(MdBlock.Task("yapilacak", checked = false, lineIndex = 1), tasks[0])
        assertEquals(MdBlock.Task("bitti", checked = true, lineIndex = 2), tasks[1])
    }

    @Test
    fun kodBloguCitlerArasindakiMetniAlir() {
        val blocks = parseMarkdown("once\n```\nval x = 1\nval y = 2\n```\nsonra")
        val code = blocks.filterIsInstance<MdBlock.Code>().single()
        assertEquals("val x = 1\nval y = 2", code.code)
        assertEquals(3, blocks.size)
    }

    @Test
    fun alintiVeYatayCizgi() {
        val blocks = parseMarkdown("> alinti\n\n---")
        assertEquals(MdBlock.Quote("alinti"), blocks[0])
        assertIs<MdBlock.Rule>(blocks[1])
    }

    // --- Görev kutusu işaretleme ---

    @Test
    fun gorevKutusuIsaretlenirVeKaldirilir() {
        val raw = "# Liste\n- [ ] bir\n- [x] iki"

        val checked = toggleTask(raw, 1)
        assertEquals("# Liste\n- [x] bir\n- [x] iki", checked)

        val unchecked = toggleTask(checked, 2)
        assertEquals("# Liste\n- [x] bir\n- [ ] iki", unchecked)
    }

    @Test
    fun ayniMetinliIkiGorevKaristirilmaz() {
        // Satır numarasıyla çalışmasının sebebi: aynı metin birden fazla kez geçebilir.
        val raw = "- [ ] tekrar\n- [ ] tekrar"
        assertEquals("- [ ] tekrar\n- [x] tekrar", toggleTask(raw, 1))
    }

    @Test
    fun gorevOlmayanSatirDegistirilmez() {
        val raw = "# Baslik\nduz metin"
        assertEquals(raw, toggleTask(raw, 1))
        assertEquals(raw, toggleTask(raw, 99))
    }

    // --- Satır içi ---

    @Test
    fun okumaModundaIsaretlerSilinirBicimKalir() {
        val annotated = renderInline("**kalin** ve *italik* ve `kod` bitti", colors)

        assertEquals(
            "kalin ve italik ve kod bitti",
            annotated.text,
            "Okuma modunda yıldız ve backtick görünmemeli",
        )
        assertEquals(3, annotated.spanStyles.size, "Üç biçim de uygulanmalı")
    }

    @Test
    fun isaretsizMetinAynenKalir() {
        assertEquals("duz bir cumle", renderInline("duz bir cumle", colors).text)
    }

    @Test
    fun baglantiMetniGosterilirHedefiAnnotationdaTasinir() {
        val annotated = renderInline("bak [şuna](https://ornek.com) hemen", colors)

        assertEquals("bak şuna hemen", annotated.text)
        val link = annotated.getStringAnnotations("link", 0, annotated.text.length).single()
        assertEquals("https://ornek.com", link.item)
    }
}
