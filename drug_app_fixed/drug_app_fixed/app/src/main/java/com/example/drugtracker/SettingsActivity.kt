package com.example.drugtracker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.drugtracker.databinding.ActivitySettingsBinding
import com.example.drugtracker.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // 文件选择器：支持所有文件类型
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            doRestoreFile(uri)
        } else {
            Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show()
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
        binding.etHeight.setText(UserPreferences.getHeightCm(this).toString())
        binding.etBodyFat.setText(UserPreferences.getBodyFatPercent(this).toString())
        binding.etThreshold.setText(UserPreferences.getReminderThreshold(this).toString())
        binding.etLevoHour.setText(UserPreferences.getLevothyroxineReminderHour(this).toString())
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnExportLog.setOnClickListener { exportCrashLog() }
        binding.btnClearLog.setOnClickListener { clearCrashLog() }
        binding.btnBackup.setOnClickListener { backupCSV() }
        binding.btnRestore.setOnClickListener { startFileRestore() }
    }

    private fun saveSettings() {
        val weight = binding.etWeight.text.toString().toDoubleOrNull()
        val height = binding.etHeight.text.toString().toDoubleOrNull()
        val bodyFat = binding.etBodyFat.text.toString().toDoubleOrNull()
        val threshold = binding.etThreshold.text.toString().toDoubleOrNull()
        val levoHour = binding.etLevoHour.text.toString().toIntOrNull()

        if (weight == null || weight <= 0) { Toast.makeText(this, "体重无效", Toast.LENGTH_SHORT).show(); return }
        if (height == null || height <= 0) { Toast.makeText(this, "身高无效", Toast.LENGTH_SHORT).show(); return }
        if (bodyFat == null || bodyFat < 0 || bodyFat > 100) { Toast.makeText(this, "体脂率需在0-100之间", Toast.LENGTH_SHORT).show(); return }
        if (threshold == null) { Toast.makeText(this, "阈值无效", Toast.LENGTH_SHORT).show(); return }
        if (levoHour == null || levoHour !in 0..23) { Toast.makeText(this, "小时需在0-23之间", Toast.LENGTH_SHORT).show(); return }

        UserPreferences.setWeightKg(this, weight)
        UserPreferences.setHeightCm(this, height)
        UserPreferences.setBodyFatPercent(this, bodyFat)
        UserPreferences.setReminderThreshold(this, threshold)
        UserPreferences.setLevothyroxineReminderHour(this, levoHour)

        Toast.makeText(this, "✓ 已保存", Toast.LENGTH_SHORT).show()
    }

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

    private fun startFileRestore() {
        AlertDialog.Builder(this)
            .setTitle("恢复数据")
            .setMessage("请选择备份文件（.csv 或 .db）\n\n• CSV：从记录恢复\n• DB：直接替换数据库（需重启应用）")
            .setPositiveButton("选择文件") { _, _ ->
                filePickerLauncher.launch(arrayOf("*/*"))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doRestoreFile(uri: Uri) {
        val fileName = getFileName(uri) ?: "unknown"
        when {
            fileName.endsWith(".csv", ignoreCase = true) -> restoreFromCsv(uri)
            fileName.endsWith(".db", ignoreCase = true) || fileName.endsWith(".sqlite", ignoreCase = true) ->
                restoreFromDb(uri)
            else -> Toast.makeText(this, "不支持的文件类型，请选择 .csv 或 .db", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreFromCsv(uri: Uri) {
        lifecycleScope.launch {
            val (success, message) = BackupHelper.importFromCSV(this@SettingsActivity, uri)
            showResultDialog(success, message + if (success) "\n\n请返回主界面查看数据。" else "")
        }
    }

    private fun restoreFromDb(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                BackupHelper.importDatabaseFile(this@SettingsActivity, uri)
            }
            showResultDialog(result.first, result.second)
        }
    }

    private fun showResultDialog(success: Boolean, message: String) {
        AlertDialog.Builder(this)
            .setTitle(if (success) "✓ 成功" else "✗ 失败")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun getFileName(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            }
            "file" -> uri.lastPathSegment
            else -> null
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
