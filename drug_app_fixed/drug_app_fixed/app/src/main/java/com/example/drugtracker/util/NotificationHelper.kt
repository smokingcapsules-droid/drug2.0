package com.example.drugtracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.drugtracker.MainActivity
import com.example.drugtracker.R

fun showNotification(
    context: Context,
    channelId: String,
    title: String,
    body: String,
    drugName: String,
    priority: Int = NotificationCompat.PRIORITY_DEFAULT
) {
    createNotificationChannels(context)

    // 点击通知打开App并跳转到快速记录
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra("quick_record_drug", drugName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context, drugName.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_pill)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(priority)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    androidx.core.app.NotificationManagerCompat.from(context).notify(drugName.hashCode(), notification)
}

fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // 普通浓度提醒
        NotificationChannel("concentration_reminder", "浓度提醒",
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "药物浓度即将降至阈值时提醒"
            manager.createNotificationChannel(this)
        }

        // 关键药物提醒（高优先级）
        NotificationChannel("critical_reminder", "关键药物提醒",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "优甲乐等关键药物的每日提醒"
            enableLights(true)
            lightColor = Color.RED
            manager.createNotificationChannel(this)
        }
    }
}