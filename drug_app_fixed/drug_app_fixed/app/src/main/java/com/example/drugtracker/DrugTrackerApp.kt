package com.example.drugtracker

import android.app.Application
import com.example.drugtracker.util.CrashLogger

class DrugTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
    }
}