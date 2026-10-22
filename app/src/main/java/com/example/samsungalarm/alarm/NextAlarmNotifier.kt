package com.example.samsungalarm.alarm

import android.app.AlarmManager
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
 * 同时被前台保活服务复用（foreground = true 时无闹钟显示占位，不 cancel 通知）。
 */
object NextAlarmNotifier {

    const val CHANNEL_ID = "next_alarm"
    const val NOTI_ID = 90002
    private const val REFRESH_REQUEST = 990001

    fun update(
        context: Context,
        alarms: List<Alarm>,
        groups: List<AlarmGroup>,
        foreground: Boolean = false
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val groupEnabled = groups.associate { it.id to it.enabled }
        val groupName = groups.associate { it.id to it.name }
        val now = Calendar.getInstance()

        var bestAlarm: Alarm? = null
        var bestTime = Long.MAX_VALUE
        var bestSnooze: Long? = null
        alarms.forEach { a ->
            if (a.enabled && (groupEnabled[a.groupId] ?: true)) {
                val sn = SnoozeStore.get(context, a.id)
                if (sn != null) {
                    if (sn < bestTime) {
                        bestTime = sn
                        bestAlarm = a
                        bestSnooze = sn
                    }
                    return@forEach
                }
                // 统一走同一个计算，一次性闹钟时间已过也会顺延到明天，
                // 不会再出现「闹钟已开启但通知里什么都算不出来」的情况。
                val t = a.nextTriggerAtMillis(now)
                if (t != null && t < bestTime) {
                    bestTime = t
                    bestAlarm = a
                }
            }
        }

        ensureChannel(nm)

        val alarm = bestAlarm
        if (alarm == null) {
            if (foreground || KeepAliveService.running) {
                safeNotify(nm, buildNotification(context, "qian❤️wei", ""))
            } else {
                nm.cancel(NOTI_ID)
            }
            return
        }

        val parts = ArrayList<String>(3)
        groupName[alarm.groupId]?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        if (alarm.label.isNotBlank()) parts.add(alarm.label)
        if (bestSnooze != null) parts.add("（延迟）")
        safeNotify(
            nm,
            buildNotification(
                context,
                "wei❤️qian",
                if (parts.isEmpty()) "" else parts.joinToString(" · "),
                countDownTo = bestTime
            )
        )

        scheduleAutoRefresh(context, bestTime)
    }

    private fun safeNotify(nm: NotificationManager, noti: android.app.Notification) {
        try { nm.notify(NOTI_ID, noti) } catch (_: Exception) { }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "待响闹钟", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "显示下一个即将响铃的闹钟"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun buildNotification(
        context: Context, title: String, text: String, countDownTo: Long? = null
    ): android.app.Notification {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val openPi = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java), flags
        )
        val noti = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi)
            .apply {
                if (countDownTo != null) {
                    setWhen(countDownTo)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setShowWhen(true)
                }
            }
            .build()
        return noti
    }

    private fun scheduleAutoRefresh(context: Context, triggerAt: Long) {
        try {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_REFRESH
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(context, REFRESH_REQUEST, intent, flags)
            val at = triggerAt + 3000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: Exception) { }
    }
}
