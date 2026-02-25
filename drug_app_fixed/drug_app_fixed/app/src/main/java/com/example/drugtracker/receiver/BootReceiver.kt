package com.example.drugtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.drugtracker.logic.ReminderEngine

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderEngine.restoreAllReminders(context)
        }
    }
}