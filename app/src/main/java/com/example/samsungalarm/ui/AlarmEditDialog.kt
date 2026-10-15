package com.example.samsungalarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.samsungalarm.data.Alarm
import com.example.samsungalarm.ui.theme.GlassPrimary
import com.example.samsungalarm.ui.theme.glassFill
import com.example.samsungalarm.ui.theme.isDark
import com.example.samsungalarm.ui.theme.textPrimary
import com.example.samsungalarm.ui.theme.textSecondary

private val ITEM_H = 46.dp

/** 新建 / 编辑闹钟：滚轮与直接输入两种方式，可选铃声。 */
@Composable
fun AlarmEditDialog(
    alarm: Alarm?,
    groupId: Long,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit
) {
    var hour by remember { mutableStateOf(alarm?.hour ?: 7) }
    var minute by remember { mutableStateOf(alarm?.minute ?: 0) }
    var label by remember { mutableStateOf(alarm?.label ?: "") }
    var vibrate by remember { mutableStateOf(alarm?.vibrate ?: true) }
    var days by remember { mutableStateOf(alarm?.repeatDays ?: emptySet()) }
    var ringtone by remember { mutableStateOf(alarm?.ringtoneUri) }
    var mode by remember { mutableStateOf(0) } // 0=滚轮 1=直接输入
    var textTime by remember { mutableStateOf("") }

    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringtone = uri?.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (alarm == null) "新建闹钟" else "编辑闹钟",
                color = textPrimary(),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                // ---- 切换标签 ----
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeTab("滚轮调节", mode == 0) { mode = 0 }
                    ModeTab("直接输入", mode == 1) {
                        textTime = String.format("%02d:%02d", hour, minute)
                        mode = 1
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (mode == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Wheel(
                            count = 24,
                            initial = hour,
                            modifier = Modifier.width(76.dp),
                            onChanged = { hour = it }
                        )
                        Text(
                            ":",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary(),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        Wheel(
                            count = 60,
                            initial = minute,
                            modifier = Modifier.width(76.dp),
                            onChanged = { minute = it }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = textTime,
                        onValueChange = { textTime = it },
                        singleLine = true,
                        label = { Text("小时:分钟") },
                        placeholder = { Text("例如 07:30") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "格式 24 小时制，中间用冒号隔开",
                        fontSize = 11.sp,
                        color = textSecondary()
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ---- 重复 ----
                Text("重复", fontSize = 12.sp, color = textSecondary())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 1..7) {
                        DayChip(
                            label = dayLabel(d),
                            selected = days.contains(d)
                        ) {
                            days = if (days.contains(d)) days - d else days + d
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ---- 标签 ----
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("标签") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                // ---- 铃声 ----
                Text("铃声", fontSize = 12.sp, color = textSecondary())
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        ringtoneName(ctx, ringtone),
                        fontSize = 14.sp,
                        color = textPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
                            if (ringtone != null) {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    Uri.parse(ringtone)
                                )
                            }
                        }
                        picker.launch(i)
                    }) { Text("选择", color = GlassPrimary) }
                }

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = vibrate,
                        onClick = { vibrate = !vibrate },
                        label = { Text("振动") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var h = hour
                var m = minute
                if (mode == 1) {
                    val parts = textTime.trim().split(":", "：")
                    if (parts.size == 2) {
                        val ph = parts[0].trim().toIntOrNull()
                        val pm = parts[1].trim().toIntOrNull()
                        if (ph != null && pm != null && ph in 0..23 && pm in 0..59) {
                            h = ph
                            m = pm
                        }
                    }
                }
                val base = alarm ?: Alarm(groupId = groupId, hour = h, minute = m)
                onSave(
                    base.copy(
                        hour = h,
                        minute = m,
                        label = label.trim(),
                        vibrate = vibrate,
                        repeatDays = days,
                        ringtoneUri = ringtone
                    )
                )
            }) { Text("保存", color = GlassPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = textSecondary()) }
        },
        containerColor = if (isDark()) Color(0xFF1B1F2A) else Color(0xFFF7F9FC)
    )
}

/** 顶部两个切换标签。 */
@Composable
private fun ModeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) GlassPrimary else glassFill())
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = if (selected) Color.White else textSecondary(),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/** 星期小圆片。 */
@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) GlassPrimary else glassFill())
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = if (selected) Color.White else textSecondary()
        )
    }
}

/**
 * 滚轮选择器。
 * 用 LazyColumn 的吸附滚动实现，中间那道就是当前值。
 */
@Composable
private fun Wheel(
    count: Int,
    initial: Int,
    modifier: Modifier = Modifier,
    onChanged: (Int) -> Unit
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val idx = state.firstVisibleItemIndex
                        .coerceIn(0, count - 1)
                    onChanged(idx)
                }
            }
    }

    Box(modifier.height(ITEM_H * 5), contentAlignment = Alignment.Center) {
        // 中间高亮条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_H)
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(glassFill())
        )
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = ITEM_H * 2),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state)
        ) {
            items((0 until count).toList()) { i ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_H),
                    contentAlignment = Alignment.Center
                ) {
                    val selected = state.firstVisibleItemIndex == i &&
                            !state.isScrollInProgress
                    Text(
                        text = if (i < 10) "0$i" else "$i",
                        fontSize = if (selected) 26.sp else 21.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) textPrimary() else textSecondary(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun dayLabel(d: Int): String = when (d) {
    1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"
    5 -> "五"; 6 -> "六"; else -> "日"
}

/** 把铃声 URI 转成用户看得懂的名字。 */
private fun ringtoneName(ctx: android.content.Context, uri: String?): String {
    if (uri.isNullOrBlank()) return "系统默认"
    return try {
        val u = Uri.parse(uri)
        val r = RingtoneManager.getRingtone(ctx, u)
        r?.getTitle(ctx) ?: "已选择"
    } catch (e: Exception) {
        "已选择"
    }
}
