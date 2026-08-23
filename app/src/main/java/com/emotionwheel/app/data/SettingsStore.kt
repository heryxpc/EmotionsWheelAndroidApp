package com.emotionwheel.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small user preferences that do not belong in the database. */
class SettingsStore(private val context: Context) {

    val cloudBackupEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[CLOUD_BACKUP_ENABLED] == true }

    /** Epoch millis of the last successful sync, or null when it never ran. */
    val lastSyncAt: Flow<Long?> =
        context.settingsDataStore.data.map { it[LAST_SYNC_AT] }

    suspend fun setCloudBackupEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[CLOUD_BACKUP_ENABLED] = enabled }
    }

    suspend fun setLastSyncAt(millis: Long) {
        context.settingsDataStore.edit { it[LAST_SYNC_AT] = millis }
    }

    private companion object {
        val CLOUD_BACKUP_ENABLED = booleanPreferencesKey("cloud_backup_enabled")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }
}
