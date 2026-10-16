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

    private const val REQUEST_CODE_BASE = 10000

    fun refreshNotification(context: Context, alarms: List<Alarm>, groups: List<AlarmGroup>) {
        NextAlarmNotifier.update(context, alarms, groups)
    }

    fun rescheduleAll(context: Context, alarms: List<Alarm>, groups: List<AlarmGroup>) {
        val groupEnabled = groups.associate { it.id to it.enabled }
        alarms.forEach { alarm ->
            cancel(context, alarm.id)
            val active = alarm.enabled && (groupEnabled[alarm.groupId] ?: true)
            if (active) {
                val triggerAt = alarm.nextTriggerAtMillis(Calendar.getInstance())
                if (triggerAt != null) schedule(context, alarm, triggerAt)
            }
        }
        NextAlarmNotifier.update(context, alarms, groups)
    }

    fun schedule(context: Context, alarm: Alarm, triggerAtMillis: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, alarm.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, alarmId))
    }

    private fun pendingIntent(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_RING
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE_BASE + alarmId.toInt(), intent, flags)
    }
}
