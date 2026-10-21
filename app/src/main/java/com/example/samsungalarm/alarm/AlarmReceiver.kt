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

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RING = "com.example.samsungalarm.RING"
        const val ACTION_REFRESH = "com.example.samsungalarm.REFRESH_NEXT"

        /** 兜底通知里的「停止」按钮，不需要 Service 也能处理。 */
        const val ACTION_DISMISS = "com.example.samsungalarm.DISMISS"

        /** 兜底通知里的「稍后提醒」按钮。 */
        const val ACTION_SNOOZE = "com.example.samsungalarm.SNOOZE"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                refreshOnly(context)
                return
            }
            ACTION_DISMISS -> {
                AlarmFallback.dismiss(context)
                // 兜底通知和响铃服务可能同时存在（服务晚一步才起来），
                // 点「停止」必须把声音一起停掉，否则通知没了声音还在响。
                AlarmService.stop(context)
                OneShotFinish.finish(context, intent.getLongExtra(EXTRA_ALARM_ID, -1L))
                refreshOnly(context)
                return
            }
            ACTION_SNOOZE -> {
                val id = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
                AlarmFallback.dismiss(context)
                // keepEnabled = true：延迟不属于「用完了」，别让一次性闹钟被关掉
                AlarmService.stop(context, keepEnabled = true)
                if (id >= 0) AlarmScheduler.scheduleSnooze(context, id, minutes)
                refreshOnly(context)
                return
            }
            ACTION_RING -> {
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

                // 先把下一次登记好，即使本次响铃被系统限制，循环也不会断
                if (alarm.repeatDays.isNotEmpty()) {
                    val next = alarm.nextTriggerAtMillis(Calendar.getInstance())
                    if (next != null) {
                        AlarmScheduler.schedule(context, alarm, next)
                    }
                }

                AlarmFallback.dismiss(context)

                val started = AlarmService.start(
                    context = context,
                    alarmId = alarm.id,
                    label = alarm.label,
                    ringtone = alarm.ringtoneUri,
                    vibrate = alarm.vibrate,
                    snoozeMinutes = alarm.snoozeMinutes
                )
                if (!started) {
                    // 前台服务被 ROM 拦下：改走完全不依赖 Service 的兜底通道，
                    // 至少保证有声、能亮屏；再尽量把响铃页也拉起来
                    AlarmFallback.show(context, alarm.id, alarm.label, alarm.snoozeMinutes)
                    try {
                        context.startActivity(
                            Intent(context, AlarmRingActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                putExtra(AlarmRingActivity.EXTRA_ID, alarm.id)
                                putExtra(AlarmRingActivity.EXTRA_LABEL, alarm.label)
                                putExtra(AlarmRingActivity.EXTRA_SNOOZE, alarm.snoozeMinutes)
                            }
                        )
                    } catch (_: Exception) {
                    }
                }

                NextAlarmNotifier.update(context, app.repository.allAlarms(), groups)
            } finally {
                pending.finish()
            }
        }
    }

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
