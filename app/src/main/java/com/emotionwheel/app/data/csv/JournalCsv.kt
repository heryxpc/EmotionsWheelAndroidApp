package com.emotionwheel.app.data.csv

import com.emotionwheel.app.data.JournalEntry
import com.emotionwheel.app.data.catalog.EmotionCatalog
import com.emotionwheel.app.data.local.JournalEntryEntity
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Reads and writes the `fecha,emoción,evento` format of the hand-kept journal, so a
 * file exported here opens in the same spreadsheet the user already had.
 *
 * The reader is deliberately forgiving because the original file is: it tolerates
 * doubled slashes in dates, several emotions in one cell, stray asterisks and
 * quoted fields containing commas and newlines.
 */
object JournalCsv {

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val HEADER = listOf("fecha", "emoción", "evento")

    data class ImportResult(val imported: Int, val skipped: Int)

    // ---------------------------------------------------------------- export

    fun export(entries: List<JournalEntry>, out: OutputStream) {
        out.bufferedWriter().use { writer ->
            writer.write("﻿") // BOM, so Excel opens the accents correctly
            writer.write(HEADER.joinToString(","))
            writer.newLine()
            entries.sortedBy { it.date }.forEach { entry ->
                writer.write(
                    listOf(
                        entry.date.format(DATE_FORMAT),
                        entry.labels.joinToString(" / "),
                        entry.situation,
                    ).joinToString(",", transform = ::quote)
                )
                writer.newLine()
            }
        }
    }

    private fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    // ---------------------------------------------------------------- import

    /**
     * Parses [input] into entities ready to insert. Ids are derived from the content
     * exactly the way tools/build_seed.py derives them, so importing the same file
     * twice — or importing the original CSV after seeding — inserts nothing new.
     */
    fun parse(input: InputStream, catalog: EmotionCatalog): List<JournalEntryEntity> {
        val text = input.bufferedReader().use { it.readText() }.removePrefix("﻿")
        val rows = parseRows(text)
        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map { normalizeHeader(it) }
        val body = if (header.take(3) == HEADER.map(::normalizeHeader)) rows.drop(1) else rows

        val now = System.currentTimeMillis()
        return body.mapNotNull { row -> toEntity(row, catalog, now) }
    }

    private fun normalizeHeader(value: String) = EmotionCatalog.normalize(value)

    private fun toEntity(
        row: List<String>,
        catalog: EmotionCatalog,
        now: Long,
    ): JournalEntryEntity? {
        if (row.size < 3) return null
        val date = parseDate(row[0]) ?: return null
        val situation = clean(row[2])
        val (emotionIds, custom) = parseEmotions(row[1], catalog)
        if (emotionIds.isEmpty() && custom == null) return null

        return JournalEntryEntity(
            id = stableId(date, emotionIds.joinToString(",") + (custom ?: ""), situation),
            dateEpochDay = date.toEpochDay(),
            emotionIds = emotionIds,
            customEmotion = custom,
            situation = situation,
            createdAt = now,
            updatedAt = now,
            dirty = true,
        )
    }

    private fun clean(value: String): String =
        value.replace("*", " ").replace(Regex("\\s+"), " ").trim()

    private fun parseDate(raw: String): LocalDate? {
        val normalized = clean(raw).replace(Regex("/{2,}"), "/")
        return runCatching { LocalDate.parse(normalized, DATE_FORMAT) }.getOrNull()
    }

    private fun parseEmotions(
        raw: String,
        catalog: EmotionCatalog,
    ): Pair<List<String>, String?> {
        val ids = mutableListOf<String>()
        val unmatched = mutableListOf<String>()
        raw.split('/', ',').map(::clean).filter { it.isNotEmpty() }.forEach { part ->
            val emotion = catalog.findByLabel(part)
            when {
                emotion == null -> unmatched += part.lowercase()
                emotion.id !in ids -> ids += emotion.id
            }
        }
        return ids to unmatched.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /**
     * What makes two entries "the same entry" for the purposes of importing.
     *
     * Ids alone are not enough: entries seeded from the original CSV carry
     * content-derived ids, but entries written in the app carry a random UUID. Export
     * one of those and import it back and the ids will not match, so an entry the user
     * already has would come back as a second copy. Comparing content catches both.
     */
    fun contentKey(
        dateEpochDay: Long,
        emotionIds: List<String>,
        customEmotion: String?,
        situation: String,
    ): String = listOf(
        dateEpochDay.toString(),
        emotionIds.sorted().joinToString(","),
        clean(customEmotion.orEmpty()).lowercase(),
        clean(situation).lowercase(),
    ).joinToString("|")

    fun contentKey(entry: JournalEntryEntity): String =
        contentKey(entry.dateEpochDay, entry.emotionIds, entry.customEmotion, entry.situation)

    /** Same recipe as tools/build_seed.py: SHA-1 of the content, shaped like a UUID. */
    private fun stableId(date: LocalDate, emotions: String, situation: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$date|$emotions|$situation".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return buildString {
            append(digest, 0, 8); append('-')
            append(digest, 8, 12); append('-')
            append(digest, 12, 16); append('-')
            append(digest, 16, 20); append('-')
            append(digest, 20, 32)
        }
    }

    // ------------------------------------------------------------ raw parsing

    /** Minimal RFC 4180 reader: handles quoted fields with commas and newlines. */
    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = mutableListOf()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                !inQuotes && char == ',' -> endField()
                !inQuotes && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    endRow()
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
