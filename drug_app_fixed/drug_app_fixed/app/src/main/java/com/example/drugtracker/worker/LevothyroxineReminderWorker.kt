package com.example.drugtracker.worker

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.drugtracker.data.AppDatabase
import com.example.drugtracker.data.MedicationRepository
import com.example.drugtracker.util.showNotification

class LevothyroxineReminderWorker(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = MedicationRepository(db)
        val hasTaken = repository.hasTodayRecord("优甲乐（左甲状腺素）")

        if (!hasTaken) {
            showNotification(
                context = applicationContext,
                channelId = "critical_reminder",
                title = "🔴 优甲乐提醒",
                body = "今日优甲乐尚未记录服药，请空腹服用",
                drugName = "优甲乐（左甲状腺素）",
                priority = NotificationCompat.PRIORITY_HIGH
            )
        }

        // 调度明天的提醒
        com.example.drugtracker.logic.ReminderEngine.scheduleLevothyroxineDaily(applicationContext)
        return Result.success()
    }
}