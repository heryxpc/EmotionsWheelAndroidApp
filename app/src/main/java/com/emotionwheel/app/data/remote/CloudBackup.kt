package com.emotionwheel.app.data.remote

import android.content.Context
import android.util.Log
import com.emotionwheel.app.BuildConfig
import com.emotionwheel.app.data.SettingsStore
import com.emotionwheel.app.data.local.JournalEntryDao
import com.emotionwheel.app.data.local.JournalEntryEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Optional backup of the journal to Firestore.
 *
 * The app is fully usable without it: when `google-services.json` is absent the
 * Google Services plugin is never applied, [BuildConfig.FIREBASE_CONFIGURED] is false,
 * and [create] hands back a no-op that reports itself as unavailable.
 */
interface CloudBackup {

    val available: Boolean

    val lastSyncAt: Flow<Long?>

    val pendingCount: Flow<Int>

    /** Pushes everything still marked dirty, then pulls anything newer from the cloud. */
    suspend fun sync(context: Context): Result<SyncReport>

    data class SyncReport(val uploaded: Int, val downloaded: Int)

    companion object {
        fun create(dao: JournalEntryDao, settings: SettingsStore): CloudBackup =
            if (BuildConfig.FIREBASE_CONFIGURED) {
                FirestoreBackup(dao, settings)
            } else {
                UnavailableBackup(dao, settings)
            }
    }
}

/** What the app uses until someone drops in a Firebase configuration. */
private class UnavailableBackup(
    dao: JournalEntryDao,
    settings: SettingsStore,
) : CloudBackup {
    override val available = false
    override val lastSyncAt = settings.lastSyncAt
    override val pendingCount = dao.observeDirtyCount()
    override suspend fun sync(context: Context) =
        Result.failure<CloudBackup.SyncReport>(IllegalStateException("Firebase not configured"))
}

/**
 * Anonymous auth plus one document per entry under `users/{uid}/journalEntries`.
 * Conflicts resolve by `updatedAt`, last write wins — enough for a single-user
 * journal, and it keeps the whole thing to one round trip in each direction.
 */
private class FirestoreBackup(
    private val dao: JournalEntryDao,
    private val settings: SettingsStore,
) : CloudBackup {

    override val available = true
    override val lastSyncAt = settings.lastSyncAt
    override val pendingCount = dao.observeDirtyCount()

    override suspend fun sync(context: Context): Result<CloudBackup.SyncReport> = runCatching {
        FirebaseApp.initializeApp(context)
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        checkNotNull(user) { "Anonymous sign-in returned no user" }

        val collection = FirebaseFirestore.getInstance()
            .collection(USERS)
            .document(user.uid)
            .collection(ENTRIES)

        val pending = dao.findDirty()
        pending.chunked(BATCH_SIZE).forEach { chunk ->
            val batch = FirebaseFirestore.getInstance().batch()
            chunk.forEach { entry -> batch.set(collection.document(entry.id), entry.toMap()) }
            batch.commit().await()
        }
        if (pending.isNotEmpty()) dao.markSynced(pending.map { it.id })

        val remote = collection.get().await().documents.mapNotNull { it.toEntity() }
        var downloaded = 0
        remote.forEach { candidate ->
            val local = dao.findById(candidate.id)
            if (local == null || candidate.updatedAt > local.updatedAt) {
                dao.upsert(candidate.copy(dirty = false))
                downloaded++
            }
        }

        settings.setLastSyncAt(System.currentTimeMillis())
        CloudBackup.SyncReport(uploaded = pending.size, downloaded = downloaded)
    }.onFailure { Log.e(TAG, "Sync failed", it) }

    private fun JournalEntryEntity.toMap(): Map<String, Any?> = mapOf(
        "date" to dateEpochDay,
        "emotionIds" to emotionIds,
        "customEmotion" to customEmotion,
        "situation" to situation,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toEntity(): JournalEntryEntity? {
        val date = getLong("date") ?: return null
        val updatedAt = getLong("updatedAt") ?: return null
        @Suppress("UNCHECKED_CAST")
        val emotionIds = (get("emotionIds") as? List<String>).orEmpty()
        return JournalEntryEntity(
            id = id,
            dateEpochDay = date,
            emotionIds = emotionIds,
            customEmotion = getString("customEmotion"),
            situation = getString("situation").orEmpty(),
            createdAt = getLong("createdAt") ?: updatedAt,
            updatedAt = updatedAt,
            dirty = false,
        )
    }

    private companion object {
        const val TAG = "FirestoreBackup"
        const val USERS = "users"
        const val ENTRIES = "journalEntries"
        const val BATCH_SIZE = 400 // Firestore caps a batch at 500 writes.
    }
}
