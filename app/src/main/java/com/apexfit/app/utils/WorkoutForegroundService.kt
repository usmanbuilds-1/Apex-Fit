package com.apexfit.app.utils

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class WorkoutForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // AUDIT FIX (BUG-V4-006): null intent = system sticky restart with no
        // active session. Stop immediately rather than pinning a phantom notification.
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val sessionName = intent.getStringExtra("sessionName") ?: "Workout"
        val startTime = intent.getLongExtra("startTime", System.currentTimeMillis())
        val notification = WorkoutActiveNotification.buildNotification(this, sessionName, startTime)
        startForeground(WorkoutActiveNotification.NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        WorkoutActiveNotification.dismiss(this)
    }

    companion object {
        fun start(context: Context, sessionName: String, startTime: Long = System.currentTimeMillis()) {
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                putExtra("sessionName", sessionName)
                putExtra("startTime", startTime)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutForegroundService::class.java))
            WorkoutActiveNotification.dismiss(context)
        }
    }
}

