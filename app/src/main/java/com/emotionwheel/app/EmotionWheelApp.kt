package com.emotionwheel.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EmotionWheelApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Load the journal the user kept by hand before this app existed. Runs once,
        // off the main thread, and never blocks the first frame.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val inserted = container.seedImporter.seedIfNeeded()
            if (inserted > 0) Log.i(TAG, "Seeded $inserted journal entries")
        }
    }

    private companion object {
        const val TAG = "EmotionWheelApp"
    }
}
