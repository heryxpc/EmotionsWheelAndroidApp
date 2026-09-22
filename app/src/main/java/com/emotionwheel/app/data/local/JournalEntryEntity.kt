package com.emotionwheel.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One line of the journal: a day, one or more emotions, and what happened.
 *
 * [emotionIds] holds wheel emotions; [customEmotion] holds anything typed by hand
 * that the wheel does not name (the source CSV has "vergüenza", "pena", "lástima"
 * and "indiferencia"). At least one of the two is always present.
 */
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    /** Stored as epoch day so ordering and range queries stay simple in SQL. */
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "emotion_ids") val emotionIds: List<String>,
    @ColumnInfo(name = "custom_emotion") val customEmotion: String?,
    val situation: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** True while the entry still has to be pushed to the cloud backup. */
    val dirty: Boolean = true,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
}
