package com.example.drugtracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.drugtracker.data.PresetDrugs
import com.example.drugtracker.databinding.ActivityPeakPlannerBinding
import com.example.drugtracker.logic.DrugCalculator
import com.example.drugtracker.ui.MedicationViewModel
import com.example.drugtracker.util.TimeUtils
import com.example.drugtracker.util.UserPreferences
import java.util.*

class PeakPlannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPeakPlannerBinding
    private lateinit var viewModel: MedicationViewModel
    private var selectedDrug: String = ""
    private var targetTimeMs: Long = System.currentTimeMillis() + 4 * 3600_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPeakPlannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "峰值规划器"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this)[MedicationViewModel::class.java]

        updateTargetTimeDisplay()
        setupDrugSpinner()
        setupButtons()
    }

    private fun setupDrugSpinner() {
        viewModel.allCustomDrugs.observe(this) { customDrugs ->
            val drugNames = PresetDrugs.all.map { it.name } + customDrugs.map { it.name }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, drugNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerDrug.adapter = adapter
        }

        binding.spinnerDrug.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDrug = parent?.getItemAtPosition(position).toString()
                // 自动填入默认剂量
                val drug = PresetDrugs.findByName(selectedDrug)
                    ?: viewModel.allCustomDrugs.value?.find { it.name == selectedDrug }?.toDrugInfo()
                drug?.defaultDose?.let { binding.etDose.setText(it.toString()) }
                drug?.let {
                    binding.tvDrugInfo.text = "半衰期: ${it.halfLifeHours}h | 达峰: ${it.tmaxHours}h"
                    binding.tvDrugInfo.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnSelectTime.setOnClickListener { showDateTimePicker() }
        binding.btnCalculate.setOnClickListener { calculatePlan() }
    }

    private fun showDateTimePicker() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.timeInMillis = targetTimeMs

        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            TimePickerDialog(this, { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                targetTimeMs = cal.timeInMillis
                updateTargetTimeDisplay()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateTargetTimeDisplay() {
        binding.btnSelectTime.text = "目标时间: ${TimeUtils.formatDateTime(targetTimeMs)}"
    }

    private fun calculatePlan() {
        if (selectedDrug.isEmpty()) {
            Toast.makeText(this, "请选择药物", Toast.LENGTH_SHORT).show()
            return
        }

        val drug = PresetDrugs.findByName(selectedDrug)
            ?: viewModel.allCustomDrugs.value?.find { it.name == selectedDrug }?.toDrugInfo()

        if (drug == null) {
            Toast.makeText(this, "药物信息未找到", Toast.LENGTH_SHORT).show()
            return
        }

        val doseStr = binding.etDose.text.toString()
        val dose = doseStr.toDoubleOrNull()
        if (dose == null || dose <= 0) {
            Toast.makeText(this, "请输入有效剂量", Toast.LENGTH_SHORT).show()
            return
        }

        val weightKg = UserPreferences.getWeightKg(this)
        val halfLife = DrugCalculator.adjustedHalfLife(drug, weightKg)

        // 反向计算：服药时间 = 目标时间 - Tmax
        val takeAtMs = targetTimeMs - (drug.tmaxHours * 3600_000L).toLong()
        val peakAtMs = targetTimeMs  // 服药后Tmax小时就是峰值

        // 有效时间窗：峰值前后约1个半衰期内浓度 > 50%
        val windowStartMs = peakAtMs - (halfLife * 0.5 * 3600_000L).toLong()
        val windowEndMs   = peakAtMs + (halfLife * 1.0 * 3600_000L).toLong()

        // 代谢至5%（约需4.32个半衰期）
        val clearAtMs = peakAtMs + (halfLife * 4.32 * 3600_000L).toLong()

        // 检查当前体内是否已有残余
        viewModel.getRecordsForDrug(selectedDrug) { records ->
            runOnUiThread {
                val existingMg = DrugCalculator.totalConcentrationMg(records, drug, weightKg, takeAtMs)
                val existingNote = if (existingMg > dose * 0.1) {
                    "\n⚠ 注意：届时体内仍有 ${String.format("%.1f", existingMg)}${drug.unit} 残余"
                } else ""

                val nowMs = System.currentTimeMillis()
                val takeNote = if (takeAtMs < nowMs) {
                    "\n（目标时间过近，建议现在立即服药）"
                } else ""

                val result = """
建议服药时间：${TimeUtils.formatDateTime(takeAtMs)}$takeNote
计划剂量：$dose ${drug.unit}

📈 预计达峰：${TimeUtils.formatDateTime(peakAtMs)}
⏱ 有效时间窗：${TimeUtils.formatTime(windowStartMs)} ~ ${TimeUtils.formatTime(windowEndMs)}
✅ 基本代谢完（<5%）：${TimeUtils.formatDateTime(clearAtMs)}$existingNote
                """.trimIndent()

                binding.tvResult.text = result
                binding.tvResult.visibility = View.VISIBLE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
