package com.example.samsungalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.samsungalarm.AlarmApp
import com.example.samsungalarm.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 前台保活服务：常驻一条低优先级通知，把进程提升为前台服务，
 * 降低被系统/厂商 ROM 清后台后闹钟失效的概率。
 * 通知内容复用「下一个闹钟」信息，与 NextAlarmNotifier 共用同一个通知 id。
 */
class KeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "keep_alive"
        const val NOTI_ID = 90003

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTI_ID, buildNotification("正在守护闹钟"))
        refreshContent()
        return START_STICKY
    }

    private fun refreshContent() {
        val app = applicationContext as? AlarmApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = app.repository.allAlarms()
                val groups = app.repository.allGroups()
                val text = nextText(alarms, groups)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTI_ID, buildNotification(text))
            } catch (_: Exception) { }
        }
    }

    private fun nextText(alarms: List<com.example.samsungalarm.data.Alarm>,
                         groups: List<com.example.samsungalarm.data.AlarmGroup>): String {
        val ge = groups.associate { it.id to it.enabled }
        val now = java.util.Calendar.getInstance()
        var best: Long = Long.MAX_VALUE
        var bestLabel = ""
        alarms.forEach { a ->
            if (a.enabled && (ge[a.groupId] ?: true)) {
                val t = com.example.samsungalarm.data.nextTriggerAtMillis(a, now)
                if (t != null && t < best) { best = t; bestLabel = a.label }
            }
        }
        if (best == Long.MAX_VALUE) return "暂无闹钟，守护中"
        val min = (best - System.currentTimeMillis()) / 60000L
        val whenText = if (min <= 0) "即将响铃" else if (min < 60) "${min}分钟后" else "${min / 60}小时${min % 60}分钟后"
        return if (bestLabel.isBlank()) "下一个：$whenText" else "下一个：$whenText · $bestLabel"
    }

    private fun buildNotification(text: String): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "闹钟守护", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持闹钟后台运行"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val openPi = PendingIntent.getActivity(this, 4, Intent(this, MainActivity::class.java), flags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("闹钟守护中")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
