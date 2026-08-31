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

    fun buildNotification(context: Context, sessionName: String, elapsedMinutes: Int): android.app.Notification {
        val intent = Intent(context, com.apexfit.app.MainActivity::class.java).apply {
            data = android.net.Uri.parse("apexfit://screen/train")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val iconRes = try {
            val r = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
            if (r != 0) r else android.R.drawable.ic_dialog_info
        } catch (e: Exception) { android.R.drawable.ic_dialog_info }

        return androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle("Workout in progress")
            .setContentText("$sessionName · ${elapsedMinutes}m")
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun show(context: Context, sessionName: String, elapsedMinutes: Int) {
        if (!NotificationEngine.hasNotificationPermission(context)) return
        context.getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(context, sessionName, elapsedMinutes))
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }
}
