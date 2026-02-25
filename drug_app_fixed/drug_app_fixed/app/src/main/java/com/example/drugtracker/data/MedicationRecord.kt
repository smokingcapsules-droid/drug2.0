package com.example.drugtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_records")
data class MedicationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val drugName: String,
    val doseMg: Double,
    val unit: String = "mg",
    val takenAtMs: Long,
    val recordType: String = "prn",
    val notes: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)