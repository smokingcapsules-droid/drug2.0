package com.example.drugtracker

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.drugtracker.data.MedicationRecord
import com.example.drugtracker.data.PresetDrugs
import com.example.drugtracker.databinding.ActivityFullscreenChartBinding
import com.example.drugtracker.ui.ChartHelper
import com.example.drugtracker.ui.MedicationViewModel
import com.example.drugtracker.util.UserPreferences

class FullscreenChartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenChartBinding
    private lateinit var viewModel: MedicationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding = ActivityFullscreenChartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MedicationViewModel::class.java]
        // 修改：传递 context 和 isFullscreen
        ChartHelper.setupChart(binding.chart, this, true)

        binding.btnClose.setOnClickListener { finish() }

        viewModel.allRecords.observe(this) { records ->
            renderChart(records, viewModel.allCustomDrugs.value?.map { it.toDrugInfo() } ?: emptyList())
        }
        viewModel.allCustomDrugs.observe(this) { customDrugs ->
            renderChart(viewModel.allRecords.value ?: emptyList(), customDrugs.map { it.toDrugInfo() })
        }
    }

    private fun renderChart(records: List<MedicationRecord>, customDrugInfos: List<com.example.drugtracker.data.DrugInfo>) {
        val weightKg = UserPreferences.getWeightKg(this)
        val bodyFat = UserPreferences.getBodyFatPercent(this)
        val therapyLow = UserPreferences.getTherapyWindowLow(this)
        val therapyHigh = UserPreferences.getTherapyWindowHigh(this)
        val allDrugs = PresetDrugs.all + customDrugInfos
        val nowMs = System.currentTimeMillis()

        val drugsWithRecords = allDrugs.filter { drug ->
            records.any { it.drugName == drug.name }
        }

        ChartHelper.updateChartData(
            binding.chart,
            records,
            drugsWithRecords,
            weightKg,
            bodyFat,
            nowMs - 24 * 60 * 60 * 1000L,
            nowMs + 3 * 24 * 60 * 60 * 1000L,
            nowMs,
            therapyLow,
            therapyHigh
        )
    }
}
