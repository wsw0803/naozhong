package com.example.samsungalarm.alarm

import android.content.Context
import com.example.samsungalarm.AlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 用户点结束后：一次性闹钟用过即关闭，
 * 避免主界面开关和常驻通知继续把它算成下一次响铃。
 */
object OneShotFinish {
    fun finish(context: Context, alarmId: Long) {
        if (alarmId < 0) return
        val app = context.applicationContext as? AlarmApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = app.repository.alarmById(alarmId) ?: return@launch
                if (alarm.repeatDays.isEmpty() && alarm.enabled) {
                    // 一次性闹钟用完：关掉开关并撤掉已登记的 PendingIntent
                    AlarmScheduler.cancel(context.applicationContext, alarmId)
                    app.repository.setAlarmEnabled(alarmId, false)
                    NextAlarmNotifier.update(
                        context.applicationContext,
                        app.repository.allAlarms(),
                        app.repository.allGroups()
                    )
                }
            } catch (_: Exception) { }
        }
    }
}
