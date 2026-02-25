package com.example.drugtracker.util

import android.content.Context

object UserPreferences {
    private const val PREFS_NAME = "drug_tracker_prefs"

    fun getWeightKg(context: Context): Double =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("weight_kg", 70f).toDouble()

    fun setWeightKg(context: Context, weight: Double) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("weight_kg", weight.toFloat()).apply()

    fun getReminderThreshold(context: Context): Double =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("reminder_threshold", 30f).toDouble()

    fun setReminderThreshold(context: Context, threshold: Double) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("reminder_threshold", threshold.toFloat()).apply()

    fun getLevothyroxineReminderHour(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("levo_reminder_hour", 7)

    fun setLevothyroxineReminderHour(context: Context, hour: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt("levo_reminder_hour", hour).apply()

    fun isDarkTheme(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("dark_theme", false)

    fun setDarkTheme(context: Context, dark: Boolean) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("dark_theme", dark).apply()
}