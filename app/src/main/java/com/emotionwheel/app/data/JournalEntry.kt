package com.emotionwheel.app.data

import com.emotionwheel.app.data.catalog.Emotion
import com.emotionwheel.app.data.catalog.EmotionFamily
import java.time.LocalDate

/**
 * A journal entry with its emotions already resolved against the catalog — this is
 * what the UI renders, as opposed to the id-only row stored in Room.
 */
data class JournalEntry(
    val id: String,
    val date: LocalDate,
    val emotions: List<Emotion>,
    val customEmotion: String?,
    val situation: String,
    val createdAt: Long,
    val updatedAt: Long,
    val dirty: Boolean,
) {
    /** Families present in this entry, used to color the entry in the journal list. */
    val families: List<EmotionFamily> get() = emotions.map { it.family }.distinct()

    /** Every emotion name of the entry, wheel ones first, then the free-text one. */
    val labels: List<String>
        get() = emotions.map { it.label } + listOfNotNull(customEmotion?.takeIf { it.isNotBlank() })
}
