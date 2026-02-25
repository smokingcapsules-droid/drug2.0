package com.example.drugtracker.data

data class DrugInfo(
    val id: Long = 0,
    val name: String,
    val halfLifeHours: Double,
    val tmaxHours: Double,
    val unit: String = "mg",
    val isLipophilic: Boolean = false,
    val isCritical: Boolean = false,
    val isCustom: Boolean = false,
    val defaultDose: Double? = null,
    val notes: String = ""
)