package com.example.drugtracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.drugtracker.util.showNotification
import java.text.SimpleDateFormat
import java.util.*

class ReminderWorker(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val drugName = inputData.getString("drug_name") ?: return Result.failure()
        val dropAtMs = inputData.getLong("drop_at_ms", 0L)

        val sdf = SimpleDateFormat("HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val dropTimeStr = sdf.format(Date(dropAtMs))

        showNotification(
            context = applicationContext,
            channelId = "concentration_reminder",
            title = "💊 $drugName 浓度提醒",
            body = "预计在 $dropTimeStr 降至阈值，建议现在服药",
            drugName = drugName
        )

        return Result.success()
    }
}