package com.example.drugtracker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.drugtracker.data.MedicationRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ExportHelper {

    fun exportToCSV(context: Context, records: List<MedicationRecord>): Uri? {
        val csvFile = File(context.getExternalFilesDir(null), 
            "drug_records_${System.currentTimeMillis()}.csv")
        
        return try {
            csvFile.writeText(buildCSV(records))
            FileProvider.getUriForFile(context, 
                "${context.packageName}.fileprovider", csvFile)
        } catch (e: Exception) {
            CrashLogger.log("export csv failed", e)
            null
        }
    }

    private fun buildCSV(records: List<MedicationRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("药物名称,剂量,单位,服药时间,记录类型,备注")
        
        records.forEach { record ->
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            val timeStr = sdf.format(Date(record.takenAtMs))
            val typeStr = when(record.recordType) {
                "prescription_regular" -> "处方定期"
                "prn" -> "按需服用"
                "supplement" -> "补充剂"
                else -> "其他"
            }
            sb.appendLine("${record.drugName},${record.doseMg},${record.unit},$timeStr,$typeStr,\"${record.notes}\"")
        }
        
        return sb.toString()
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享文件"))
    }
}