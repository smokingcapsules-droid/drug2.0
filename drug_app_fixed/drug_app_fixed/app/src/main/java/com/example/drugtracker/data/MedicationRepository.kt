package com.example.drugtracker.data

import java.util.*

class MedicationRepository(private val db: AppDatabase) {
    val allRecords = db.medicationDao().getAllRecords()
    val allCustomDrugs = db.customDrugDao().getAllCustomDrugs()

    suspend fun addRecord(record: MedicationRecord): Long {
        return db.medicationDao().insert(record)
    }

    suspend fun deleteRecord(record: MedicationRecord) {
        db.medicationDao().delete(record)
    }

    suspend fun getRecordsSince(startMs: Long): List<MedicationRecord> {
        return db.medicationDao().getRecordsSince(startMs)
    }

    suspend fun getRecordsForDrug(name: String): List<MedicationRecord> {
        return db.medicationDao().getRecordsForDrug(name)
    }

    suspend fun hasTodayRecord(drugName: String): Boolean {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return db.medicationDao().countTodayRecordsForDrug(drugName, cal.timeInMillis) > 0
    }

    suspend fun addCustomDrug(drug: CustomDrug) = db.customDrugDao().insert(drug)
    suspend fun updateCustomDrug(drug: CustomDrug) = db.customDrugDao().update(drug)
    suspend fun deleteCustomDrug(drug: CustomDrug) = db.customDrugDao().delete(drug)
    suspend fun getAllCustomDrugsSync() = db.customDrugDao().getAllCustomDrugsSync()
}