package com.example.drugtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_drugs")
data class CustomDrug(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val halfLifeHours: Double,
    val tmaxHours: Double,
    val unit: String = "mg",
    val isLipophilic: Boolean = false,
    val isCritical: Boolean = false,
    val defaultDose: Double? = null,
    val notes: String = ""
) {
    fun toDrugInfo(): DrugInfo {
        return DrugInfo(
            id = id,
            name = name,
            halfLifeHours = halfLifeHours,
            tmaxHours = tmaxHours,
            unit = unit,
            isLipophilic = isLipophilic,
            isCritical = isCritical,
            isCustom = true,
            defaultDose = defaultDose,
            notes = notes
        )
    }
}