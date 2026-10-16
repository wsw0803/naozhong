package com.example.samsungalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.app.Notification
import com.example.samsungalarm.MainActivity
import com.example.samsungalarm.data.Alarm
import com.example.samsungalarm.data.AlarmGroup
import com.example.samsungalarm.data.nextTriggerAtMillis
import java.util.Calendar

/**
 * 常驻通知：显示下一个即将响铃的闹钟。
 * 无待响闹钟时自动移除。
 */
object NextAlarmNotifier {

    private const val CHANNEL_ID = "next_alarm"
    private const val NOTI_ID = 90002

    fun update(context: Context, alarms: List<Alarm>, groups: List<AlarmGroup>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val groupEnabled = groups.associate { it.id to it.enabled }
        val now = Calendar.getInstance()

        var bestAlarm: Alarm? = null
        var bestTime: Long = Long.MAX_VALUE
        alarms.forEach { a ->
            val active = a.enabled && (groupEnabled[a.groupId] ?: true)
            if (active) {
                val t = a.nextTriggerAtMillis(now)
                if (t != null && t < bestTime) {
                    bestTime = t
                    bestAlarm = a
                }
            }
        }

        val alarm = bestAlarm
        if (alarm == null) {
            nm.cancel(NOTI_ID)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "待响闹钟",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "显示下一个即将响铃的闹钟"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        val cal = Calendar.getInstance().apply { timeInMillis = bestTime }
        val timeText = String.format(
            "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
        val text = if (alarm.label.isBlank()) timeText else timeText + " · " + alarm.label

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        val openPi = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java),
            flags
        )

        val noti = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("下一个闹钟")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi)
            .build()

        try {
            nm.notify(NOTI_ID, noti)
        } catch (_: Exception) {
        }
    }
}
