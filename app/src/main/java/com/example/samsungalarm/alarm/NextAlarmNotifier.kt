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
 * 规则：
 *  - 只要闹钟开启且所属分组开启，就参与候选（过期的也参与，自动顺延到下一次）。
 *  - 在所有候选里取「下一次触发时间」最近的那个，与谁先开启、列表顺序无关。
 *  - 文案显示还剩多久，例如「23小时53分钟后 · 分组 · 标签」。
 *  - 到点后自动刷新，开关闹钟也会重新刷新。
 */
object NextAlarmNotifier {

    private const val CHANNEL_ID = "next_alarm"
    private const val NOTI_ID = 90002
    private const val REFRESH_REQUEST = 990001

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
                val t = nextTriggerIncludingPast(a, now)
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

        val parts = ArrayList<String>()
        parts.add(remainingText(bestTime - System.currentTimeMillis()))
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

        scheduleAutoRefresh(context, bestTime)
    }

    /**
     * 计算闹钟的下一次触发时间：
     *  - 重复闹钟：交给 nextTriggerAtMillis，自然顺延到下一个重复日。
     *  - 一次性闹钟：今天的时间点已过就顺延到明天同一时刻（保证始终显示未来最近的那个）。
     */
    private fun nextTriggerIncludingPast(a: Alarm, now: Calendar): Long? {
        if (a.repeatDays.isNotEmpty()) {
            return a.nextTriggerAtMillis(now)
        }
        val cal = now.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, a.hour)
        cal.set(Calendar.MINUTE, a.minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now.timeInMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** 把剩余毫秒转成「X天X小时后 / X小时X分钟后 / X分钟后 / 即将响铃」。 */
    private fun remainingText(remainMs: Long): String {
        if (remainMs <= 0) return "即将响铃"
        val totalMin = remainMs / 60000L
        return when {
            totalMin < 1 -> "即将响铃"
            totalMin < 60 -> totalMin.toString() + "分钟后"
            totalMin < 1440 -> (totalMin / 60).toString() + "小时" + (totalMin % 60).toString() + "分钟后"
            else -> (totalMin / 1440).toString() + "天" + ((totalMin % 1440) / 60).toString() + "小时后"
        }
    }

    private fun scheduleAutoRefresh(context: Context, triggerAt: Long) {
        try {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_REFRESH
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            val pi = PendingIntent.getBroadcast(context, REFRESH_REQUEST, intent, flags)
            val at = triggerAt + 3000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: Exception) {
        }
    }
}
