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
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.example.samsungalarm.AlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

        /** 当前是否真的在响。响铃页据此判断要不要补一次启动，避免重复从头播。 */
        @Volatile var isRinging = false
            private set

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
            try {
                context.startService(
                    Intent(context, AlarmService::class.java).apply { action = ACTION_STOP }
                )
            } catch (_: Exception) {
            }
        }
    }

    private var player: MediaPlayer? = null
    private var toneGen: ToneGenerator? = null
    private var toneJob: Job? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringing = false
    private var currentAlarmId: Long = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopEverything()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                if (!keepEnabledOnStop) OneShotFinish.finish(this, currentAlarmId)
                keepEnabledOnStop = false
                stopEverything()
                return START_NOT_STICKY
            }
            else -> {
                val id = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                val toneUri = intent.getStringExtra(EXTRA_RINGTONE)
                val vib = intent.getBooleanExtra(EXTRA_VIBRATE, true)
                val sn = intent.getIntExtra(EXTRA_SNOOZE, 5)
                startRinging(id, label, toneUri, vib, sn)
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(
        alarmId: Long,
        label: String,
        toneUri: String?,
        vibrate: Boolean,
        snooze: Int
    ) {
        if (ringing) {
            releasePlayer()
            stopVibration()
        }
        ringing = true
        isRinging = true
        currentAlarmId = alarmId

        acquireWakeLock()
        // 前台通知失败（通知被禁、FGS 类型受限、个别 ROM）不能影响响铃，
        // 否则还没出声整个服务就崩了，表现就是「不响」。
        try {
            showForegroundNotification(label)
        } catch (_: Exception) {
            try {
                startForeground(NOTI_ID, buildRingingNotification(label))
            } catch (_: Exception) {
            }
        }
        launchFullScreen(alarmId, label, snooze)
        ensureAlarmVolume()
        playSound(toneUri)
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

        startForeground(NOTI_ID, buildRingingNotification(label))
    }

    private fun buildRingingNotification(label: String): Notification {
        val openPi = PendingIntent.getActivity(
            this,
            2,
            Intent(this, AlarmRingActivity::class.java),
            flags()
        )
        val stopPi = PendingIntent.getService(
            this,
            1,
            Intent(this, AlarmService::class.java).apply { action = ACTION_STOP },
            flags()
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
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
    }

    private fun flags(): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or PendingIntent.FLAG_IMMUTABLE
        return f
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
            // 用户主动按了静音键（音量 0）时不强行掰回来，留给用户自己决定。
            // 需要 MODIFY_AUDIO_SETTINGS 权限，Manifest 里已补上。
            if (am.isStreamMute(AudioManager.STREAM_ALARM)) return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = (max * 0.7f).toInt().coerceAtLeast(1)
            if (cur < target) am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (_: Exception) {
        }
    }

    private fun playSound(toneUri: String?) {
        val uri: Uri? = try {
            if (toneUri.isNullOrBlank()) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } else {
                Uri.parse(toneUri)
            }
        } catch (_: Exception) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

        if (tryPlay(uri)) return
        if (tryPlay(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))) return
        if (tryPlay(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))) return
        // 铃声全都读不出来（自定义铃声被删、媒体库异常等）时，
        // 用 ToneGenerator 直接合成报警音，保证一定有声
        startBeepLoop()
    }

    private fun tryPlay(uri: Uri?): Boolean {
        if (uri == null) return false
        return try {
            releasePlayer()
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
            true
        } catch (_: Exception) {
            releasePlayer()
            false
        }
    }

    private fun startBeepLoop() {
        toneGen = try {
            ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (_: Exception) {
            null
        }
        if (toneGen == null) return
        toneJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                while (ringing) {
                    toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200)
                    delay(1500L)
                }
            } catch (_: Exception) {
            }
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
        try {
            toneJob?.cancel()
        } catch (_: Exception) { }
        toneJob = null
        try {
            toneGen?.release()
        } catch (_: Exception) { }
        toneGen = null
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) { }
        vibrator = null
    }

    private fun stopEverything() {
        ringing = false
        isRinging = false
        releasePlayer()
        stopVibration()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
        wakeLock = null
        try {
            stopForeground(true)
        } catch (_: Exception) { }
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
