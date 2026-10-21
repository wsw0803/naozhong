package com.example.samsungalarm.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.samsungalarm.AlarmApp
import com.example.samsungalarm.ui.GlassSettingsStore
import com.example.samsungalarm.ui.LocalGlassSettings
import com.example.samsungalarm.ui.LocalGlassStore
import com.example.samsungalarm.ui.theme.AlarmTheme
import com.example.samsungalarm.ui.theme.AppBackground
import com.example.samsungalarm.ui.theme.GlassPrimary
import com.example.samsungalarm.ui.theme.glassBorder
import com.example.samsungalarm.ui.theme.glassFill
import com.example.samsungalarm.ui.theme.glassShadow
import com.example.samsungalarm.ui.theme.textPrimary
import com.example.samsungalarm.ui.theme.textSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmRingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ID = "ring_id"
        const val EXTRA_LABEL = "ring_label"
        const val EXTRA_TIME = "ring_time"
        const val EXTRA_SNOOZE = "ring_snooze"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
        val time = intent?.getStringExtra(EXTRA_TIME).orEmpty()
        val snooze = intent?.getIntExtra(EXTRA_SNOOZE, 5) ?: 5
        ensureRinging(id, label, snooze)
        val store = GlassSettingsStore(applicationContext)
        setContent {
            CompositionLocalProvider(
                LocalGlassSettings provides store.settings,
                LocalGlassStore provides store
            ) {
                AlarmTheme {
                    RingScreen(
                        label = label,
                        time = time,
                        snoozeMinutes = snooze,
                        onStop = { stopRinging() },
                        onSnooze = { doSnooze(id, snooze) }
                    )
                }
            }
        }
    }

    /**
     * 补一次响铃。
     *
     * 兜底通知被点开、或系统把全屏页弹出来时，AlarmService 往往因为
     * 后台限制没起来（这时只有通知响了一声）。Activity 已经在前台了，
     * 此时启动前台服务是完全合法的，所以在这里补上，保证进来一直有声音。
     */
    private fun ensureRinging(id: Long, label: String, snooze: Int) {
        if (id < 0 || AlarmService.isRinging) return
        val app = application as? AlarmApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = app.repository.alarmById(id) ?: return@launch
                val started = AlarmService.start(
                    context = applicationContext,
                    alarmId = alarm.id,
                    label = if (alarm.label.isBlank()) label else alarm.label,
                    ringtone = alarm.ringtoneUri,
                    vibrate = alarm.vibrate,
                    snoozeMinutes = if (snooze > 0) snooze else alarm.snoozeMinutes
                )
                // 服务真的起来了才撤兜底通知，否则会把唯一的声音来源关掉
                if (started) AlarmFallback.dismiss(applicationContext)
            } catch (_: Exception) {
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopRinging() {
        AlarmService.stop(applicationContext)
        AlarmFallback.dismiss(applicationContext)
        finishAndRemoveTask()
    }

    private fun doSnooze(id: Long, minutes: Int) {
        AlarmService.stop(applicationContext, keepEnabled = true)
        AlarmFallback.dismiss(applicationContext)
        if (id >= 0) AlarmScheduler.scheduleSnooze(applicationContext, id, minutes)
        finishAndRemoveTask()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // 全屏响铃页禁止返回键关闭，只能从左滑到右停止
    }
}

@Composable
private fun RingScreen(
    label: String,
    time: String,
    snoozeMinutes: Int,
    onStop: () -> Unit,
    onSnooze: () -> Unit
) {
    BackHandler(enabled = true) { }
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, shape, clip = false, ambientColor = glassShadow(), spotColor = glassShadow())
                    .clip(shape)
                    .background(glassFill())
                    .border(BorderStroke(1.dp, glassBorder()), shape)
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .background(GlassPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = GlassPrimary, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    if (time.isBlank()) currentTime() else time,
                    fontSize = 56.sp, fontWeight = FontWeight.Bold, color = textPrimary()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (label.isBlank()) "时间到了" else label,
                    fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    color = textSecondary(), textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            SlideToAction(text = "滑动停止  →", forward = true, accent = GlassPrimary, onDone = onStop)
            Spacer(Modifier.height(14.dp))
            SlideToAction(text = "←  滑动延迟 " + snoozeMinutes + " 分钟", forward = false, accent = GlassPrimary, onDone = onSnooze)
        }
    }
}

@Composable
private fun SlideToAction(
    text: String,
    forward: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onDone: () -> Unit
) {
    val trackHeight = 66.dp
    val thumb = 54.dp
    val pad = 6.dp
    var widthPx by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbPx = with(density) { thumb.toPx() }
    val padPx = with(density) { pad.toPx() }
    val maxOffset = (widthPx - thumbPx - padPx * 2f).coerceAtLeast(0f)

    fun snapBack() {
        scope.launch {
            val from = offsetX
            for (i in 1..10) {
                offsetX = from * (1f - i / 10f)
                delay(14)
            }
            offsetX = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .height(trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(glassFill())
            .border(BorderStroke(1.dp, glassBorder()), RoundedCornerShape(trackHeight / 2))
            .onSizeChanged { widthPx = it.width }
            .pointerInput(widthPx, forward) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val reached = if (forward) offsetX >= maxOffset * 0.85f else offsetX <= -maxOffset * 0.85f
                        if (maxOffset > 0f && reached) onDone() else snapBack()
                    },
                    onDragCancel = { snapBack() },
                    onHorizontalDrag = { change, drag ->
                        change.consume()
                        offsetX = if (forward) {
                            (offsetX + drag).coerceIn(0f, maxOffset)
                        } else {
                            (offsetX + drag).coerceIn(-maxOffset, 0f)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            fontSize = 16.sp, fontWeight = FontWeight.Medium,
            color = textSecondary(),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .offset {
                    val base = if (forward) padPx else (widthPx - thumbPx - padPx)
                    IntOffset((base + offsetX).roundToInt(), 0)
                }
                .size(thumb)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (forward) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun currentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
