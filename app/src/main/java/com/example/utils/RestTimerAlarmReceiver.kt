package com.example.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.text.SimpleDateFormat
import java.util.Locale

class RestTimerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "rest_timer_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_EXERCISE_NAME = "exercise_name"
        private const val TAG = "RestTimerAlarm"

        /**
         * Schedule a one-time alarm for the rest timer completion.
         * Falls back to inexact alarm if SCHEDULE_EXACT_ALARM permission
         * is not granted (Android 12+). A late notification is acceptable;
         * no notification is not.
         */
        fun scheduleAlarm(context: Context, triggerAtMillis: Long, exerciseName: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, RestTimerAlarmReceiver::class.java).apply {
                putExtra(EXTRA_EXERCISE_NAME, exerciseName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.i(TAG, "Scheduled EXACT alarm at $triggerAtMillis for '$exerciseName'")
                } else {
                    // Permission not granted — fall back to inexact alarm.
                    // May fire late during Doze but will still fire.
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.w(TAG, "Exact alarm permission not granted — using inexact alarm (may be delayed)")
                }
            } else {
                // Android 11 and below — setExactAndAllowWhileIdle doesn't need permission
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.i(TAG, "Scheduled exact alarm (pre-S) at $triggerAtMillis for '$exerciseName'")
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, RestTimerAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.i(TAG, "Cancelled pending rest timer alarm")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Rest timer alarm received — posting notification")
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Rest"

        createNotificationChannel(context)

        // Use the existing deep link scheme to return to the Train screen
        val deepLinkUri = Uri.parse("apexfit://screen/train")
        val launchIntent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            setClass(context, MainActivity::class.java)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest Complete")
            .setContentText("$exerciseName — back to it")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Vibrate — keep this; vibration is short and safe in onReceive
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }

        // NOTE: Do NOT call AudioService.playRestTimerComplete here.
        // The sound is configured on the notification channel itself (see createNotificationChannel),
        // which Android plays reliably without needing the process to stay alive.
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rest Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when rest timer completes"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
