package com.example.samsungalarm.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        finishAndRemoveTask()
    }

    private fun doSnooze(id: Long, minutes: Int) {
        AlarmService.stop(applicationContext)
        if (id >= 0) AlarmScheduler.scheduleSnooze(applicationContext, id, minutes)
        finishAndRemoveTask()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
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
            Button(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = glassFill(), contentColor = textPrimary()
                ),
                border = BorderStroke(1.dp, glassBorder())
            ) {
                Text("延迟 ${snoozeMinutes} 分钟", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassPrimary)
            ) {
                Text("停止", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

private fun currentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
