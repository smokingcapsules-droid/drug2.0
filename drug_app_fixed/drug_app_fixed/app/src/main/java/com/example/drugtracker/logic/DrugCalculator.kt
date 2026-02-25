package com.example.drugtracker.logic

import com.example.drugtracker.data.DrugInfo
import com.example.drugtracker.data.MedicationRecord
import kotlin.math.*

object DrugCalculator {
    
    // 根据体重调整半衰期（脂溶性药物）
    fun adjustedHalfLife(drug: DrugInfo, weightKg: Double): Double {
        return if (drug.isLipophilic) {
            drug.halfLifeHours * (weightKg / 70.0).pow(0.3)
        } else {
            drug.halfLifeHours
        }
    }

    // 计算单次给药后的残余浓度百分比 (0-100%)
    fun concentrationPercentAfterDose(halfLifeHours: Double, hoursSinceDose: Double): Double {
        if (hoursSinceDose < 0) return 0.0
        return 100.0 * (0.5).pow(hoursSinceDose / halfLifeHours)
    }

    // 计算单次给药后的残余量 (mg)
    fun concentrationMgAfterDose(doseMg: Double, halfLifeHours: Double, hoursSinceDose: Double): Double {
        if (hoursSinceDose < 0) return 0.0
        return doseMg * (0.5).pow(hoursSinceDose / halfLifeHours)
    }

    // 计算达峰时间后的浓度（考虑吸收相）
    fun concentrationWithAbsorption(
        doseMg: Double,
        halfLifeHours: Double,
        tmaxHours: Double,
        hoursSinceDose: Double
    ): Double {
        if (hoursSinceDose < 0) return 0.0
        
        // 简化模型：吸收相用正弦曲线模拟，达峰后按指数衰减
        return if (hoursSinceDose <= tmaxHours) {
            // 吸收上升相
            val absorptionRatio = sin((hoursSinceDose / tmaxHours) * PI / 2)
            doseMg * absorptionRatio
        } else {
            // 达峰后衰减
            val peakConcentration = doseMg
            val hoursAfterPeak = hoursSinceDose - tmaxHours
            peakConcentration * (0.5).pow(hoursAfterPeak / halfLifeHours)
        }
    }

    // 计算某时刻的总浓度（所有记录叠加）- 返回百分比
    fun totalConcentrationPercent(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double {
        val halfLife = adjustedHalfLife(drug, weightKg)
        val drugRecords = records.filter { it.drugName == drug.name }
        
        if (drugRecords.isEmpty()) return 0.0
        
        val totalDose = drugRecords.sumOf { it.doseMg }
        if (totalDose == 0.0) return 0.0
        
        var currentAmount = 0.0
        drugRecords.forEach { record ->
            val hoursSince = (atTimeMs - record.takenAtMs) / (1000.0 * 60 * 60)
            currentAmount += concentrationMgAfterDose(record.doseMg, halfLife, hoursSince)
        }
        
        return (currentAmount / totalDose) * 100.0
    }

    // 计算某时刻的总残余量 (mg)
    fun totalConcentrationMg(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        atTimeMs: Long
    ): Double {
        val halfLife = adjustedHalfLife(drug, weightKg)
        val drugRecords = records.filter { it.drugName == drug.name }
        
        var currentAmount = 0.0
        drugRecords.forEach { record ->
            val hoursSince = (atTimeMs - record.takenAtMs) / (1000.0 * 60 * 60)
            currentAmount += concentrationMgAfterDose(record.doseMg, halfLife, hoursSince)
        }
        
        return currentAmount
    }

    // 计算稳态浓度（规律服药时）
    fun steadyStateConcentration(
        doseMg: Double,
        halfLifeHours: Double,
        dosingIntervalHours: Double
    ): Double {
        // 稳态浓度 = 剂量 / (1 - 0.5^(间隔/半衰期))
        val accumulationFactor = 1.0 / (1.0 - (0.5).pow(dosingIntervalHours / halfLifeHours))
        return doseMg * accumulationFactor
    }

    // 计算下次达峰时间
    fun nextPeakTime(records: List<MedicationRecord>, drug: DrugInfo, fromTimeMs: Long): Long? {
        val drugRecords = records.filter { it.drugName == drug.name }
        if (drugRecords.isEmpty()) return null
        
        // 找到最近一次服药的达峰时间
        val lastRecord = drugRecords.maxByOrNull { it.takenAtMs } ?: return null
        val tmaxMs = (drug.tmaxHours * 60 * 60 * 1000).toLong()
        val peakTime = lastRecord.takenAtMs + tmaxMs
        
        return if (peakTime > fromTimeMs) peakTime else null
    }

    // 计算浓度降至阈值的时间
    fun timeUntilBelowThreshold(
        records: List<MedicationRecord>,
        drug: DrugInfo,
        weightKg: Double,
        thresholdPercent: Double,
        fromTimeMs: Long
    ): Long? {
        val halfLife = adjustedHalfLife(drug, weightKg)
        val drugRecords = records.filter { it.drugName == drug.name }
        if (drugRecords.isEmpty()) return null
        
        val totalDose = drugRecords.sumOf { it.doseMg }
        if (totalDose == 0.0) return null
        
        // 逐步扫描未来时间点
        val stepMs = 15 * 60 * 1000L // 15分钟
        var scanMs = fromTimeMs
        
        repeat(200) { // 最多扫描50小时
            scanMs += stepMs
            var currentAmount = 0.0
            drugRecords.forEach { record ->
                val hoursSince = (scanMs - record.takenAtMs) / (1000.0 * 60 * 60)
                currentAmount += concentrationMgAfterDose(record.doseMg, halfLife, hoursSince)
            }
            val percent = (currentAmount / totalDose) * 100.0
            if (percent < thresholdPercent) {
                return scanMs
            }
        }
        
        return null
    }

    // 获取活跃药物（过去72小时内有记录且当前浓度>5%）
    fun getActiveDrugs(
        records: List<MedicationRecord>,
        allDrugs: List<DrugInfo>,
        weightKg: Double,
        atTimeMs: Long
    ): List<Pair<DrugInfo, Double>> {
        val seventyTwoHoursAgo = atTimeMs - 72 * 60 * 60 * 1000L
        
        return allDrugs.mapNotNull { drug ->
            val drugRecords = records.filter { 
                it.drugName == drug.name && it.takenAtMs >= seventyTwoHoursAgo 
            }
            if (drugRecords.isEmpty()) return@mapNotNull null
            
            val concentration = totalConcentrationPercent(records, drug, weightKg, atTimeMs)
            if (concentration > 5.0) {
                drug to concentration
            } else null
        }.sortedByDescending { it.second }
    }
}