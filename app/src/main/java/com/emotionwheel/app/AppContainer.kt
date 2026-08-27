package com.emotionwheel.app

import android.content.Context
import com.emotionwheel.app.data.JournalRepository
import com.emotionwheel.app.data.SettingsStore
import com.emotionwheel.app.data.catalog.EmotionCatalog
import com.emotionwheel.app.data.local.AppDatabase
import com.emotionwheel.app.data.remote.CloudBackup
import com.emotionwheel.app.data.seed.SeedImporter

/**
 * Hand-rolled dependency container. The graph is six objects deep, so a DI framework
 * would cost more in build time and indirection than it saves.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val catalog: EmotionCatalog by lazy { EmotionCatalog.get(appContext) }

    val repository: JournalRepository by lazy {
        JournalRepository(AppDatabase.get(appContext).journalEntryDao(), catalog)
    }

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val seedImporter: SeedImporter by lazy { SeedImporter(appContext, repository) }

    val cloudBackup: CloudBackup by lazy {
        CloudBackup.create(
            dao = AppDatabase.get(appContext).journalEntryDao(),
            settings = settings,
        )
    }
}
