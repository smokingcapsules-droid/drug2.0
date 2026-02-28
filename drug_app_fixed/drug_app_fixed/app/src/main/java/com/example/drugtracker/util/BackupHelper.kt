package com.example.drugtracker.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.drugtracker.data.AppDatabase
import com.example.drugtracker.data.MedicationRecord
import java.io.File

object BackupHelper {

    // ── 导出为CSV（比db文件更通用，任何app都能打开）──────
    suspend fun exportToCSV(context: Context): Uri? {
        return try {
            val db = AppDatabase.getDatabase(context)
            val records = db.medicationDao().getAllRecordsSync()
            val sb = StringBuilder()
            sb.appendLine("id,drugName,doseMg,unit,takenAtMs,takenAtReadable,notes,recordType")
            records.forEach { r ->
                sb.appendLine("${r.id},\"${r.drugName}\",${r.doseMg},${r.unit},${r.takenAtMs},\"${TimeUtils.formatDateTime(r.takenAtMs)}\",\"${r.notes}\",${r.recordType}")
            }
            val file = File(context.getExternalFilesDir(null),
                "drugtracker_backup_${System.currentTimeMillis()}.csv")
            file.writeText(sb.toString())
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            CrashLogger.log("backup csv failed", e)
            null
        }
    }

    // ── 从CSV恢复 ─────────────────────────────────────────
    suspend fun importFromCSV(context: Context, uri: Uri): Pair<Boolean, String> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Pair(false, "无法读取文件")
            val lines = inputStream.bufferedReader().readLines()
            if (lines.isEmpty()) return Pair(false, "文件为空")

            val header = lines[0]
            if (!header.contains("drugName") || !header.contains("doseMg")) {
                return Pair(false, "文件格式不正确，请选择本应用导出的CSV备份文件")
            }

            val records = mutableListOf<MedicationRecord>()
            var skipped = 0
            lines.drop(1).forEach { line ->
                try {
                    val parts = parseCSVLine(line)
                    if (parts.size >= 6) {
                        records.add(MedicationRecord(
                            drugName = parts[1],
                            doseMg = parts[2].toDouble(),
                            unit = parts[3],
                            takenAtMs = parts[4].toLong(),
                            notes = if (parts.size > 6) parts[6] else "",
                            recordType = if (parts.size > 7) parts[7] else "other"
                        ))
                    }
                } catch (e: Exception) { skipped++ }
            }

            if (records.isEmpty()) return Pair(false, "未能解析任何记录")

            val db = AppDatabase.getDatabase(context)
            db.medicationDao().deleteAll()
            records.forEach { db.medicationDao().insert(it) }
            Pair(true, "成功导入 ${records.size} 条记录${if (skipped > 0) "，跳过 $skipped 条无效行" else ""}")
        } catch (e: Exception) {
            CrashLogger.log("restore csv failed", e)
            Pair(false, "恢复失败：${e.message}")
        }
    }

    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    // 保留db备份（给高级用户）
    fun exportDatabaseFile(context: Context): Uri? {
        val dbFile = context.getDatabasePath("drug_tracker_database")
        val exportFile = File(context.getExternalFilesDir(null),
            "drugtracker_db_${System.currentTimeMillis()}.db")
        return try {
            dbFile.copyTo(exportFile, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
        } catch (e: Exception) {
            CrashLogger.log("backup db failed", e)
            null
        }
    }

    fun importDatabaseFile(context: Context, sourceUri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val dbFile = context.getDatabasePath("drug_tracker_database")
            AppDatabase.getDatabase(context).close()
            inputStream?.use { it.copyTo(dbFile.outputStream()) }
            true
        } catch (e: Exception) {
            CrashLogger.log("restore db failed", e)
            false
        }
    }
}
