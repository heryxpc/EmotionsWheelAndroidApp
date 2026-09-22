package com.emotionwheel.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    /** Newest first, and within a day the most recently written first. */
    @Query("SELECT * FROM journal_entries ORDER BY date_epoch_day DESC, created_at DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun findById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE dirty = 1")
    suspend fun findDirty(): List<JournalEntryEntity>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM journal_entries")
    suspend fun all(): List<JournalEntryEntity>

    @Upsert
    suspend fun upsert(entry: JournalEntryEntity)

    /** Used by seeding and CSV import, where existing entries must not be overwritten. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(entries: List<JournalEntryEntity>): List<Long>

    @Query("UPDATE journal_entries SET dirty = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteById(id: String)
}
