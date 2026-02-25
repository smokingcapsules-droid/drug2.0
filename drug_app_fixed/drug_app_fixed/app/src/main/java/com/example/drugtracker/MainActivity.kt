package com.example.drugtracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
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

    // 服药时间，默认当前时间，用户可修改
    private var selectedTimeMs: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

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
        // 更新spinner选中项
        val adapter = binding.spinnerDrug.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString() == drugName) {
                binding.spinnerDrug.setSelection(i)
                break
            }
        }
        Toast.makeText(this, "已选中 $drugName，请确认剂量后记录", Toast.LENGTH_SHORT).show()
    }

    // ── 时间选择器 ──────────────────────────────────────
    private fun showDateTimePicker() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.timeInMillis = selectedTimeMs

        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(year, month, day)
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        selectedTimeMs = cal.timeInMillis
                        updateTimeButtonText()
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateTimeButtonText() {
        binding.btnSelectTime.text = "服药时间: ${TimeUtils.formatDateTime(selectedTimeMs)}"
    }

    // ── Spinner ─────────────────────────────────────────
    private fun setupDrugSpinner() {
        fun refreshSpinner(customNames: List<String>) {
            val drugNames = PresetDrugs.all.map { it.name } + customNames
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, drugNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerDrug.adapter = adapter
        }

        viewModel.allCustomDrugs.observe(this) { customDrugs ->
            refreshSpinner(customDrugs.map { it.name })
        }

        binding.spinnerDrug.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDrug = parent?.getItemAtPosition(position).toString()
                val drug = PresetDrugs.findByName(selectedDrug)
                    ?: viewModel.allCustomDrugs.value?.find { it.name == selectedDrug }?.toDrugInfo()
                drug?.defaultDose?.let { binding.etDose.setText(it.toString()) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── 图表 Tab ─────────────────────────────────────────
    private fun setupChartTabs() {
        listOf("今日活跃", "功能性", "维持类", "全部").forEach { label ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(label))
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { updateChartForTab(tab?.position ?: 0) }
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

    // ── 按钮 ─────────────────────────────────────────────
    private fun setupButtons() {
        binding.btnSelectTime.setOnClickListener { showDateTimePicker() }
        binding.btnRecord.setOnClickListener { recordMedication() }
        binding.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.btnDrugs.setOnClickListener { startActivity(Intent(this, DrugManagementActivity::class.java)) }
        binding.btnPlanner.setOnClickListener { startActivity(Intent(this, PeakPlannerActivity::class.java)) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    // ── 记录服药 ─────────────────────────────────────────
    private fun recordMedication() {
        if (selectedDrug.isEmpty()) {
            Toast.makeText(this, "请选择药物", Toast.LENGTH_SHORT).show()
            return
        }
        val dose = binding.etDose.text.toString().toDoubleOrNull()
        if (dose == null || dose <= 0) {
            Toast.makeText(this, "请输入有效剂量", Toast.LENGTH_SHORT).show()
            return
        }

        val drug = PresetDrugs.findByName(selectedDrug)
            ?: viewModel.allCustomDrugs.value?.find { it.name == selectedDrug }?.toDrugInfo()

        val record = MedicationRecord(
            drugName = selectedDrug,
            doseMg = dose,
            unit = drug?.unit ?: "mg",
            takenAtMs = selectedTimeMs,
            notes = binding.etNotes.text.toString().trim()
        )

        viewModel.addRecord(record)
        Toast.makeText(this, "✓ 已记录 $selectedDrug ${dose}${drug?.unit ?: "mg"} @ ${TimeUtils.formatDateTime(selectedTimeMs)}", Toast.LENGTH_SHORT).show()
        binding.etNotes.text?.clear()
        // 重置时间为当前时间
        selectedTimeMs = System.currentTimeMillis()
        updateTimeButtonText()
    }

    // ── 数据观察 ─────────────────────────────────────────
    private fun observeData() {
        viewModel.allRecords.observe(this) { records ->
            currentRecords = records
            updateLevothyroxineCard(records)
            updateActiveDrugsCard(records)
            updateChartForTab(binding.tabLayout.selectedTabPosition)
        }
        viewModel.allCustomDrugs.observe(this) {
            updateChartForTab(binding.tabLayout.selectedTabPosition)
        }
    }

    // ── 优甲乐状态卡 ─────────────────────────────────────
    private fun updateLevothyroxineCard(records: List<MedicationRecord>) {
        val todayStart = TimeUtils.getTodayStartMs()
        val hasTaken = records.any {
            it.drugName == "优甲乐（左甲状腺素）" && it.takenAtMs >= todayStart
        }
        if (hasTaken) {
            binding.cardLevo.setCardBackgroundColor(getColor(android.R.color.holo_green_light))
            binding.tvLevoStatus.text = "✓ 今日优甲乐已记录"
        } else {
            binding.cardLevo.setCardBackgroundColor(getColor(android.R.color.holo_red_light))
            binding.tvLevoStatus.text = "⚠ 今日优甲乐未记录服药"
        }
        binding.cardLevo.visibility = View.VISIBLE
    }

    // ── 活跃药物总览 ─────────────────────────────────────
    private fun updateActiveDrugsCard(records: List<MedicationRecord>) {
        val weightKg = UserPreferences.getWeightKg(this)
        val allDrugs = PresetDrugs.all + (viewModel.allCustomDrugs.value?.map { it.toDrugInfo() } ?: emptyList())
        val nowMs = System.currentTimeMillis()

        val active = DrugCalculator.getActiveDrugs(records, allDrugs, weightKg, nowMs)
        if (active.isEmpty()) {
            binding.tvActiveDrugs.text = "暂无活跃药物"
            return
        }

        val sb = StringBuilder()
        active.take(6).forEach { (drug, conc) ->
            val bar = "█".repeat((conc / 10).toInt().coerceIn(0, 10))
            val empty = "░".repeat(10 - (conc / 10).toInt().coerceIn(0, 10))
            sb.appendLine("${drug.name}  $bar$empty  ${String.format("%.0f", conc)}%")
        }
        binding.tvActiveDrugs.text = sb.toString().trimEnd()
    }

    // ── 图表更新 ─────────────────────────────────────────
    private fun updateChartForTab(tabPosition: Int) {
        val weightKg = UserPreferences.getWeightKg(this)
        val nowMs = System.currentTimeMillis()
        val allDrugs = PresetDrugs.all + (viewModel.allCustomDrugs.value?.map { it.toDrugInfo() } ?: emptyList())

        data class TabConfig(val drugs: List<com.example.drugtracker.data.DrugInfo>, val startMs: Long, val endMs: Long)

        val config = when (tabPosition) {
            0 -> {
                val active = DrugCalculator.getActiveDrugs(currentRecords, allDrugs, weightKg, nowMs).map { it.first }
                TabConfig(active, nowMs - 6 * 3600_000L, nowMs + 18 * 3600_000L)
            }
            1 -> TabConfig(PresetDrugs.getFunctionalDrugs(), nowMs - 6 * 3600_000L, nowMs + 24 * 3600_000L)
            2 -> TabConfig(PresetDrugs.getMaintenanceDrugs(), nowMs - 24 * 3600_000L, nowMs + 7 * 24 * 3600_000L)
            else -> {
                val withRecords = allDrugs.filter { drug -> currentRecords.any { it.drugName == drug.name } }
                TabConfig(withRecords, nowMs - 24 * 3600_000L, nowMs + 3 * 24 * 3600_000L)
            }
        }

        ChartHelper.updateChartData(
            binding.chart, currentRecords, config.drugs, weightKg,
            config.startMs, config.endMs, nowMs
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                updateChartForTab(binding.tabLayout.selectedTabPosition)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
