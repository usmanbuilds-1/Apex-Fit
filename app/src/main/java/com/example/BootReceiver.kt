package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.utils.CoachingScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoachingScheduler.schedule6AmDailyCoachingTask(context)
        }
    }
}
