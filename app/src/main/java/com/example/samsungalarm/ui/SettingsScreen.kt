package com.example.samsungalarm.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.samsungalarm.ui.theme.AppBackground
import com.example.samsungalarm.ui.theme.GlassPrimary
import com.example.samsungalarm.ui.theme.glassBorder
import com.example.samsungalarm.ui.theme.glassFill
import com.example.samsungalarm.ui.theme.glassShadow
import com.example.samsungalarm.ui.theme.textPrimary
import com.example.samsungalarm.ui.theme.textSecondary

/** 外观设置 + 权限自检。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = LocalGlassStore.current
    val s = LocalGlassSettings.current

    val pickBg = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            store?.update(s.copy(bgImageUri = uri.toString()))
        }
    }

    var notifOk by remember { mutableStateOf(false) }
    var exactOk by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(false) }

    fun refreshPerms() {
        notifOk = PermissionUtils.hasNotification(ctx)
        exactOk = PermissionUtils.canExactAlarm(ctx)
        overlayOk = PermissionUtils.canDrawOverlay(ctx)
        batteryOk = PermissionUtils.ignoringBattery(ctx)
    }

    LaunchedEffect(Unit) { refreshPerms() }

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("外观设置", fontWeight = FontWeight.SemiBold, color = textPrimary()) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textPrimary())
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { inner ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---- 主题模式 ----
                GlassPanel {
                    Text("主题模式", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary())
                    Spacer(Modifier.height(2.dp))
                    Text("控制整体配色和字体颜色", fontSize = 11.sp, color = textSecondary())
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("跟随系统", s.darkMode == 0) { store?.update(s.copy(darkMode = 0)) }
                        ThemeChip("浅色", s.darkMode == 1) { store?.update(s.copy(darkMode = 1)) }
                        ThemeChip("深色", s.darkMode == 2) { store?.update(s.copy(darkMode = 2)) }
                    }
                }

                // ---- 权限自检 ----
                GlassPanel {
                    Text("权限自检", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary())
                    Spacer(Modifier.height(2.dp))
                    Text("缺哪个点哪项去开，开完回来会自动刷新", fontSize = 11.sp, color = textSecondary())
                    Spacer(Modifier.height(10.dp))

                    PermRow("通知权限", notifOk) { PermissionUtils.openNotificationSettings(ctx) }
                    PermRow("精确闹钟", exactOk) { PermissionUtils.openExactAlarmSettings(ctx) }
                    PermRow("悬浮窗", overlayOk) { PermissionUtils.openOverlaySettings(ctx) }
                    PermRow("后台运行（电池优化白名单）", batteryOk) { PermissionUtils.openBatterySettings(ctx) }
                    PermRow("后台弹出界面（部分国产 ROM）", null) { PermissionUtils.openAppDetailSettings(ctx) }

                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { refreshPerms() }) {
                        Text("重新检测", color = GlassPrimary)
                    }
                }

                // ---- 玻璃透明度 ----
                GlassPanel {
                    Text("玻璃透明度", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary())
                    Spacer(Modifier.height(2.dp))
                    Text("越低越透，越高越接近实心卡片", fontSize = 11.sp, color = textSecondary())
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = s.opacity,
                            onValueChange = { v -> store?.update(s.copy(opacity = v)) },
                            valueRange = 0.15f..1f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = GlassPrimary, activeTrackColor = GlassPrimary)
                        )
                        Text("${(s.opacity * 100).toInt()}%", fontSize = 13.sp, color = textPrimary(), modifier = Modifier.padding(start = 10.dp))
                    }
                }

                // ---- 背景 ----
                GlassPanel {
                    Text("背景图片", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary())
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (s.bgImageUri.isNullOrBlank()) "当前使用默认渐变背景" else "已设置自定义背景",
                        fontSize = 11.sp, color = textSecondary()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { pickBg.launch(arrayOf("image/*")) }) {
                            Text("选择图片", color = GlassPrimary)
                        }
                        if (!s.bgImageUri.isNullOrBlank()) {
                            TextButton(onClick = { store?.update(s.copy(bgImageUri = null)) }) {
                                Text("恢复默认", color = textSecondary())
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("背景压暗", fontSize = 13.sp, color = textPrimary())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = s.bgDim,
                            onValueChange = { v -> store?.update(s.copy(bgDim = v)) },
                            valueRange = 0f..0.8f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = GlassPrimary, activeTrackColor = GlassPrimary)
                        )
                        Text("${(s.bgDim * 100).toInt()}%", fontSize = 13.sp, color = textPrimary(), modifier = Modifier.padding(start = 10.dp))
                    }
                }

                // ---- 圆角 ----
                GlassPanel {
                    Text("卡片圆角", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = s.corner,
                            onValueChange = { v -> store?.update(s.copy(corner = v)) },
                            valueRange = 0f..32f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = GlassPrimary, activeTrackColor = GlassPrimary)
                        )
                        Text("${s.corner.toInt()}", fontSize = 13.sp, color = textPrimary(), modifier = Modifier.padding(start = 10.dp))
                    }
                }

                TextButton(onClick = { store?.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("恢复全部默认", color = textSecondary())
                }

                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

/** 一行权限状态：ok 为 null 表示无法标准检测。 */
@Composable
private fun PermRow(name: String, ok: Boolean?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 13.sp, color = textPrimary(), modifier = Modifier.weight(1f))
        val (label, color) = when (ok) {
            true -> "已开启" to Color(0xFF2FA84F)
            false -> "去开启" to Color(0xFFE0533D)
            null -> "需手动" to GlassPrimary
        }
        Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ThemeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) GlassPrimary else glassFill())
            .border(BorderStroke(1.dp, if (selected) GlassPrimary else glassBorder()), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = if (selected) Color.White else textSecondary(),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun GlassPanel(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, clip = false, ambientColor = glassShadow(), spotColor = glassShadow())
            .clip(shape)
            .background(glassFill())
            .border(BorderStroke(1.dp, glassBorder()), shape)
            .padding(16.dp)
    ) {
        content()
    }
}

/** 权限/系统设置跳转与检测。 */
object PermissionUtils {

    fun hasNotification(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun canExactAlarm(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun canDrawOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun ignoringBattery(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun openNotificationSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        safeStart(ctx, i)
    }

    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            safeStart(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${ctx.packageName}")))
        } else {
            openAppDetailSettings(ctx)
        }
    }

    fun openOverlaySettings(ctx: Context) {
        safeStart(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setData(Uri.parse("package:${ctx.packageName}")))
    }

    fun openBatterySettings(ctx: Context) {
        safeStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openAppDetailSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${ctx.packageName}"))
        safeStart(ctx, i)
    }

    private fun safeStart(ctx: Context, intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
