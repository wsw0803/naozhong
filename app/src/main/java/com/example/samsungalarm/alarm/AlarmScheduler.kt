package com.example.samsungalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.samsungalarm.data.Alarm
import com.example.samsungalarm.data.AlarmGroup
import com.example.samsungalarm.data.nextTriggerAtMillis
import java.util.Calendar

object AlarmScheduler {
    private const val REQ = 10000
    private const val SHOW = 30000

    fun refreshNotification(c: Context, a: List<Alarm>, g: List<AlarmGroup>) {
        NextAlarmNotifier.update(c, a, g)
    }

    fun rescheduleAll(c: Context, alarms: List<Alarm>, groups: List<AlarmGroup>) {
        val ge = groups.associate { it.id to it.enabled }
        alarms.forEach { al ->
            val snoozeAt = SnoozeStore.get(c, al.id)
            cancel(c, al.id)
            if (al.enabled && (ge[al.groupId] ?: true)) {
                if (snoozeAt != null) {
                    // cancel() 会把延迟记录一起清掉，这里必须补写回去。
                    // 否则常驻通知读不到延迟时间，会显示成闹钟原本的下一次。
                    SnoozeStore.set(c, al.id, snoozeAt)
                    PendingAlarmStore.save(c, al, snoozeAt)
                    arm(c, al.id, snoozeAt)
                } else {
                    val t = al.nextTriggerAtMillis(Calendar.getInstance())
                    if (t != null) {
                        PendingAlarmStore.save(c, al, t)
                        arm(c, al.id, t)
                    } else {
                        PendingAlarmStore.clear(c, al.id)
                    }
                }
            }
        }
        NextAlarmNotifier.update(c, alarms, groups)
    }

    fun schedule(c: Context, alarm: Alarm, at: Long) {
        PendingAlarmStore.save(c, alarm, at)
        arm(c, alarm.id, at)
    }

    fun scheduleSnooze(c: Context, alarmId: Long, minutes: Int) {
        val at = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60000L
        SnoozeStore.set(c, alarmId, at)
        // 只更新时间，标签/铃声等沿用之前登记好的镜像内容
        PendingAlarmStore.updateTriggerAt(c, alarmId, at, minutes)
        arm(c, alarmId, at)
    }

    /**
     * 解锁前的重启恢复。
     * 只读设备加密区的镜像，完全不碰 Room（解锁前读不到），
     * 把还没到点的闹钟重新登记回 AlarmManager。
     */
    fun rearmFromMirror(c: Context) {
        PendingAlarmStore.allPending(c).forEach { p ->
            arm(c, p.id, p.triggerAt)
        }
    }

    private fun arm(c: Context, id: Long, at: Long) {
        val m = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val p = pi(c, id, REQ + id.toInt())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !m.canScheduleExactAlarms())
                m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else
                m.setAlarmClock(AlarmManager.AlarmClockInfo(at, show(c, id)), p)
        } catch (e: Exception) {
            try { m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
            catch (_: Exception) { m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
        }
    }

    fun cancel(c: Context, id: Long) {
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(c, id, REQ + id.toInt()))
        SnoozeStore.clear(c, id)
        PendingAlarmStore.clear(c, id)
    }

    private fun flags(): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or PendingIntent.FLAG_IMMUTABLE
        return f
    }

    private fun pi(c: Context, id: Long, requestCode: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_RING
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, id)
        }
        return PendingIntent.getBroadcast(c, requestCode, i, flags())
    }

    private fun show(c: Context, id: Long): PendingIntent {
        val i = Intent(c, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(c, SHOW + id.toInt(), i, flags())
    }
}
