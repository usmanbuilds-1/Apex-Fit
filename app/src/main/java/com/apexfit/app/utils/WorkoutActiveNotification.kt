package com.apexfit.app.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.apexfit.app.MainActivity

object WorkoutActiveNotification {
    private const val CHANNEL_ID = "workout_active_channel"
    const val NOTIFICATION_ID = 9001

    fun createChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Active Workout",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while a workout session is in progress"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, sessionName: String, elapsedMinutes: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("apexfit://screen/train")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val iconRes = try {
            val r = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
            if (r != 0) r else android.R.drawable.ic_dialog_info
        } catch (e: Exception) { android.R.drawable.ic_dialog_info }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle("Workout in progress")
            .setContentText("$sessionName · ${elapsedMinutes}m")
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }
}
