package com.example.drugtracker.util

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CrashLogger {
    private const val LOG_FILE = "crash_log.txt"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveLog(context, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun log(tag: String, e: Exception) {
        android.util.Log.e("DrugTracker", tag, e)
    }

    private fun saveLog(context: Context, throwable: Throwable) {
        try {
            val logFile = File(context.getExternalFilesDir(null), LOG_FILE)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            val timestamp = sdf.format(Date())
            val log = buildString {
                appendLine("=== 崩溃时间：$timestamp ===")
                appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android：${Build.VERSION.RELEASE}")
                appendLine("异常：${throwable::class.java.name}")
                appendLine("信息：${throwable.message}")
                appendLine("堆栈：")
                throwable.stackTrace.forEach { appendLine("  at $it") }
                appendLine()
            }
            logFile.appendText(log)
        } catch (e: Exception) {
            // 日志写入失败，忽略
        }
    }

    fun readLog(context: Context): String {
        val logFile = File(context.getExternalFilesDir(null), LOG_FILE)
        return if (logFile.exists()) logFile.readText() else "暂无崩溃日志"
    }

    fun clearLog(context: Context) {
        File(context.getExternalFilesDir(null), LOG_FILE).delete()
    }

    fun getLogSize(context: Context): Long {
        val logFile = File(context.getExternalFilesDir(null), LOG_FILE)
        return if (logFile.exists()) logFile.length() else 0
    }
}