package com.example.samsungalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.samsungalarm.MainActivity
import com.example.samsungalarm.data.Alarm
import com.example.samsungalarm.data.AlarmGroup
import com.example.samsungalarm.data.nextTriggerAtMillis
import java.util.Calendar

/**
 * 常驻通知：显示下一个即将响铃的闹钟。
 * 规则：在所有「已开启且所属分组已开启」的闹钟里，严格按绝对触发时间取最近的一个，
 * 与谁先开启、列表顺序无关；没有任何待响闹钟时移除通知。
 * 文案展示：今天/明天 + 时间 + 分组名 + 标签。
 */
object NextAlarmNotifier {

    private const val CHANNEL_ID = "next_alarm"
    private const val NOTI_ID = 90002

    fun update(context: Context, alarms: List<Alarm>, groups: List<AlarmGroup>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val groupEnabled = groups.associate { it.id to it.enabled }
        val groupName = groups.associate { it.id to it.name }
        val now = Calendar.getInstance()

        var bestAlarm: Alarm? = null
        var bestTime: Long = Long.MAX_VALUE
        alarms.forEach { a ->
            val active = a.enabled && (groupEnabled[a.groupId] ?: true)
            if (active) {
                val t = a.nextTriggerAtMillis(now)
                if (t != null) {
                    if (t < bestTime || (t == bestTime && bestAlarm != null && a.id < bestAlarm!!.id)) {
                        bestTime = t
                        bestAlarm = a
                    }
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
        val nowCal = Calendar.getInstance()
        val sameDay = cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
        val tomorrowCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val isTomorrow = cal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)
        val dayPrefix = when {
            sameDay -> "今天 "
            isTomorrow -> "明天 "
            else -> ""
        }

        val parts = ArrayList<String>()
        parts.add(dayPrefix + timeText)
        val gName = groupName[alarm.groupId]
        if (!gName.isNullOrBlank()) parts.add(gName)
        if (alarm.label.isNotBlank()) parts.add(alarm.label)
        val text = parts.joinToString(" · ")

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
