package com.example

import android.app.Application
import androidx.work.Configuration
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.example.data.DataStoreManager
import com.example.utils.SeedService

class ApexFitApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Seed exercises on first launch
        val app = this
        CoroutineScope(Dispatchers.IO).launch {
            val dataStore = DataStoreManager.getInstance(app)
            val isSeeded = dataStore.isExercisesSeededFlow.firstOrNull() ?: false
            if (!isSeeded) {
                SeedService.seed(app)
                dataStore.setExercisesSeeded(true)
            }
        }
    }
}
