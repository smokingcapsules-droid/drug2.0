package com.example.drugtracker.logic

import android.content.Context
import androidx.work.*
import com.example.drugtracker.data.MedicationRepository
import com.example.drugtracker.data.PresetDrugs
import com.example.drugtracker.util.UserPreferences
import com.example.drugtracker.worker.LevothyroxineReminderWorker
import com.example.drugtracker.worker.ReminderWorker
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit

object ReminderEngine {

    suspend fun rescheduleForDrug(
        context: Context,
        drugName: String,
        repository: MedicationRepository
    ) {
        val drug = PresetDrugs.all.find { it.name == drugName }
            ?: repository.getAllCustomDrugsSync().find { it.name == drugName }
                ?.toDrugInfo() ?: return

        val weightKg = UserPreferences.getWeightKg(context)
        val bodyFat = UserPreferences.getBodyFatPercent(context)
        val threshold = UserPreferences.getReminderThreshold(context)
        val advanceMinutes = 60L

        val records = repository.getRecordsForDrug(drugName)
        if (records.isEmpty()) return

        val nowMs = System.currentTimeMillis()

        var dropTimeMs: Long? = null
        var scanMs = nowMs
        val stepMs = 15 * 60 * 1000L

        // 修改：使用新函数，传入 context 和 atTimeMs
        val currentConc = DrugCalculator.totalConcentrationPercent(records, drug, weightKg, bodyFat, nowMs)
        if (currentConc < threshold) return

        repeat(200) {
            scanMs += stepMs
            val conc = DrugCalculator.totalConcentrationPercent(records, drug, weightKg, bodyFat, scanMs)
            if (conc < threshold && dropTimeMs == null) {
                dropTimeMs = scanMs
            }
        }

        dropTimeMs?.let { dropMs ->
            val remindMs = dropMs - advanceMinutes * 60 * 1000L
            if (remindMs > nowMs) {
                scheduleOneTimeReminder(context, drugName, remindMs, dropMs)
            }
        }
    }

    private fun scheduleOneTimeReminder(
        context: Context,
        drugName: String,
        triggerAtMs: Long,
        dropAtMs: Long
    ) {
        val delay = triggerAtMs - System.currentTimeMillis()
        if (delay <= 0) return

        val data = workDataOf(
            "drug_name" to drugName,
            "drop_at_ms" to dropAtMs
        )

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("reminder_$drugName")
            .build()

        WorkManager.getInstance(context).cancelAllWorkByTag("reminder_$drugName")
        WorkManager.getInstance(context).enqueue(request)
    }

    fun scheduleLevothyroxineDaily(context: Context) {
        val hour = UserPreferences.getLevothyroxineReminderHour(context)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val delay = cal.timeInMillis - System.currentTimeMillis()

        val request = OneTimeWorkRequestBuilder<LevothyroxineReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("levothyroxine_daily")
            .build()

        WorkManager.getInstance(context).cancelAllWorkByTag("levothyroxine_daily")
        WorkManager.getInstance(context).enqueue(request)
    }

    fun restoreAllReminders(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val db = com.example.drugtracker.data.AppDatabase.getDatabase(context)
            val repository = MedicationRepository(db)
            val allDrugs = PresetDrugs.all.map { it.name } +
                repository.getAllCustomDrugsSync().map { it.name }
            allDrugs.distinct().forEach { drugName ->
                rescheduleForDrug(context, drugName, repository)
            }
            scheduleLevothyroxineDaily(context)
        }
    }
}
