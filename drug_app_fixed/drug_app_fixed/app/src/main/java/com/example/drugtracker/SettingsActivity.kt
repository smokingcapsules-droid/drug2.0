package com.example.drugtracker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.drugtracker.databinding.ActivitySettingsBinding
import com.example.drugtracker.util.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // 文件选择器（真正可用的恢复入口）
    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                doRestore(uri)
            } else {
                Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show()
            }
        }
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
        binding.btnBackup.setOnClickListener { backupDatabase() }
        binding.btnRestore.setOnClickListener { openFilePicker() }
    }

    private fun saveSettings() {
        val weight = binding.etWeight.text.toString().toDoubleOrNull()
        val threshold = binding.etThreshold.text.toString().toDoubleOrNull()
        val levoHour = binding.etLevoHour.text.toString().toIntOrNull()

        if (weight == null || weight <= 0) { Toast.makeText(this, "体重无效", Toast.LENGTH_SHORT).show(); return }
        if (threshold == null || threshold < 0 || threshold > 100) { Toast.makeText(this, "阈值需在0-100之间", Toast.LENGTH_SHORT).show(); return }
        if (levoHour == null || levoHour < 0 || levoHour > 23) { Toast.makeText(this, "时间需在0-23之间", Toast.LENGTH_SHORT).show(); return }

        UserPreferences.setWeightKg(this, weight)
        UserPreferences.setReminderThreshold(this, threshold)
        UserPreferences.setLevothyroxineReminderHour(this, levoHour)
        Toast.makeText(this, "✓ 设置已保存", Toast.LENGTH_SHORT).show()
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
        AlertDialog.Builder(this)
            .setTitle("确认清空").setMessage("清空所有崩溃日志？")
            .setPositiveButton("清空") { _, _ -> CrashLogger.clearLog(this); Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("取消", null).show()
    }

    private fun backupDatabase() {
        val uri = BackupHelper.exportDatabaseFile(this)
        if (uri != null) ExportHelper.shareFile(this, uri, "application/octet-stream")
        else Toast.makeText(this, "备份失败", Toast.LENGTH_SHORT).show()
    }

    // 真正的文件选择器
    private fun openFilePicker() {
        AlertDialog.Builder(this)
            .setTitle("恢复数据")
            .setMessage("将从备份文件恢复数据，当前所有记录会被覆盖。确认继续？")
            .setPositiveButton("选择备份文件") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"  // 允许选择任意文件，包括.db
                }
                restoreLauncher.launch(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doRestore(uri: Uri) {
        val success = BackupHelper.importDatabaseFile(this, uri)
        if (success) {
            Toast.makeText(this, "✓ 恢复成功，请重启应用", Toast.LENGTH_LONG).show()
            // 提示重启
            AlertDialog.Builder(this)
                .setTitle("恢复成功")
                .setMessage("数据已恢复，需要重启应用才能生效。")
                .setPositiveButton("确定", null)
                .show()
        } else {
            Toast.makeText(this, "恢复失败，请确认文件格式正确", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
