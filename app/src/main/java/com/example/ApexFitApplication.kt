package com.example

import android.app.Application
import androidx.work.Configuration
import android.util.Log

class ApexFitApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Initialization logic if needed
    }
}
