package com.emotionwheel.app.data.seed

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.emotionwheel.app.data.JournalRepository
import com.emotionwheel.app.data.local.JournalEntryEntity
import com.emotionwheel.app.data.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Loads the journal the user kept by hand before this app existed, once.
 *
 * The asset is produced by tools/build_seed.py, which already normalized the dates
 * and split the rows that named several emotions. Ids there are deterministic, so a
 * second run would be a no-op even without the guard flag.
 */
class SeedImporter(
    private val context: Context,
    private val repository: JournalRepository,
) {

    @Serializable
    private data class SeedFile(val version: Int, val entries: List<SeedEntry>)

    @Serializable
    private data class SeedEntry(
        val id: String,
        val date: String,
        val emotionIds: List<String>,
        val customEmotion: String? = null,
        val situation: String,
    )

    /** Returns how many entries were inserted; 0 when seeding already ran. */
    suspend fun seedIfNeeded(): Int {
        val alreadySeeded = context.settingsDataStore.data.first()[SEEDED_KEY] == true
        if (alreadySeeded) return 0

        val inserted = runCatching { importAsset() }
            .onFailure { Log.e(TAG, "Could not seed the journal from $ASSET_NAME", it) }
            .getOrDefault(0)

        context.settingsDataStore.edit { it[SEEDED_KEY] = true }
        return inserted
    }

    private suspend fun importAsset(): Int {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val seed = json.decodeFromString<SeedFile>(text)
        val now = System.currentTimeMillis()

        val rows = seed.entries.map { entry ->
            JournalEntryEntity(
                id = entry.id,
                dateEpochDay = LocalDate.parse(entry.date).toEpochDay(),
                emotionIds = entry.emotionIds,
                customEmotion = entry.customEmotion,
                situation = entry.situation,
                createdAt = now,
                updatedAt = now,
                dirty = true,
            )
        }
        return repository.insertNew(rows)
    }

    private companion object {
        const val TAG = "SeedImporter"
        const val ASSET_NAME = "journal_seed.json"
        val SEEDED_KEY = booleanPreferencesKey("journal_seeded")
        val json = Json { ignoreUnknownKeys = true }
    }
}
