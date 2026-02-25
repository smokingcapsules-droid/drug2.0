package com.example.drugtracker.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.drugtracker.data.AppDatabase
import java.io.File

object BackupHelper {
    // 导出数据库文件本身（完整备份）
    fun exportDatabaseFile(context: Context): Uri? {
        val dbFile = context.getDatabasePath("drug_tracker_database")
        val exportFile = File(context.getExternalFilesDir(null),
            "drug_tracker_backup_${System.currentTimeMillis()}.db")
        return try {
            dbFile.copyTo(exportFile, overwrite = true)
            FileProvider.getUriForFile(context,
                "${context.packageName}.fileprovider", exportFile)
        } catch (e: Exception) {
            CrashLogger.log("backup failed", e)
            null
        }
    }

    // 导入数据库文件恢复
    fun importDatabaseFile(context: Context, sourceUri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val dbFile = context.getDatabasePath("drug_tracker_database")
            // 关闭当前数据库连接
            AppDatabase.getDatabase(context).close()
            inputStream?.use { it.copyTo(dbFile.outputStream()) }
            true
        } catch (e: Exception) {
            CrashLogger.log("restore failed", e)
            false
        }
    }
}