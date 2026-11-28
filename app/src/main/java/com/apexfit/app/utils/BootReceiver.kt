package com.apexfit.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && !LocalResetCoordinator.pending(context)) {
            CoachingScheduler.schedule6AmDailyCoachingTask(context)
        }
    }
}
