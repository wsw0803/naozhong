package com.example.samsungalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.samsungalarm.AlarmApp
import com.example.samsungalarm.data.nextTriggerAtMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 闹钟到点时被系统唤醒。
 * 这里只负责查数据、拉起响铃服务、安排下一次，真正的出声在 AlarmService 里。
 * 响铃后立即刷新常驻通知，让已过期的一次性闹钟不再占位。
 * 另外支持一个「仅刷新通知」的动作，用于通知栏随时间自动前移。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RING = "com.example.samsungalarm.RING"
        const val ACTION_REFRESH = "com.example.samsungalarm.REFRESH_NEXT"
        const val EXTRA_ALARM_ID = "alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                refreshOnly(context)
                return
            }
            ACTION_RING -> {
                // 继续走下面的响铃流程
            }
            else -> return
        }

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId < 0) return

        val app = context.applicationContext as AlarmApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = app.repository.alarmById(alarmId) ?: return@launch
                val groups = app.repository.allGroups()
                val groupEnabled =
                    groups.firstOrNull { it.id == alarm.groupId }?.enabled ?: true

                if (!alarm.enabled || !groupEnabled) {
                    NextAlarmNotifier.update(context, app.repository.allAlarms(), groups)
                    return@launch
                }

                SnoozeStore.clear(context, alarm.id)
                AlarmService.start(
                    context = context,
                    alarmId = alarm.id,
                    label = alarm.label,
                    ringtone = alarm.ringtoneUri,
                    vibrate = alarm.vibrate,
                    snoozeMinutes = alarm.snoozeMinutes
                )

                if (alarm.repeatDays.isNotEmpty()) {
                    val next = alarm.nextTriggerAtMillis(Calendar.getInstance())
                    if (next != null) {
                        AlarmScheduler.schedule(context, alarm, next)
                    }
                }

                NextAlarmNotifier.update(context, app.repository.allAlarms(), groups)
            } finally {
                pending.finish()
            }
        }
    }

    /** 仅刷新常驻通知，不响铃。 */
    private fun refreshOnly(context: Context) {
        val app = context.applicationContext as? AlarmApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = app.repository.allAlarms()
                val groups = app.repository.allGroups()
                NextAlarmNotifier.update(context, alarms, groups)
            } finally {
                pending.finish()
            }
        }
    }
}
