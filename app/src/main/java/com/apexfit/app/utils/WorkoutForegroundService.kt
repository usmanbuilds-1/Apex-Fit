package com.apexfit.app.utils

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class WorkoutForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionName = intent?.getStringExtra("sessionName") ?: "Workout"
        val elapsedMinutes = intent?.getIntExtra("elapsedMinutes", 0) ?: 0
        val notification = WorkoutActiveNotification.buildNotification(this, sessionName, elapsedMinutes)
        startForeground(WorkoutActiveNotification.NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        fun start(context: Context, sessionName: String, elapsedMinutes: Int = 0) {
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                putExtra("sessionName", sessionName)
                putExtra("elapsedMinutes", elapsedMinutes)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutForegroundService::class.java))
        }
    }
}

