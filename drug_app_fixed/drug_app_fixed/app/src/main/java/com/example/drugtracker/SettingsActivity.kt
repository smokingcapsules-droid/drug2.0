package com.example.drugtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.drugtracker.databinding.ActivitySettingsBinding
import com.example.drugtracker.util.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

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
        val weight = UserPreferences.getWeightKg(this)
        val threshold = UserPreferences.getReminderThreshold(this)
        val levoHour = UserPreferences.getLevothyroxineReminderHour(this)

        binding.etWeight.setText(weight.toString())
        binding.etThreshold.setText(threshold.toString())
        binding.etLevoHour.setText(levoHour.toString())
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnExportLog.setOnClickListener {
            exportCrashLog()
        }

        binding.btnClearLog.setOnClickListener {
            clearCrashLog()
        }

        binding.btnBackup.setOnClickListener {
            backupDatabase()
        }

        binding.btnRestore.setOnClickListener {
            showRestoreDialog()
        }
    }

    private fun saveSettings() {
        val weightStr = binding.etWeight.text.toString()
        val thresholdStr = binding.etThreshold.text.toString()
        val levoHourStr = binding.etLevoHour.text.toString()

        val weight = weightStr.toDoubleOrNull()
        val threshold = thresholdStr.toDoubleOrNull()
        val levoHour = levoHourStr.toIntOrNull()

        if (weight == null || weight <= 0) {
            Toast.makeText(this, "体重无效", Toast.LENGTH_SHORT).show()
            return
        }

        if (threshold == null || threshold < 0 || threshold > 100) {
            Toast.makeText(this, "提醒阈值需在 0-100 之间", Toast.LENGTH_SHORT).show()
            return
        }

        if (levoHour == null || levoHour < 0 || levoHour > 23) {
            Toast.makeText(this, "提醒时间需在 0-23 之间", Toast.LENGTH_SHORT).show()
            return
        }

        UserPreferences.setWeightKg(this, weight)
        UserPreferences.setReminderThreshold(this, threshold)
        UserPreferences.setLevothyroxineReminderHour(this, levoHour)

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun exportCrashLog() {
        val logContent = CrashLogger.readLog(this)
        if (logContent == "暂无崩溃日志") {
            Toast.makeText(this, "暂无崩溃日志", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DrugTracker 崩溃日志")
            putExtra(Intent.EXTRA_TEXT, logContent)
        }
        startActivity(Intent.createChooser(shareIntent, "分享崩溃日志"))
    }

    private fun clearCrashLog() {
        AlertDialog.Builder(this)
            .setTitle("确认清空")
            .setMessage("清空所有崩溃日志？")
            .setPositiveButton("清空") { _, _ ->
                CrashLogger.clearLog(this)
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun backupDatabase() {
        val uri = BackupHelper.exportDatabaseFile(this)
        if (uri != null) {
            ExportHelper.shareFile(this, uri, "application/octet-stream")
        } else {
            Toast.makeText(this, "备份失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRestoreDialog() {
        AlertDialog.Builder(this)
            .setTitle("恢复数据")
            .setMessage("请选择之前备份的数据库文件。恢复将覆盖当前所有数据。")
            .setPositiveButton("选择文件") { _, _ ->
                // 这里简化处理，实际应该使用文件选择器
                Toast.makeText(this, "请通过文件管理器选择 .db 文件打开", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}