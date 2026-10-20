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
    private const val SNOOZE = 1000000

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
                    setAlarm(c, al.id, snoozeAt, REQ + al.id.toInt())
                } else {
                    val t = al.nextTriggerAtMillis(Calendar.getInstance())
                    if (t != null) setAlarm(c, al.id, t, REQ + al.id.toInt())
                }
            }
        }
        NextAlarmNotifier.update(c, alarms, groups)
    }

    fun schedule(c: Context, alarm: Alarm, at: Long) = setAlarm(c, alarm.id, at, REQ + alarm.id.toInt())

    fun scheduleSnooze(c: Context, alarmId: Long, minutes: Int) {
        val at = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60000L
        SnoozeStore.set(c, alarmId, at)
        setAlarm(c, alarmId, at, REQ + alarmId.toInt())
    }

    private fun setAlarm(c: Context, id: Long, at: Long, requestCode: Int) {
        val m = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val p = pi(c, id, requestCode)
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
