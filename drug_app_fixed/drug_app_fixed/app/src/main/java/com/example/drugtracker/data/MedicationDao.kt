package com.example.drugtracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MedicationRecord): Long

    @Delete
    suspend fun delete(record: MedicationRecord)

    @Query("SELECT * FROM medication_records ORDER BY takenAtMs DESC")
    fun getAllRecords(): LiveData<List<MedicationRecord>>

    @Query("SELECT * FROM medication_records WHERE takenAtMs >= :startMs ORDER BY takenAtMs ASC")
    suspend fun getRecordsSince(startMs: Long): List<MedicationRecord>

    @Query("SELECT * FROM medication_records WHERE drugName = :name ORDER BY takenAtMs ASC")
    suspend fun getRecordsForDrug(name: String): List<MedicationRecord>

    @Query("SELECT COUNT(*) FROM medication_records WHERE drugName = :name AND takenAtMs >= :dayStartMs")
    suspend fun countTodayRecordsForDrug(name: String, dayStartMs: Long): Int

    @Query("SELECT * FROM medication_records ORDER BY takenAtMs DESC")
    suspend fun getAllRecordsSync(): List<MedicationRecord>
}