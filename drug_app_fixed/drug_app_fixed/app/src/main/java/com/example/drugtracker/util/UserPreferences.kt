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

    fun getHeightCm(context: Context): Double =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("height_cm", 165f).toDouble()

    fun setHeightCm(context: Context, height: Double) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("height_cm", height.toFloat()).apply()

    fun getBodyFatPercent(context: Context): Double =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("body_fat_percent", 25f).toDouble()

    fun setBodyFatPercent(context: Context, bodyFat: Double) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("body_fat_percent", bodyFat.toFloat()).apply()

    // 治疗窗上下限（单位：浓度百分比或实际浓度？这里我们使用百分比，以便与图表纵轴一致）
    fun getTherapyWindowLow(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("therapy_window_low", 0f)

    fun setTherapyWindowLow(context: Context, low: Float) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("therapy_window_low", low).apply()

    fun getTherapyWindowHigh(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("therapy_window_high", 0f)

    fun setTherapyWindowHigh(context: Context, high: Float) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("therapy_window_high", high).apply()

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
