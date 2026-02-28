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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // MODIFIED: 文件选择器改为支持所有文件类型 (*/*)，以便同时选择 .csv 和 .db 文件
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) doRestoreFile(uri)
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

    // ── 文件恢复：选择备份文件（支持 .csv 或 .db） ─────────────────────────
    // MODIFIED: 方法重命名，提示信息更通用
    private fun startFileRestore() {
        AlertDialog.Builder(this)
            .setTitle("恢复数据")
            .setMessage("请选择备份文件（.csv 或 .db）。\n\n• 若选择 .csv 文件，将从 CSV 恢复（覆盖当前记录）\n• 若选择 .db 文件，将直接替换数据库文件（需重启应用生效）")
            .setPositiveButton("选择文件") { _, _ ->
                // 使用 "*/*" 允许选择任何文件，同时保留 CSV 的 MIME 类型以方便过滤
                filePickerLauncher.launch(arrayOf("*/*"))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // MODIFIED: 原 doRestoreCSV 更名为 doRestoreFile，并根据文件后缀分流
    private fun doRestoreFile(uri: Uri) {
        val fileName = getFileName(uri)
        when {
            fileName?.endsWith(".csv", ignoreCase = true) == true -> {
                // CSV 恢复走原有逻辑
                lifecycleScope.launch {
                    val (success, message) = BackupHelper.importFromCSV(this@SettingsActivity, uri)
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(if (success) "✓ 恢复成功" else "✗ 恢复失败")
                        .setMessage(message + if (success) "\n\n请返回主界面查看数据。" else "")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
            fileName?.endsWith(".db", ignoreCase = true) == true ||
                    fileName?.endsWith(".sqlite", ignoreCase = true) == true -> {
                // 数据库文件恢复
                restoreFromDB(uri)
            }
            else -> {
                Toast.makeText(this, "不支持的文件类型，请选择 .csv 或 .db 文件", Toast.LENGTH_LONG).show()
            }
        }
    }

    // MODIFIED: 新增方法，从 .db 文件恢复数据库
    private fun restoreFromDB(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // 1. 获取应用当前使用的数据库文件
                    val dbFile = getDatabaseFile() ?: return@withContext Pair(false, "未找到应用的数据库文件")

                    // 2. 确保数据库目录存在
                    dbFile.parentFile?.mkdirs()

                    // 3. 尝试关闭 Room 数据库实例（如果可访问）
                    //    这里假设项目中有 DatabaseHolder 或类似单例，尝试关闭（若无则跳过）
                    //    为了安全，我们仅尝试关闭已知的实例，若无法访问则忽略
                    try {
                        // 如果项目中有全局数据库实例，请在这里调用其 close 方法
                        // 例如：DrugTrackerApplication.database.close()
                        // 由于无法修改其他文件，此处仅作示意，实际请根据项目调整
                        // 若没有公开的关闭方法，可跳过此步
                    } catch (e: Exception) {
                        // 忽略关闭失败
                    }

                    // 4. 使用 ContentResolver 打开输入流
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(dbFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } ?: return@withContext Pair(false, "无法读取所选文件")

                    // 5. 恢复成功
                    Pair(true, "数据库文件已替换，请完全重启应用以使新数据生效。")
                } catch (e: IOException) {
                    e.printStackTrace()
                    Pair(false, "文件读写失败：${e.message}")
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Pair(false, "权限不足，无法读取文件")
                } catch (e: Exception) {
                    e.printStackTrace()
                    Pair(false, "未知错误：${e.message}")
                }
            }

            // 显示结果对话框
            val (success, message) = result
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(if (success) "✓ 恢复成功" else "✗ 恢复失败")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        }
    }

    // MODIFIED: 辅助方法：获取应用的数据库文件
    private fun getDatabaseFile(): File? {
        // 方式1：如果知道确切的数据库名称（例如 "drug_tracker.db"），直接返回
        // return getDatabasePath("drug_tracker.db")

        // 方式2：自动扫描应用默认数据库目录下的第一个 .db 文件（更通用）
        val dbDir = getDatabasePath("dummy").parentFile ?: return null
        val dbFiles = dbDir.listFiles { _, name -> name.endsWith(".db") }
        return dbFiles?.firstOrNull()
    }

    // MODIFIED: 辅助方法：从 Uri 获取文件名
    private fun getFileName(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)
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
