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
            val names = PresetDrugs.all.map { it.name } + customDrugs.map { it.name }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerDrug.adapter = adapter
        }
        binding.spinnerDrug.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDrug = parent?.getItemAtPosition(position).toString()
                val drug = PresetDrugs.findByName(selectedDrug)
                    ?: viewModel.allCustomDrugs.value?.find { it.name == selectedDrug }?.toDrugInfo()
                drug?.let {
                    binding.etDose.setText(it.defaultDose?.toString() ?: "")
                    binding.tvDrugInfo.text = "半衰期 ${it.halfLifeHours}h · 达峰 ${it.tmaxHours}h · ${it.notes}"
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
                cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                targetTimeMs = cal.timeInMillis
                updateTargetTimeDisplay()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateTargetTimeDisplay() {
        binding.btnSelectTime.text = "目标时间: ${TimeUtils.formatDateTime(targetTimeMs)}"
    }

    private fun calculatePlan() {
        if (selectedDrug.isEmpty()) { Toast.makeText(this, "请选择药物", Toast.LENGTH_SHORT).show(); return }

        val drug = PresetDrugs.findByName(selectedDrug)
            ?: viewModel.allCustomDrugs.value?.find { it.name == selectedDrug }?.toDrugInfo()
            ?: run { Toast.makeText(this, "药物未找到", Toast.LENGTH_SHORT).show(); return }

        val dose = binding.etDose.text.toString().toDoubleOrNull()
            ?: run { Toast.makeText(this, "请输入剂量", Toast.LENGTH_SHORT).show(); return }

        val weightKg = UserPreferences.getWeightKg(this)
        val halfLife = DrugCalculator.adjustedHalfLife(drug, weightKg)

        val takeAtMs = targetTimeMs - (drug.tmaxHours * 3600_000L).toLong()
        val peakAtMs = targetTimeMs
        val windowStartMs = peakAtMs - (halfLife * 0.5 * 3600_000L).toLong()
        val windowEndMs   = peakAtMs + (halfLife * 1.0 * 3600_000L).toLong()
        val clearAtMs     = peakAtMs + (halfLife * 4.32 * 3600_000L).toLong()

        viewModel.getRecordsForDrug(selectedDrug) { records ->
            runOnUiThread {
                val nowMs = System.currentTimeMillis()
                // 用新逻辑：当前残余mg
                val existingMg = DrugCalculator.totalRemainingMg(records, drug, weightKg, takeAtMs)
                val advice = DrugCalculator.getDoseAdvice(records, drug, weightKg, nowMs)

                val existingNote = if (existingMg > dose * 0.05)
                    "\n⚠ 届时体内还有 ${String.format("%.2f", existingMg)}${drug.unit} 残余（约${String.format("%.0f", advice.percentOfStandard)}%剂量当量）"
                else ""

                val takeNote = if (takeAtMs < nowMs) "\n（目标时间较近，建议现在立即服药）" else ""

                val accumulateWarn = if (existingMg + dose > (drug.defaultDose ?: dose) * 1.5)
                    "\n🔴 叠加后超过1.5倍标准剂量，注意过量风险" else ""

                binding.tvResult.text = """
建议服药时间：${TimeUtils.formatDateTime(takeAtMs)}$takeNote
计划剂量：$dose ${drug.unit}$existingNote$accumulateWarn

📈 预计达峰：${TimeUtils.formatDateTime(peakAtMs)}
⏱ 效果时间窗：${TimeUtils.formatTime(windowStartMs)} ~ ${TimeUtils.formatTime(windowEndMs)}
✅ 代谢至5%：${TimeUtils.formatDateTime(clearAtMs)}
                """.trimIndent()
                binding.tvResult.visibility = View.VISIBLE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
