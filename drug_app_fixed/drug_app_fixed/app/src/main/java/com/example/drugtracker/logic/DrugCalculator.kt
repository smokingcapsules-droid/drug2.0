package com.example.drugtracker.logic

import com.example.drugtracker.data.DrugInfo
import com.example.drugtracker.data.MedicationRecord
import kotlin.math.*

object DrugCalculator {

    fun adjustedHalfLife(drug: DrugInfo, weightKg: Double): Double {
        return if (drug.isLipophilic) drug.halfLifeHours * (weightKg / 70.0).pow(0.3)
        else drug.halfLifeHours
    }

    // 含吸收相的浓度（修复：服药后立即有浓度，不从0开始）
    fun concentrationMgWithAbsorption(
        doseMg: Double,
        halfLifeHours: Double,
        tmaxHours: Double,
        hoursSinceDose: Double
    ): Double {
        if (hoursSinceDose < 0) return 0.0
        return if (hoursSinceDose <= tmaxHours) {
            // 修复：用线性上升代替sin，避免起点为0
            // 服药后立即有少量吸收，线性爬升到峰值
            doseMg * (hoursSinceDose / tmaxHours)
        } else {
            val hoursAfterPeak = hoursSinceDose - tmaxHours
            doseMg * 0.5.pow(hoursAfterPeak / halfLifeHours)
        }
    }

    fun concentrationMgAfterDose(doseMg: Double, halfLifeHours: Double, hoursSinceDose: Double): Double {
        if (hoursSinceDose < 0) return 0.0
        return doseMg * 0.5.pow(hoursSinceDose / halfLifeHours)
    }

    fun totalRemainingMg(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double {
        val halfLife = adjustedHalfLife(drug, weightKg)
        return records
            .filter { it.drugName == drug.name && it.takenAtMs <= atTimeMs }
            .sumOf { record ->
                val hoursSince = (atTimeMs - record.takenAtMs) / 3_600_000.0
                concentrationMgWithAbsorption(record.doseMg, halfLife, drug.tmaxHours, hoursSince)
            }
    }

    // 剂量当量%：100% = 体内有一个标准剂量
    fun totalConcentrationPercent(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double {
        val remainingMg = totalRemainingMg(records, drug, weightKg, atTimeMs)
        val standardDose = getStandardDose(drug, records)
        return (remainingMg / standardDose) * 100.0
    }

    fun totalConcentrationMg(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double = totalRemainingMg(records, drug, weightKg, atTimeMs)

    private fun getStandardDose(drug: DrugInfo, records: List<MedicationRecord>): Double {
        if (drug.defaultDose != null && drug.defaultDose > 0) return drug.defaultDose
        return records.filter { it.drugName == drug.name }
            .groupBy { it.doseMg }
            .maxByOrNull { it.value.size }?.key ?: 1.0
    }

    data class DoseAdvice(
        val drugName: String,
        val remainingMg: Double,
        val standardDose: Double,
        val suggestedDose: Double,
        val percentOfStandard: Double,
        val isAccumulated: Boolean,
        val unit: String
    )

    fun getDoseAdvice(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        nowMs: Long
    ): DoseAdvice {
        val remaining = totalRemainingMg(records, drug, weightKg, nowMs)
        val standardDose = getStandardDose(drug, records)
        val percent = (remaining / standardDose) * 100.0
        val suggested = maxOf(0.0, standardDose - remaining)
        return DoseAdvice(
            drugName = drug.name,
            remainingMg = remaining,
            standardDose = standardDose,
            suggestedDose = suggested,
            percentOfStandard = percent,
            isAccumulated = percent > 110.0,
            unit = drug.unit
        )
    }

    // 修复：getActiveDrugs 改为只要72h内有记录就显示，不用卡5%门槛
    fun getActiveDrugs(
        records: List<MedicationRecord>,
        allDrugs: List<DrugInfo>,
        weightKg: Double,
        atTimeMs: Long
    ): List<Pair<DrugInfo, Double>> {
        val cutoff = atTimeMs - 72 * 3_600_000L
        return allDrugs.mapNotNull { drug ->
            val recentRecords = records.filter {
                it.drugName == drug.name && it.takenAtMs >= cutoff
            }
            if (recentRecords.isEmpty()) return@mapNotNull null
            val pct = totalConcentrationPercent(records, drug, weightKg, atTimeMs)
            // 只要有记录就显示（刚服药时也能看到），完全代谢后（<1%）才隐藏
            if (pct >= 1.0 || recentRecords.any { (atTimeMs - it.takenAtMs) < drug.tmaxHours * 3_600_000L * 2 }) {
                drug to pct
            } else null
        }.sortedByDescending { it.second }
    }

    fun timeUntilBelowThreshold(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        thresholdPercent: Double,
        fromTimeMs: Long
    ): Long? {
        if (totalConcentrationPercent(records, drug, weightKg, fromTimeMs) < thresholdPercent) return null
        val stepMs = 15 * 60 * 1000L
        var scanMs = fromTimeMs
        repeat(200) {
            scanMs += stepMs
            if (totalConcentrationPercent(records, drug, weightKg, scanMs) < thresholdPercent) return scanMs
        }
        return null
    }
}
