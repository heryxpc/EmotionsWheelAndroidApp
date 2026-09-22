package com.emotionwheel.app.data

import com.emotionwheel.app.data.catalog.EmotionCatalog
import com.emotionwheel.app.data.csv.JournalCsv
import com.emotionwheel.app.data.local.JournalEntryDao
import com.emotionwheel.app.data.local.JournalEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * Single entry point to the journal. Writes always land locally first and are
 * flagged [JournalEntryEntity.dirty] so the optional cloud backup can pick them up
 * later; nothing in the app blocks on the network.
 */
class JournalRepository(
    private val dao: JournalEntryDao,
    private val catalog: EmotionCatalog,
) {

    fun observeEntries(): Flow<List<JournalEntry>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    fun observePendingSyncCount(): Flow<Int> = dao.observeDirtyCount()

    suspend fun findById(id: String): JournalEntry? = dao.findById(id)?.let(::toDomain)

    /**
     * Creates a new entry or updates an existing one when [id] is given.
     * Returns the id so callers can navigate back to it.
     */
    suspend fun save(
        id: String? = null,
        date: LocalDate,
        emotionIds: List<String>,
        customEmotion: String?,
        situation: String,
    ): String {
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.findById(it) }
        val entry = JournalEntryEntity(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            dateEpochDay = date.toEpochDay(),
            emotionIds = emotionIds.filter { catalog[it] != null }.distinct(),
            customEmotion = customEmotion?.trim()?.takeIf { it.isNotEmpty() },
            situation = situation.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            dirty = true,
        )
        dao.upsert(entry)
        return entry.id
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    /** Re-inserts an entry the user deleted by mistake, keeping its original id. */
    suspend fun restore(entry: JournalEntry) {
        dao.upsert(
            JournalEntryEntity(
                id = entry.id,
                dateEpochDay = entry.date.toEpochDay(),
                emotionIds = entry.emotions.map { it.id },
                customEmotion = entry.customEmotion,
                situation = entry.situation,
                createdAt = entry.createdAt,
                updatedAt = System.currentTimeMillis(),
                dirty = true,
            )
        )
    }

    /**
     * Adds entries that are not there yet, matching on content rather than on id so
     * that re-importing a file the user exported from this app does not duplicate the
     * entries they wrote here. Returns how many were actually inserted.
     */
    suspend fun insertNew(entries: List<JournalEntryEntity>): Int {
        val existing = dao.all().mapTo(mutableSetOf()) { JournalCsv.contentKey(it) }
        val fresh = entries.filter { JournalCsv.contentKey(it) !in existing }
        return dao.insertIgnoringExisting(fresh).count { it != -1L }
    }

    suspend fun count(): Int = dao.count()

    private fun toDomain(row: JournalEntryEntity) = JournalEntry(
        id = row.id,
        date = row.date,
        emotions = row.emotionIds.mapNotNull { catalog[it] },
        customEmotion = row.customEmotion,
        situation = row.situation,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
        dirty = row.dirty,
    )
}
