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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // MODIFIED: 文件选择器改为支持所有文件类型 (*/*)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // 申请持久化权限（Android 10+ 必需）
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
        binding.etThreshold.setText(UserPreferences.getReminderThreshold(this).toString())
        binding.etLevoHour.setText(UserPreferences.getLevothyroxineReminderHour(this).toString())
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnExportLog.setOnClickListener { exportCrashLog() }
        binding.btnClearLog.setOnClickListener { clearCrashLog() }
        binding.btnBackup.setOnClickListener { backupCSV() }
        binding.btnRestore.setOnClickListener { startFileRestore() } // MODIFIED: 方法名变更
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

    // MODIFIED: 恢复入口，允许选择任意文件
    private fun startFileRestore() {
        AlertDialog.Builder(this)
            .setTitle("恢复数据")
            .setMessage("请选择备份文件（.csv 或 .db）\n\n• CSV：从记录恢复\n• DB：直接替换数据库（需重启应用）")
            .setPositiveButton("选择文件") { _, _ ->
                filePickerLauncher.launch(arrayOf("*/*")) // 允许所有文件类型
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // MODIFIED: 根据文件扩展名分流恢复
    private fun doRestoreFile(uri: Uri) {
        val fileName = getFileName(uri) ?: "unknown"
        when {
            fileName.endsWith(".csv", ignoreCase = true) -> restoreFromCsv(uri)
            fileName.endsWith(".db", ignoreCase = true) || fileName.endsWith(".sqlite", ignoreCase = true) ->
                restoreFromDb(uri)
            else -> Toast.makeText(this, "不支持的文件类型，请选择 .csv 或 .db", Toast.LENGTH_LONG).show()
        }
    }

    // MODIFIED: CSV 恢复（复用原有逻辑）
    private fun restoreFromCsv(uri: Uri) {
        lifecycleScope.launch {
            val (success, message) = BackupHelper.importFromCSV(this@SettingsActivity, uri)
            showResultDialog(success, message + if (success) "\n\n请返回主界面查看数据。" else "")
        }
    }

    // MODIFIED: 数据库文件恢复
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

    // MODIFIED: 从 Uri 获取文件名
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
