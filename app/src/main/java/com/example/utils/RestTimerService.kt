package com.example.utils

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.R

class RestTimerService : Service() {
    companion object {
        const val CHANNEL_ID = "rest_timer_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_EXERCISE = "exercise"

        private val _secondsRemaining = MutableStateFlow(0)
        val secondsRemaining: StateFlow<Int> = _secondsRemaining.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private var job: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val seconds = intent?.getIntExtra(EXTRA_SECONDS, 0) ?: 0
        val exercise = intent?.getStringExtra(EXTRA_EXERCISE) ?: "Rest"

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(exercise, seconds))

        job?.cancel()
        job = serviceScope.launch {
            _isRunning.value = true
            for (remaining in seconds downTo 0) {
                _secondsRemaining.value = remaining
                if (remaining > 0) {
                    val notification = buildNotification(exercise, remaining)
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification)
                    delay(1000)
                }
            }
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(exercise: String, seconds: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rest Timer")
            .setContentText("$exercise — ${seconds}s remaining")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Rest Timer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
