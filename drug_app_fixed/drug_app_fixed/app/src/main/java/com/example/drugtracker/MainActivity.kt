package com.example.drugtracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.drugtracker.data.DrugInfo
import com.example.drugtracker.data.MedicationRecord
import com.example.drugtracker.data.PresetDrugs
import com.example.drugtracker.databinding.ActivityMainBinding
import com.example.drugtracker.logic.DrugCalculator
import com.example.drugtracker.logic.ReminderEngine
import com.example.drugtracker.ui.ChartHelper
import com.example.drugtracker.ui.MedicationViewModel
import com.example.drugtracker.util.TimeUtils
import com.example.drugtracker.util.UserPreferences
import com.google.android.material.tabs.TabLayout
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MedicationViewModel
    private var selectedDrug: String = ""
    private var currentRecords: List<MedicationRecord> = emptyList()
    private var selectedTimeMs: Long = System.currentTimeMillis()
    private var currentDrugInfo: DrugInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[MedicationViewModel::class.java]
        updateTimeButtonText()
        setupDrugSpinner()
        setupChartTabs()
        setupButtons()
        setupChart()
        observeData()
        checkQuickRecord(intent)
        ReminderEngine.scheduleLevothyroxineDaily(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        checkQuickRecord(intent)
    }

    private fun checkQuickRecord(intent: Intent?) {
        val drugName = intent?.getStringExtra("quick_record_drug") ?: return
        selectedDrug = drugName
        val adapter = binding.spinnerDrug.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString() == drugName) {
                binding.spinnerDrug.setSelection(i)
                break
            }
        }
    }

    private fun showDateTimePicker() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.timeInMillis = selectedTimeMs
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            TimePickerDialog(this, { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                selectedTimeMs = cal.timeInMillis
                updateTimeButtonText()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateTimeButtonText() {
        binding.btnSelectTime.text = "服药时间: ${TimeUtils.formatDateTime(selectedTimeMs)}"
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
                currentDrugInfo = PresetDrugs.findByName(selectedDrug)
                    ?: viewModel.allCustomDrugs.value?.find { it.name == selectedDrug }?.toDrugInfo()

                // 更新剂量单位显示
                binding.tvUnit.text = currentDrugInfo?.unit ?: "mg"
                // 自动填充默认剂量（原始值，如150μg直接显示150）
                currentDrugInfo?.defaultDose?.let { binding.etDose.setText(it.toString()) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupChartTabs() {
        listOf("今日活跃", "功能性", "维持类", "全部").forEach {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(it))
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateChartForTab(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupChart() {
        ChartHelper.setupChart(binding.chart)
        binding.chart.setOnClickListener {
            startActivity(Intent(this, FullscreenChartActivity::class.java))
        }
    }

    private fun setupButtons() {
        binding.btnSelectTime.setOnClickListener { showDateTimePicker() }
        binding.btnRecord.setOnClickListener { recordMedication() }
        binding.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.btnDrugs.setOnClickListener { startActivity(Intent(this, DrugManagementActivity::class.java)) }
        binding.btnPlanner.setOnClickListener { startActivity(Intent(this, PeakPlannerActivity::class.java)) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun recordMedication() {
        if (selectedDrug.isEmpty()) {
            Toast.makeText(this, "请选择药物", Toast.LENGTH_SHORT).show()
            return
        }
        val doseInput = binding.etDose.text.toString().toDoubleOrNull()
        if (doseInput == null || doseInput <= 0) {
            Toast.makeText(this, "请输入有效剂量", Toast.LENGTH_SHORT).show()
            return
        }

        val drug = currentDrugInfo ?: run {
            Toast.makeText(this, "药物信息获取失败", Toast.LENGTH_SHORT).show()
            return
        }

        // 剂量转换：若药物单位是μg，输入值除以1000转换为毫克
        val doseMg = if (drug.unit == "μg") {
            doseInput / 1000.0
        } else {
            doseInput
        }

        val record = MedicationRecord(
            drugName = selectedDrug,
            doseMg = doseMg,
            unit = "mg", // 统一存储为mg
            takenAtMs = selectedTimeMs,
            notes = binding.etNotes.text.toString().trim()
        )

        viewModel.addRecord(record)

        // 提示信息显示用户输入值和原始单位
        Toast.makeText(
            this,
            "✓ 已记录 $selectedDrug $doseInput${drug.unit}",
            Toast.LENGTH_SHORT
        ).show()

        binding.etNotes.text?.clear()
        selectedTimeMs = System.currentTimeMillis()
        updateTimeButtonText()
    }

    private fun observeData() {
        viewModel.allRecords.observe(this) { records ->
            currentRecords = records
            updateLevothyroxineCard(records)
            updateActiveDrugsCard(records)
            updateChartForTab(binding.tabLayout.selectedTabPosition)
        }
        viewModel.allCustomDrugs.observe(this) {
            updateActiveDrugsCard(currentRecords)
            updateChartForTab(binding.tabLayout.selectedTabPosition)
        }
    }

    private fun updateLevothyroxineCard(records: List<MedicationRecord>) {
        val hasTaken = records.any {
            it.drugName == "优甲乐（左甲状腺素）" && it.takenAtMs >= TimeUtils.getTodayStartMs()
        }
        binding.cardLevo.setCardBackgroundColor(
            if (hasTaken) getColor(android.R.color.holo_green_light)
            else getColor(android.R.color.holo_red_light)
        )
        binding.tvLevoStatus.text = if (hasTaken) "✓ 今日优甲乐已记录" else "⚠ 今日优甲乐未记录服药"
        binding.cardLevo.visibility = View.VISIBLE
    }

    private fun updateActiveDrugsCard(records: List<MedicationRecord>) {
        val allDrugs = PresetDrugs.all + (viewModel.allCustomDrugs.value?.map { it.toDrugInfo() } ?: emptyList())
        val nowMs = System.currentTimeMillis()
        val active = DrugCalculator.getActiveDrugs(records, allDrugs, this, nowMs)

        if (active.isEmpty()) {
            binding.tvActiveDrugs.text = "暂无活跃药物"
            return
        }

        val sb = StringBuilder()
        active.take(8).forEach { (drug, pct) ->
            val advice = DrugCalculator.getDoseAdvice(records, drug, this, nowMs)
            val barLen = (pct / 10).toInt().coerceIn(0, 10)
            val bar = "█".repeat(barLen) + "░".repeat(10 - barLen)

            if (advice.isMaintenance) {
                // 维持类：显示稳态达成度
                val ssStr = String.format("%.0f", advice.steadyStatePercent)
                sb.appendLine(drug.name)
                sb.appendLine("  $bar 稳态${ssStr}%  剩${String.format("%.1f", advice.remainingMg)}${drug.unit}")
                sb.appendLine("  → 按处方服用 ${advice.standardDose}${drug.unit}")
            } else {
                // 按需类：显示残余%和补充建议
                val warn = if (advice.isAccumulated) " ⚠积累" else ""
                sb.appendLine(drug.name)
                sb.appendLine("  $bar ${String.format("%.0f", pct)}%残余$warn  剩${String.format("%.2f", advice.remainingMg)}${drug.unit}")
                if (advice.suggestedDose > 0.01) {
                    sb.appendLine("  → 可补充 ${String.format("%.2f", advice.suggestedDose)}${drug.unit}")
                } else {
                    sb.appendLine("  → 暂不需要补充")
                }
            }
            sb.appendLine()
        }
        binding.tvActiveDrugs.text = sb.toString().trimEnd()
    }

    private fun updateChartForTab(tabPosition: Int) {
        val weightKg = UserPreferences.getWeightKg(this)
        val nowMs = System.currentTimeMillis()
        val allDrugs = PresetDrugs.all + (viewModel.allCustomDrugs.value?.map { it.toDrugInfo() } ?: emptyList())
        data class Cfg(val drugs: List<DrugInfo>, val start: Long, val end: Long)

        val cfg = when (tabPosition) {
            0 -> {
                val activeDrugs = DrugCalculator.getActiveDrugs(currentRecords, allDrugs, this, nowMs)
                    .map { it.first }
                Cfg(activeDrugs, nowMs - 6 * 3600_000L, nowMs + 18 * 3600_000L)
            }
            1 -> Cfg(PresetDrugs.getFunctionalDrugs(), nowMs - 6 * 3600_000L, nowMs + 24 * 3600_000L)
            2 -> Cfg(PresetDrugs.getMaintenanceDrugs(), nowMs - 24 * 3600_000L, nowMs + 7 * 24 * 3600_000L)
            else -> {
                val withRecords = allDrugs.filter { d -> currentRecords.any { it.drugName == d.name } }
                Cfg(withRecords, nowMs - 24 * 3600_000L, nowMs + 3 * 24 * 3600_000L)
            }
        }
        ChartHelper.updateChartData(binding.chart, currentRecords, cfg.drugs, weightKg, cfg.start, cfg.end, nowMs)
    }
}
