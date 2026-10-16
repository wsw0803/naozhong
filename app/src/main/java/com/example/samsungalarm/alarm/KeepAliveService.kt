package com.example.samsungalarm.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.example.samsungalarm.AlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 前台保活服务：把进程提升为前台服务，降低被系统/厂商 ROM 清后台后闹钟失效的概率。
 * 通知直接复用 NextAlarmNotifier 的同一条常驻通知（同一个 id），不额外占一条。
 */
class KeepAliveService : Service() {

    companion object {
        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            val i = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (_: Exception) { }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        val placeholder = NextAlarmNotifier.buildNotification(
            this, "闹钟守护中", "正在守护闹钟"
        )
        startForeground(NextAlarmNotifier.NOTI_ID, placeholder)
        refreshContent()
        return START_STICKY
    }

    private fun refreshContent() {
        val app = applicationContext as? AlarmApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NextAlarmNotifier.update(
                    this@KeepAliveService,
                    app.repository.allAlarms(),
                    app.repository.allGroups(),
                    foreground = true
                )
            } catch (_: Exception) { }
        }
    }
}
