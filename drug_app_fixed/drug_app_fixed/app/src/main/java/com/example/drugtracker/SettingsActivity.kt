package com.example.drugtracker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.drugtracker.databinding.ActivitySettingsBinding
import com.example.drugtracker.util.*
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // 文件选择器：用于选择CSV恢复文件
    private val csvPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) doRestoreCSV(uri)
        else Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "设置"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        loadSettings()
        setupButtons()
    }

    private fun loadSettings() {
        binding.etWeight.setText(UserPreferences.getWeightKg(this).toString())
        binding.etThreshold.setText(UserPreferences.getReminderThreshold(this).toString())
        binding.etLevoHour.setText(UserPreferences.getLevothyroxineReminderHour(this).toString())
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnExportLog.setOnClickListener { exportCrashLog() }
        binding.btnClearLog.setOnClickListener { clearCrashLog() }
        binding.btnBackup.setOnClickListener { backupCSV() }
        binding.btnRestore.setOnClickListener { startCSVRestore() }
    }

    private fun saveSettings() {
        val weight = binding.etWeight.text.toString().toDoubleOrNull()
        val threshold = binding.etThreshold.text.toString().toDoubleOrNull()
        val levoHour = binding.etLevoHour.text.toString().toIntOrNull()
        if (weight == null || weight <= 0) { Toast.makeText(this, "体重无效", Toast.LENGTH_SHORT).show(); return }
        if (threshold == null) { Toast.makeText(this, "阈值无效", Toast.LENGTH_SHORT).show(); return }
        if (levoHour == null || levoHour !in 0..23) { Toast.makeText(this, "小时需在0-23之间", Toast.LENGTH_SHORT).show(); return }
        UserPreferences.setWeightKg(this, weight)
        UserPreferences.setReminderThreshold(this, threshold)
        UserPreferences.setLevothyroxineReminderHour(this, levoHour)
        Toast.makeText(this, "✓ 已保存", Toast.LENGTH_SHORT).show()
    }

    // ── CSV备份 ────────────────────────────────────────────
    private fun backupCSV() {
        lifecycleScope.launch {
            val uri = BackupHelper.exportToCSV(this@SettingsActivity)
            if (uri != null) {
                ExportHelper.shareFile(this@SettingsActivity, uri, "text/csv")
            } else {
                Toast.makeText(this@SettingsActivity, "备份失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── CSV恢复：用系统文件选择器 ─────────────────────────
    private fun startCSVRestore() {
        AlertDialog.Builder(this)
            .setTitle("恢复数据")
            .setMessage("从CSV备份文件恢复，当前所有记录将被覆盖。\n\n请选择本应用之前导出的 .csv 备份文件。")
            .setPositiveButton("选择文件") { _, _ ->
                // 使用 ActivityResultContracts.OpenDocument，是最可靠的文件选择方式
                csvPickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doRestoreCSV(uri: Uri) {
        lifecycleScope.launch {
            val (success, message) = BackupHelper.importFromCSV(this@SettingsActivity, uri)
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(if (success) "✓ 恢复成功" else "✗ 恢复失败")
                .setMessage(message + if (success) "\n\n请返回主界面查看数据。" else "")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun exportCrashLog() {
        val log = CrashLogger.readLog(this)
        if (log == "暂无崩溃日志") { Toast.makeText(this, "暂无崩溃日志", Toast.LENGTH_SHORT).show(); return }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "DrugTracker 崩溃日志")
                putExtra(Intent.EXTRA_TEXT, log)
            }, "分享崩溃日志"
        ))
    }

    private fun clearCrashLog() {
        AlertDialog.Builder(this).setTitle("确认清空").setMessage("清空所有崩溃日志？")
            .setPositiveButton("清空") { _, _ -> CrashLogger.clearLog(this); Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("取消", null).show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
