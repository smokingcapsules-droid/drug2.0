package com.example.drugtracker.logic

import com.example.drugtracker.data.DrugInfo
import com.example.drugtracker.data.MedicationRecord
import kotlin.math.pow

object PeakPlanner {

    data class DosePlan(
        val drug: DrugInfo,
        val recommendedDose: Double,
        val takeAtMs: Long,
        val peakAtMs: Long,
        val expectedConcentrationAtTarget: Double
    )

    fun planForTargetConcentration(
        drug: DrugInfo,
        targetTimeMs: Long,
        targetConcentrationMg: Double,
        weightKg: Double,
        existingRecords: List<MedicationRecord>
    ): DosePlan? {
        val halfLife = DrugCalculator.adjustedHalfLife(drug, weightKg)
        val existingAmount = DrugCalculator.totalConcentrationMg(
            existingRecords, drug, weightKg, targetTimeMs
        )
        val neededAmount = maxOf(0.0, targetConcentrationMg - existingAmount)
        if (neededAmount <= 0) return null
        val doseNeeded = neededAmount / 0.5.pow(drug.tmaxHours / halfLife)
        val takeAtMs = targetTimeMs - (drug.tmaxHours * 3_600_000L).toLong()
        return DosePlan(drug, doseNeeded, takeAtMs, targetTimeMs, neededAmount)
    }

    fun calculateCombinedEffect(plans: List<DosePlan>, atTimeMs: Long): Double {
        var total = 0.0
        for (plan in plans) {
            val h = (atTimeMs - plan.takeAtMs) / 3_600_000.0
            total += plan.expectedConcentrationAtTarget * 0.5.pow(h / plan.drug.halfLifeHours)
        }
        return total
    }
}
