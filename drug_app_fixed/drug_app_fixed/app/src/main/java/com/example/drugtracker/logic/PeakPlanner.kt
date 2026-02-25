package com.example.drugtracker.logic

import com.example.drugtracker.data.DrugInfo
import com.example.drugtracker.data.MedicationRecord

object PeakPlanner {
    
    data class DosePlan(
        val drug: DrugInfo,
        val recommendedDose: Double,
        val takeAtMs: Long,
        val peakAtMs: Long,
        val expectedConcentrationAtTarget: Double
    )

    // 反向计算：为了在目标时间达到目标浓度，应该什么时候吃多少
    fun planForTargetConcentration(
        drug: DrugInfo,
        targetTimeMs: Long,
        targetConcentrationMg: Double,
        weightKg: Double,
        existingRecords: List<MedicationRecord>
    ): DosePlan? {
        val halfLife = DrugCalculator.adjustedHalfLife(drug, weightKg)
        
        // 计算现有药物在目标时间的残余量
        val existingAmount = DrugCalculator.totalConcentrationMg(
            existingRecords, drug, weightKg, targetTimeMs
        )
        
        // 需要补充的量
        val neededAmount = maxOf(0.0, targetConcentrationMg - existingAmount)
        if (neededAmount <= 0) return null
        
        // 反推剂量：考虑半衰期衰减
        val hoursBeforeTarget = drug.tmaxHours
        val doseNeeded = neededAmount / ((0.5).pow(hoursBeforeTarget / halfLife))
        
        val takeAtMs = targetTimeMs - (drug.tmaxHours * 60 * 60 * 1000).toLong()
        
        return DosePlan(
            drug = drug,
            recommendedDose = doseNeeded,
            takeAtMs = takeAtMs,
            peakAtMs = targetTimeMs,
            expectedConcentrationAtTarget = neededAmount
        )
    }

    // 计算最佳服药时机以达到目标效果
    fun findOptimalDosingTime(
        drug: DrugInfo,
        targetTimeMs: Long,
        weightKg: Double,
        minConcentrationMg: Double,
        maxConcentrationMg: Double
    ): List<Long> {
        val halfLife = DrugCalculator.adjustedHalfLife(drug, weightKg)
        val results = mutableListOf<Long>()
        
        // 从目标时间往前推，找到合适的服药时间
        val tmaxMs = (drug.tmaxHours * 60 * 60 * 1000).toLong()
        
        // 方案1：单次服药，在达峰时达到目标
        val singleDoseTime = targetTimeMs - tmaxMs
        results.add(singleDoseTime)
        
        // 方案2：如果需要维持效果，计算多次服药
        val effectiveDuration = (halfLife * 3 * 60 * 60 * 1000).toLong() // 3个半衰期
        
        return results
    }

    // 计算多个药物的组合效果
    fun calculateCombinedEffect(
        plans: List<DosePlan>,
        atTimeMs: Long
    ): Double {
        return plans.sumOf { plan ->
            val hoursSinceDose = (atTimeMs - plan.takeAtMs) / (1000.0 * 60 * 60)
            val halfLife = plan.drug.halfLifeHours
            plan.expectedConcentrationAtTarget * (0.5).pow(hoursSinceDose / halfLife)
        }
    }
}