package com.example.samsungalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.example.samsungalarm.AlarmApp
import com.example.samsungalarm.MainActivity
import com.example.samsungalarm.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟响铃前台服务。
 * 用 MediaPlayer 循环播放铃声，配合持续振动，直到用户点停止。
 * 走 USAGE_ALARM 音频流，所以静音模式下依然会响。
 * 停止后刷新常驻「下一个闹钟」通知，保证过期闹钟及时消失。
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.example.samsungalarm.START_RING"
        const val ACTION_STOP = "com.example.samsungalarm.STOP_RING"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_RINGTONE = "ringtone"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_SNOOZE = "snooze"

        private const val NOTI_ID = 90001
        @Volatile var keepEnabledOnStop = false

        const val CHANNEL_ID = "alarm_ringing"

        fun start(
            context: Context,
            alarmId: Long,
            label: String,
            ringtone: String?,
            vibrate: Boolean,
            snoozeMinutes: Int
        ): Boolean {
            val i = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_RINGTONE, ringtone)
                putExtra(EXTRA_VIBRATE, vibrate)
                putExtra(EXTRA_SNOOZE, snoozeMinutes)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        fun stop(context: Context, keepEnabled: Boolean = false) {
            keepEnabledOnStop = keepEnabled
            context.startService(
                Intent(context, AlarmService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringing = false
    private var currentAlarmId: Long = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopEverything(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_STOP -> {
                if (!keepEnabledOnStop) OneShotFinish.finish(this, currentAlarmId)
                keepEnabledOnStop = false
                stopEverything()
                return START_NOT_STICKY
            }
            else -> {
                val id = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
                val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
                val tone = intent?.getStringExtra(EXTRA_RINGTONE)
                val vib = intent?.getBooleanExtra(EXTRA_VIBRATE, true) ?: true
                val sn = intent?.getIntExtra(EXTRA_SNOOZE, 5) ?: 5
                startRinging(id, label, tone, vib, sn)
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(alarmId: Long, label: String, tone: String?, vibrate: Boolean, snooze: Int) {
        if (ringing) {
            releasePlayer()
            stopVibration()
        }
        ringing = true
        currentAlarmId = alarmId

        acquireWakeLock()
        showForegroundNotification(label)
        launchFullScreen(alarmId, label, snooze)
        ensureAlarmVolume()
        playSound(tone)
        if (vibrate) startVibration()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SamsungAlarm::ringing"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) { }
    }

    private fun showForegroundNotification(label: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 声音交给 MediaPlayer，通知渠道本身静音，避免双重声音
            val ch = NotificationChannel(
                CHANNEL_ID,
                "闹钟响铃",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟正在响铃"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, flags)

        val openIntent = Intent(this, AlarmRingActivity::class.java)
        val openPi = PendingIntent.getActivity(this, 2, openIntent, flags)

        val noti = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("闹钟")
            .setContentText(if (label.isBlank()) "时间到了" else label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .addAction(0, "停止", stopPi)
            .build()

        startForeground(NOTI_ID, noti)
    }

    private fun launchFullScreen(alarmId: Long, label: String, snooze: Int) {
        try {
            val i = Intent(this, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AlarmRingActivity.EXTRA_ID, alarmId)
                putExtra(AlarmRingActivity.EXTRA_LABEL, label)
                putExtra(AlarmRingActivity.EXTRA_SNOOZE, snooze)
            }
            startActivity(i)
        } catch (_: Exception) { }
    }

    private fun ensureAlarmVolume() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = (max * 0.7f).toInt().coerceAtLeast(1)
            if (cur < target) am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (_: Exception) {
        }
    }

    private fun playSound(tone: String?) {
        val uri: Uri = try {
            if (tone.isNullOrBlank()) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } else {
                Uri.parse(tone)
            }
        } catch (e: Exception) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // 指定的铃声读不到就退回默认闹钟音
            try {
                player?.release()
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    setDataSource(
                        this@AlarmService,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) { }
        }
    }

    private fun startVibration() {
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 800, 500, 800, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) { }
    }

    private fun releasePlayer() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) { }
        player = null
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) { }
        vibrator = null
    }

    private fun stopEverything() {
        ringing = false
        releasePlayer()
        stopVibration()

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
        wakeLock = null

        stopForeground(true)
        stopSelf()

        // 停止后刷新常驻通知：一次性闹钟已经响过，不该继续占位
        refreshNextAlarmNotification()
    }

    private fun refreshNextAlarmNotification() {
        val app = applicationContext as? AlarmApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = app.repository.allAlarms()
                val groups = app.repository.allGroups()
                NextAlarmNotifier.update(applicationContext, alarms, groups)
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }
}
