package com.emotionwheel.app.data

import com.emotionwheel.app.data.catalog.EmotionCatalog
import com.emotionwheel.app.data.csv.JournalCsv
import com.emotionwheel.app.data.local.JournalEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/**
 * The reader has to survive the real file, which is why these cases are reproduce the shapes such a
 * file takes: several emotions in one cell, quoted text with commas, names the wheel
 * does not have, and dates typed with a doubled slash. The wording is invented.
 */
class JournalCsvTest {

    private val catalog =
        EmotionCatalog.parse(File("src/main/assets/emotions.json").readText())

    private fun parse(csv: String) =
        JournalCsv.parse(ByteArrayInputStream(csv.toByteArray()), catalog)

    @Test
    fun `reads a plain row`() {
        val rows = parse(
            """
            fecha,emoción ,evento
            02/06/2026,alivio,terminé un pendiente largo
            """.trimIndent()
        )
        assertEquals(1, rows.size)
        assertEquals(LocalDate.of(2026, 6, 2).toEpochDay(), rows[0].dateEpochDay)
        assertEquals(listOf("alivio"), rows[0].emotionIds)
        assertNull(rows[0].customEmotion)
        assertEquals("terminé un pendiente largo", rows[0].situation)
    }

    @Test
    fun `splits a cell naming several emotions`() {
        val rows = parse(
            """
            fecha,emoción,evento
            09/06/2026,impaciencia / hostilidad / desánimo ,se canceló la cita del lunes
            """.trimIndent()
        )
        assertEquals(listOf("impaciencia", "hostilidad", "desanimo"), rows[0].emotionIds)
    }

    @Test
    fun `keeps emotions the wheel does not name`() {
        val rows = parse(
            """
            fecha,emoción,evento
            05/06/2026,vergüenza ,confundí un nombre en la reunión
            07/06/2026,indiferencia / desánimo ,no sentí nada en todo el día
            """.trimIndent()
        )
        assertEquals(emptyList<String>(), rows[0].emotionIds)
        assertEquals("vergüenza", rows[0].customEmotion)
        // A mixed row keeps the wheel emotion and the free-text one side by side.
        assertEquals(listOf("desanimo"), rows[1].emotionIds)
        assertEquals("indiferencia", rows[1].customEmotion)
    }

    @Test
    fun `repairs a doubled slash in the date`() {
        val rows = parse(
            """
            fecha,emoción,evento
            11//06/2026,esperanza ,de que la conversación saliera bien
            """.trimIndent()
        )
        assertEquals(LocalDate.of(2026, 6, 11).toEpochDay(), rows[0].dateEpochDay)
    }

    @Test
    fun `reads a quoted field holding commas`() {
        val rows = parse(
            """
            fecha,emoción,evento
            03/06/2026,desilusión ,"de acomodar la mesa, las sillas y los cuadros"
            """.trimIndent()
        )
        assertEquals(1, rows.size)
        assertEquals("de acomodar la mesa, las sillas y los cuadros", rows[0].situation)
    }

    @Test
    fun `strips the stray asterisks the source uses as side notes`() {
        val rows = parse(
            """
            fecha,emoción,evento
            12/06/2026,lástima *,De no aprovechar la tarde libre
            """.trimIndent()
        )
        assertEquals("lástima", rows[0].customEmotion)
    }

    @Test
    fun `the same row always gets the same id, so importing twice adds nothing`() {
        val csv = """
            fecha,emoción,evento
            02/06/2026,alivio,terminé un pendiente largo
        """.trimIndent()
        assertEquals(parse(csv).single().id, parse(csv).single().id)
    }

    @Test
    fun `a row without a usable date is skipped rather than failing the import`() {
        val rows = parse(
            """
            fecha,emoción,evento
            no es fecha,alivio,una situación cualquiera
            02/06/2026,alivio,una situación cualquiera
            """.trimIndent()
        )
        assertEquals(1, rows.size)
    }

    @Test
    fun `an entry written in the app matches its own exported row`() {
        // Entries created in the app carry a random UUID rather than a content-derived
        // id, so re-importing an export of them used to add a second copy. The content
        // key is what closes that gap.
        val written = JournalEntryEntity(
            id = "9f1c2d3e-0000-4444-8888-aaaabbbbcccc", // random, as JournalRepository assigns
            dateEpochDay = LocalDate.of(2026, 6, 14).toEpochDay(),
            emotionIds = listOf("satisfaccion"),
            customEmotion = null,
            situation = "De haber terminado el curso que empecé",
            createdAt = 0,
            updatedAt = 0,
        )
        val out = ByteArrayOutputStream()
        JournalCsv.export(
            listOf(
                JournalEntry(
                    id = written.id,
                    date = LocalDate.ofEpochDay(written.dateEpochDay),
                    emotions = written.emotionIds.map(catalog::require),
                    customEmotion = written.customEmotion,
                    situation = written.situation,
                    createdAt = 0,
                    updatedAt = 0,
                    dirty = false,
                )
            ),
            out,
        )

        val reimported = parse(out.toString(Charsets.UTF_8.name())).single()
        assertNotEquals("the ids genuinely differ", written.id, reimported.id)
        assertEquals(
            "yet they are the same entry",
            JournalCsv.contentKey(written),
            JournalCsv.contentKey(reimported),
        )
    }

    @Test
    fun `the content key ignores emotion order and stray whitespace`() {
        val a = JournalEntryEntity(
            id = "a", dateEpochDay = 100,
            emotionIds = listOf("impaciencia", "desanimo"),
            customEmotion = null, situation = "pasó  algo ",
            createdAt = 0, updatedAt = 0,
        )
        val b = a.copy(
            id = "b",
            emotionIds = listOf("desanimo", "impaciencia"),
            situation = "pasó algo",
        )
        assertEquals(JournalCsv.contentKey(a), JournalCsv.contentKey(b))
    }

    @Test
    fun `export writes back what import can read`() {
        val original = parse(
            """
            fecha,emoción,evento
            02/06/2026,alivio,"por fin, sin prisa"
            09/06/2026,impaciencia / hostilidad ,cambiaron los planes a última hora
            05/06/2026,vergüenza ,me equivoqué de fecha
            """.trimIndent()
        )
        val entries = original.map { row ->
            JournalEntry(
                id = row.id,
                date = LocalDate.ofEpochDay(row.dateEpochDay),
                emotions = row.emotionIds.map(catalog::require),
                customEmotion = row.customEmotion,
                situation = row.situation,
                createdAt = 0,
                updatedAt = 0,
                dirty = true,
            )
        }

        val out = ByteArrayOutputStream()
        JournalCsv.export(entries, out)
        val text = out.toString(Charsets.UTF_8.name())
        assertTrue(text.startsWith("﻿" + "fecha,emoción,evento"))

        val reread = parse(text)
        assertEquals(original.map { it.id }.toSet(), reread.map { it.id }.toSet())
    }
}
