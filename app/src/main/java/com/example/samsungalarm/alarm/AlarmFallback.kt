package com.example.samsungalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * 末级兜底通道：完全不依赖前台服务，也不依赖后台弹出 Activity。
 *
 * 部分 ROM（小米 / ColorOS / HyperOS 等）会直接拦掉「后台启动前台服务」，
 * 这时 AlarmService 根本起不来；而原先唯一的退路 startActivity 在 Android 10+
 * 同样被禁，于是一点声音都没有 —— 表现就是「闹钟不响」。
 *
 * 这里改由 BroadcastReceiver 直接弹一条全屏闹钟通知：
 * 通知声音走 USAGE_ALARM，一定能出声；锁屏也能亮屏，点开就是响铃页。
 */
object AlarmFallback {

    const val CHANNEL_ID = "alarm_fallback"
    const val NOTI_ID = 90003

    fun show(context: Context, alarmId: Long, label: String, snoozeMinutes: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(nm)

            val openPi = PendingIntent.getActivity(
                context,
                9101,
                Intent(context, AlarmRingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(AlarmRingActivity.EXTRA_ID, alarmId)
                    putExtra(AlarmRingActivity.EXTRA_LABEL, label)
                    putExtra(AlarmRingActivity.EXTRA_SNOOZE, snoozeMinutes)
                },
                flags()
            )

            val stopPi = PendingIntent.getBroadcast(
                context,
                9102,
                Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_DISMISS
                    putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                },
                flags()
            )

            val snoozePi = PendingIntent.getBroadcast(
                context,
                9103,
                Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_SNOOZE
                    putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
                },
                flags()
            )

            val noti = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("闹钟")
                .setContentText(if (label.isBlank()) "时间到了" else label)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(openPi)
                .setFullScreenIntent(openPi, true)
                .addAction(0, "停止", stopPi)
                .addAction(0, "稍后提醒", snoozePi)
                .build()

            nm.notify(NOTI_ID, noti)
        } catch (_: Exception) {
        }
    }

    fun dismiss(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTI_ID)
        } catch (_: Exception) {
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val alarmSound: Uri? = try {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        } catch (_: Exception) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        val ch = NotificationChannel(
            CHANNEL_ID,
            "闹钟兜底提醒",
            NotificationManager.IMPORTANCE_MAX
        ).apply {
            description = "前台服务被系统拦截时的兜底响铃"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 500, 800, 500)
            if (alarmSound != null) {
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        }
        nm.createNotificationChannel(ch)
    }

    private fun flags(): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or PendingIntent.FLAG_IMMUTABLE
        return f
    }
}
