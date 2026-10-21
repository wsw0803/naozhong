package com.example.samsungalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 应用被从最近任务划掉时，尽快把保活服务拉回来，保证闹钟继续生效。
        // 拉两次：1 秒后先试精确闹钟，20 秒后再兜一次非精确的。
        scheduleSelfResurrection(1000L, preferExact = true)
        scheduleSelfResurrection(20000L, preferExact = false)
        super.onTaskRemoved(rootIntent)
    }

    /**
     * 安排一次「把自己拉起来」。
     *
     * 关键修复：没有「闹钟和提醒」权限时 setExactAndAllowWhileIdle 会抛
     * SecurityException，原来被 catch 吞掉后就再也拉不起来了 —— 划掉任务即死。
     * 现在退到非精确的 setAndAllowWhileIdle，虽然可能晚几分钟，但不会完全失效。
     */
    private fun scheduleSelfResurrection(delayMs: Long, preferExact: Boolean) {
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(this, KeepAliveService::class.java)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            val requestCode = if (preferExact) 91 else 92
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(this, requestCode, i, flags)
            } else {
                PendingIntent.getService(this, requestCode, i, flags)
            }
            val at = System.currentTimeMillis() + delayMs
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (preferExact && canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        try {
            val placeholder = NextAlarmNotifier.buildNotification(
                this, "qian❤️wei", ""
            )
            startForeground(NextAlarmNotifier.NOTI_ID, placeholder)
        } catch (_: Exception) {
            // 通知权限被拒时 startForeground 会抛异常，吞掉否则保活直接失效
        }
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
