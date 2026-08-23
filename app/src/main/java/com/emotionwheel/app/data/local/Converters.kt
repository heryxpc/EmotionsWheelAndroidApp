package com.emotionwheel.app.data.local

import androidx.room.TypeConverter

/**
 * Emotion ids are slugs of `[a-z]+` (see tools/build_catalog.py), so a comma is a
 * safe separator and avoids a join table for what is at most a handful of ids.
 */
class Converters {

    @TypeConverter
    fun emotionIdsToString(ids: List<String>): String = ids.joinToString(SEPARATOR)

    @TypeConverter
    fun stringToEmotionIds(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = ","
    }
}
