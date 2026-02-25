package com.example.drugtracker.util

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    private val beijingTimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    fun formatDateTime(ms: Long): String {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        sdf.timeZone = beijingTimeZone
        return sdf.format(Date(ms))
    }

    fun formatTime(ms: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
        sdf.timeZone = beijingTimeZone
        return sdf.format(Date(ms))
    }

    fun formatDate(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        sdf.timeZone = beijingTimeZone
        return sdf.format(Date(ms))
    }

    fun getTodayStartMs(): Long {
        val cal = Calendar.getInstance(beijingTimeZone)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getDayStartMs(dayOffset: Int): Long {
        val cal = Calendar.getInstance(beijingTimeZone)
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}