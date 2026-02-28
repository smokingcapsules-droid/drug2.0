package com.example.drugtracker.logic

import com.example.drugtracker.data.DrugInfo
import com.example.drugtracker.data.MedicationRecord
import kotlin.math.*

object DrugCalculator {

    // 体重修正半衰期（仅脂溶性药物）
    fun adjustedHalfLife(drug: DrugInfo, weightKg: Double): Double {
        return if (drug.isLipophilic) drug.halfLifeHours * (weightKg / 70.0).pow(0.3)
        else drug.halfLifeHours
    }

    // 单次给药的残余量（考虑吸收上升相）
    fun concentrationMgWithAbsorption(
        doseMg: Double,
        halfLifeHours: Double,
        tmaxHours: Double,
        hoursSinceDose: Double
    ): Double {
        if (hoursSinceDose < 0) return 0.0
        return if (hoursSinceDose <= tmaxHours) {
            // 吸收上升相：正弦曲线从0爬升到峰值
            doseMg * sin((hoursSinceDose / tmaxHours) * PI / 2)
        } else {
            // 达峰后指数衰减
            val hoursAfterPeak = hoursSinceDose - tmaxHours
            doseMg * 0.5.pow(hoursAfterPeak / halfLifeHours)
        }
    }

    // 简单衰减（不含吸收相，用于计算残余量）
    fun concentrationMgAfterDose(doseMg: Double, halfLifeHours: Double, hoursSinceDose: Double): Double {
        if (hoursSinceDose < 0) return 0.0
        return doseMg * 0.5.pow(hoursSinceDose / halfLifeHours)
    }

    // ── 核心：当前残余总量（mg），含吸收相 ──────────────────────
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

    // ── 剂量当量百分比（新逻辑）──────────────────────────────────
    // 100% = 体内恰好有一个标准剂量当量
    // >100% = 积累超过一个剂量，需要警惕
    fun totalConcentrationPercent(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double {
        val remainingMg = totalRemainingMg(records, drug, weightKg, atTimeMs)
        val standardDose = drug.defaultDose ?: run {
            // 没有默认剂量时，用历史记录中最常用的剂量
            records.filter { it.drugName == drug.name }
                .groupBy { it.doseMg }
                .maxByOrNull { it.value.size }
                ?.key ?: 1.0
        }
        return (remainingMg / standardDose) * 100.0
    }

    // 兼容旧接口（totalConcentrationMg）
    fun totalConcentrationMg(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double = totalRemainingMg(records, drug, weightKg, atTimeMs)

    // ── 今日建议补充量 ────────────────────────────────────────────
    data class DoseAdvice(
        val drugName: String,
        val remainingMg: Double,       // 当前残余mg
        val standardDose: Double,       // 标准单次剂量
        val suggestedDose: Double,      // 建议补充mg（0表示暂不需要）
        val percentOfStandard: Double,  // 剂量当量%
        val isAccumulated: Boolean,     // 是否超过100%（积累警告）
        val unit: String
    )

    fun getDoseAdvice(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        nowMs: Long
    ): DoseAdvice {
        val remaining = totalRemainingMg(records, drug, weightKg, nowMs)
        val standardDose = drug.defaultDose ?: records
            .filter { it.drugName == drug.name }
            .groupBy { it.doseMg }.maxByOrNull { it.value.size }?.key ?: 1.0
        val percent = (remaining / standardDose) * 100.0
        val suggested = maxOf(0.0, standardDose - remaining)
        return DoseAdvice(
            drugName = drug.name,
            remainingMg = remaining,
            standardDose = standardDose,
            suggestedDose = suggested,
            percentOfStandard = percent,
            isAccumulated = percent > 110.0,  // 超过110%触发积累警告
            unit = drug.unit
        )
    }

    // ── 获取活跃药物（72h内有记录且残余>5%）────────────────────────
    fun getActiveDrugs(
        records: List<MedicationRecord>,
        allDrugs: List<DrugInfo>,
        weightKg: Double,
        atTimeMs: Long
    ): List<Pair<DrugInfo, Double>> {
        val cutoff = atTimeMs - 72 * 3_600_000L
        return allDrugs.mapNotNull { drug ->
            val hasRecent = records.any { it.drugName == drug.name && it.takenAtMs >= cutoff }
            if (!hasRecent) return@mapNotNull null
            val pct = totalConcentrationPercent(records, drug, weightKg, atTimeMs)
            if (pct > 5.0) drug to pct else null
        }.sortedByDescending { it.second }
    }

    // 浓度降至阈值的时间（用于提醒）
    fun timeUntilBelowThreshold(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        thresholdPercent: Double,
        fromTimeMs: Long
    ): Long? {
        val currentPct = totalConcentrationPercent(records, drug, weightKg, fromTimeMs)
        if (currentPct < thresholdPercent) return null

        val stepMs = 15 * 60 * 1000L
        var scanMs = fromTimeMs
        repeat(200) {
            scanMs += stepMs
            if (totalConcentrationPercent(records, drug, weightKg, scanMs) < thresholdPercent) {
                return scanMs
            }
        }
        return null
    }
}
