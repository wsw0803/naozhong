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
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RING = "com.example.samsungalarm.RING"
        const val EXTRA_ALARM_ID = "alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RING) return
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

                if (!alarm.enabled || !groupEnabled) return@launch

                // 拉起前台服务，循环播放铃声直到用户停止
                AlarmService.start(
                    context = context,
                    alarmId = alarm.id,
                    label = alarm.label,
                    ringtone = alarm.ringtoneUri,
                    vibrate = alarm.vibrate
                )

                // 重复闹钟安排下一次
                if (alarm.repeatDays.isNotEmpty()) {
                    val next = alarm.nextTriggerAtMillis(Calendar.getInstance())
                    if (next != null) {
                        AlarmScheduler.schedule(context, alarm, next)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
