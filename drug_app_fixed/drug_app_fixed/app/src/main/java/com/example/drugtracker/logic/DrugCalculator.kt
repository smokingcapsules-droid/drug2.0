package com.example.drugtracker.logic

import com.example.drugtracker.data.DrugInfo
import com.example.drugtracker.data.MedicationRecord
import com.example.drugtracker.util.UserPreferences
import kotlin.math.*

object DrugCalculator {

    // MODIFIED: 半衰期修正，加入体脂率影响（脂溶性药物）
    fun adjustedHalfLife(
        drug: DrugInfo,
        weightKg: Double,
        bodyFatPercent: Double // 0~100 的百分比
    ): Double {
        val base = if (drug.isLipophilic) {
            drug.halfLifeHours * (weightKg / 70.0).pow(0.3)
        } else {
            drug.halfLifeHours * (weightKg / 70.0).pow(0.25)
        }
        return if (drug.isLipophilic) {
            // 脂溶性药物额外考虑体脂率：体脂率25%为基准，每高1%半衰期延长0.3%
            val fatFactor = 1 + 0.3 * (bodyFatPercent / 100.0 - 0.25)
            base * fatFactor.coerceAtLeast(0.5) // 至少不低于一半
        } else {
            base
        }
    }

    // 兼容旧接口（从UserPreferences读取体脂率）
    fun adjustedHalfLife(drug: DrugInfo, context: android.content.Context): Double {
        val weight = UserPreferences.getWeightKg(context)
        val bodyFat = UserPreferences.getBodyFatPercent(context)
        return adjustedHalfLife(drug, weight, bodyFat)
    }

    // 含吸收相的单次给药浓度
    fun concentrationMgWithAbsorption(
        doseMg: Double,
        halfLifeHours: Double,
        tmaxHours: Double,
        hoursSinceDose: Double
    ): Double {
        if (hoursSinceDose < 0) return 0.0
        return if (hoursSinceDose <= tmaxHours) {
            doseMg * (hoursSinceDose / tmaxHours)
        } else {
            val hoursAfterPeak = hoursSinceDose - tmaxHours
            doseMg * 0.5.pow(hoursAfterPeak / halfLifeHours)
        }
    }

    // 当前体内总残余量（mg）
    fun totalRemainingMg(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        bodyFatPercent: Double,
        atTimeMs: Long
    ): Double {
        val halfLife = adjustedHalfLife(drug, weightKg, bodyFatPercent)
        return records
            .filter { it.drugName == drug.name && it.takenAtMs <= atTimeMs }
            .sumOf { r ->
                concentrationMgWithAbsorption(
                    r.doseMg, halfLife, drug.tmaxHours,
                    (atTimeMs - r.takenAtMs) / 3_600_000.0
                )
            }
    }

    // 统一百分比接口
    fun totalConcentrationPercent(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        bodyFatPercent: Double,
        atTimeMs: Long
    ): Double {
        return if (drug.isCritical) {
            getSteadyStatePercent(records, drug, weightKg, bodyFatPercent, atTimeMs)
        } else {
            val remaining = totalRemainingMg(records, drug, weightKg, bodyFatPercent, atTimeMs)
            val dose = getStandardDose(drug, records)
            (remaining / dose * 100.0).coerceAtMost(200.0)
        }
    }

    // 维持类：稳态达成度
    fun getSteadyStatePercent(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        bodyFatPercent: Double,
        atTimeMs: Long
    ): Double {
        val halfLife = adjustedHalfLife(drug, weightKg, bodyFatPercent)
        val dosingIntervalHours = 24.0
        val dailyDose = getStandardDose(drug, records)

        val accFactor = 1.0 / (1.0 - 0.5.pow(dosingIntervalHours / halfLife))
        val steadyStateMg = dailyDose * accFactor
        val currentMg = totalRemainingMg(records, drug, weightKg, bodyFatPercent, atTimeMs)

        return (currentMg / steadyStateMg * 100.0).coerceAtMost(100.0)
    }

    // 获取标准剂量（统一转换为毫克）
    private fun getStandardDose(drug: DrugInfo, records: List<MedicationRecord>): Double {
        val dose = if (drug.defaultDose != null && drug.defaultDose > 0) {
            drug.defaultDose
        } else {
            records.filter { it.drugName == drug.name }
                .groupBy { it.doseMg }.maxByOrNull { it.value.size }?.key ?: 1.0
        }
        return if (drug.unit == "μg") dose / 1000.0 else dose
    }

    // 剂量建议数据类
    data class DoseAdvice(
        val drugName: String,
        val remainingMg: Double,
        val standardDose: Double,
        val suggestedDose: Double,
        val percentOfStandard: Double,
        val isAccumulated: Boolean,
        val isMaintenance: Boolean,
        val steadyStatePercent: Double,
        val unit: String
    )

    fun getDoseAdvice(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        context: android.content.Context,
        nowMs: Long
    ): DoseAdvice {
        val weight = UserPreferences.getWeightKg(context)
        val bodyFat = UserPreferences.getBodyFatPercent(context)
        val remaining = totalRemainingMg(records, drug, weight, bodyFat, nowMs)
        val standardDose = getStandardDose(drug, records)
        val isMaintenance = drug.isCritical

        return if (isMaintenance) {
            val ssPercent = getSteadyStatePercent(records, drug, weight, bodyFat, nowMs)
            DoseAdvice(
                drugName = drug.name,
                remainingMg = remaining,
                standardDose = standardDose,
                suggestedDose = standardDose,
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

    // 活跃药物列表
    fun getActiveDrugs(
        records: List<MedicationRecord>,
        allDrugs: List<DrugInfo>,
        context: android.content.Context,
        atTimeMs: Long
    ): List<Pair<DrugInfo, Double>> {
        val weight = UserPreferences.getWeightKg(context)
        val bodyFat = UserPreferences.getBodyFatPercent(context)
        val cutoff = atTimeMs - 72 * 3_600_000L
        return allDrugs.mapNotNull { drug ->
            val recentRecords = records.filter {
                it.drugName == drug.name && it.takenAtMs >= cutoff
            }
            if (recentRecords.isEmpty()) return@mapNotNull null

            val pct = totalConcentrationPercent(records, drug, weight, bodyFat, atTimeMs)
            val justTaken = recentRecords.any {
                (atTimeMs - it.takenAtMs) < drug.tmaxHours * 2 * 3_600_000L
            }
            if (pct >= 1.0 || justTaken) drug to pct else null
        }.sortedByDescending { it.second }
    }

    fun timeUntilBelowThreshold(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        context: android.content.Context,
        thresholdPercent: Double,
        fromTimeMs: Long
    ): Long? {
        val weight = UserPreferences.getWeightKg(context)
        val bodyFat = UserPreferences.getBodyFatPercent(context)
        if (totalConcentrationPercent(records, drug, weight, bodyFat, fromTimeMs) < thresholdPercent) return null
        val stepMs = 15 * 60 * 1000L
        var scanMs = fromTimeMs
        repeat(200) {
            scanMs += stepMs
            if (totalConcentrationPercent(records, drug, weight, bodyFat, scanMs) < thresholdPercent) return scanMs
        }
        return null
    }
}
