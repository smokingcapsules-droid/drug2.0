package com.example.drugtracker.logic

import com.example.drugtracker.data.DrugInfo
import com.example.drugtracker.data.MedicationRecord
import kotlin.math.*

object DrugCalculator {

    fun adjustedHalfLife(drug: DrugInfo, weightKg: Double): Double {
        return if (drug.isLipophilic) drug.halfLifeHours * (weightKg / 70.0).pow(0.3)
        else drug.halfLifeHours
    }

    // ── 含吸收相的单次给药浓度 ────────────────────────────
    fun concentrationMgWithAbsorption(
        doseMg: Double,
        halfLifeHours: Double,
        tmaxHours: Double,
        hoursSinceDose: Double
    ): Double {
        if (hoursSinceDose < 0) return 0.0
        return if (hoursSinceDose <= tmaxHours) {
            doseMg * (hoursSinceDose / tmaxHours)  // 线性上升到峰值
        } else {
            val hoursAfterPeak = hoursSinceDose - tmaxHours
            doseMg * 0.5.pow(hoursAfterPeak / halfLifeHours)
        }
    }

    // ── 当前体内总残余量（mg）────────────────────────────
    fun totalRemainingMg(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double {
        val halfLife = adjustedHalfLife(drug, weightKg)
        return records
            .filter { it.drugName == drug.name && it.takenAtMs <= atTimeMs }
            .sumOf { r ->
                concentrationMgWithAbsorption(
                    r.doseMg, halfLife, drug.tmaxHours,
                    (atTimeMs - r.takenAtMs) / 3_600_000.0
                )
            }
    }

    // ── 统一百分比接口（图表用）──────────────────────────
    // 维持类：稳态达成度%（最高100%）
    // 按需类：残余量 ÷ 单次标准剂量%
    fun totalConcentrationPercent(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double {
        return if (drug.isCritical) {  // isCritical = 维持类
            getSteadyStatePercent(records, drug, weightKg, atTimeMs)
        } else {
            val remaining = totalRemainingMg(records, drug, weightKg, atTimeMs)
            val dose = getStandardDose(drug, records)
            (remaining / dose * 100.0).coerceAtMost(200.0)
        }
    }

    // ── 维持类：稳态达成度 ────────────────────────────────
    // 原理：每天服药，经过~5个半衰期（约5×halfLife小时）后达到稳态
    // 稳态浓度 ≈ 单次剂量 / (1 - 0.5^(dosing_interval/halfLife))
    // 稳态达成度 = 当前残余 / 稳态目标浓度 × 100%
    fun getSteadyStatePercent(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double {
        val halfLife = adjustedHalfLife(drug, weightKg)
        val dosingIntervalHours = 24.0  // 每日一次
        val dailyDose = getStandardDose(drug, records)

        // 稳态峰浓度理论值 = dose / (1 - e^(-ln2/halfLife * interval))
        // 简化：= dose / (1 - 0.5^(interval/halfLife))
        val accFactor = 1.0 / (1.0 - 0.5.pow(dosingIntervalHours / halfLife))
        val steadyStateMg = dailyDose * accFactor

        val currentMg = totalRemainingMg(records, drug, weightKg, atTimeMs)
        return (currentMg / steadyStateMg * 100.0).coerceAtMost(100.0)
    }

    // 兼容旧接口
    fun totalConcentrationMg(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double = totalRemainingMg(records, drug, weightKg, atTimeMs)

    private fun getStandardDose(drug: DrugInfo, records: List<MedicationRecord>): Double {
        if (drug.defaultDose != null && drug.defaultDose > 0) return drug.defaultDose
        return records.filter { it.drugName == drug.name }
            .groupBy { it.doseMg }.maxByOrNull { it.value.size }?.key ?: 1.0
    }

    // ── 按需类专用：剂量建议 ──────────────────────────────
    data class DoseAdvice(
        val drugName: String,
        val remainingMg: Double,
        val standardDose: Double,
        val suggestedDose: Double,      // 0 = 暂不需要
        val percentOfStandard: Double,
        val isAccumulated: Boolean,
        val isMaintenance: Boolean,     // true = 维持类，不显示补充建议
        val steadyStatePercent: Double, // 仅维持类有意义
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
        val isMaintenance = drug.isCritical

        return if (isMaintenance) {
            val ssPercent = getSteadyStatePercent(records, drug, weightKg, nowMs)
            DoseAdvice(
                drugName = drug.name,
                remainingMg = remaining,
                standardDose = standardDose,
                suggestedDose = standardDose,  // 维持类永远按处方剂量服用
                percentOfStandard = ssPercent,
                isAccumulated = false,
                isMaintenance = true,
                steadyStatePercent = ssPercent,
                unit = drug.unit
            )
        } else {
            val percent = (remaining / standardDose) * 100.0
            val suggested = maxOf(0.0, standardDose - remaining)
            DoseAdvice(
                drugName = drug.name,
                remainingMg = remaining,
                standardDose = standardDose,
                suggestedDose = suggested,
                percentOfStandard = percent,
                isAccumulated = percent > 110.0,
                isMaintenance = false,
                steadyStatePercent = 0.0,
                unit = drug.unit
            )
        }
    }

    // ── 活跃药物列表 ─────────────────────────────────────
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
            val justTaken = recentRecords.any {
                (atTimeMs - it.takenAtMs) < drug.tmaxHours * 2 * 3_600_000L
            }
            if (pct >= 1.0 || justTaken) drug to pct else null
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
