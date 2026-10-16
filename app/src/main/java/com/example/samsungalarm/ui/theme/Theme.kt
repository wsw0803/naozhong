package com.example.samsungalarm.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.samsungalarm.ui.LocalGlassSettings
import com.example.samsungalarm.ui.loadScaledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val GlassPrimary = Color(0xFF2B6CF6)
val GlassPrimaryDark = Color(0xFF8AB4FF)

val LightBgTop = Color(0xFFDCE7FA)
val LightBgMid = Color(0xFFEDF1F8)
val LightBgBottom = Color(0xFFF7F4FA)

// Light mode text is pure black
val LightTextPrimary = Color(0xFF000000)
val LightTextSecondary = Color(0xFF000000)

val DarkBgTop = Color(0xFF1B2138)
val DarkBgMid = Color(0xFF141828)
val DarkBgBottom = Color(0xFF0E1018)
val DarkTextPrimary = Color(0xFFF7F8FA)
val DarkTextSecondary = Color(0xFFB4BCC9)

val BlobBlue = Color(0x552B6CF6)
val BlobPurple = Color(0x446A5BF7)
val BlobPink = Color(0x33E86FA8)
val BlobCyan = Color(0x3356C8E8)

private val LightColors = lightColorScheme(
    primary = GlassPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFF4C6A92),
    background = LightBgMid,
    surface = Color.White,
    surfaceVariant = Color(0xFFE6EAF2),
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary
)

private val DarkColors = darkColorScheme(
    primary = GlassPrimaryDark,
    onPrimary = Color(0xFF00214D),
    secondary = Color(0xFFB6C6E0),
    background = DarkBgMid,
    surface = Color(0xFF171B21),
    surfaceVariant = Color(0xFF2A313A),
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary
)

// Dark follows manual choice first, else system
@Composable
fun isDark(): Boolean {
    return when (LocalGlassSettings.current.darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
}

@Composable
fun glassFill(): Color {
    val s = LocalGlassSettings.current
    return if (isDark()) Color.White.copy(alpha = 0.02f + s.opacity * 0.28f)
    else Color.White.copy(alpha = 0.04f + s.opacity * 0.66f)
}

@Composable
fun glassBorder(): Color {
    val s = LocalGlassSettings.current
    return if (isDark()) Color.White.copy(alpha = 0.04f + s.opacity * 0.26f)
    else Color.White.copy(alpha = 0.12f + s.opacity * 0.50f)
}

@Composable
fun glassShadow(): Color =
    if (isDark()) Color(0x66000000) else Color(0x1A1B2A4A)

@Composable
fun textPrimary(): Color = if (isDark()) DarkTextPrimary else LightTextPrimary

@Composable
fun textSecondary(): Color = if (isDark()) DarkTextSecondary else LightTextSecondary

@Composable
fun backgroundBrush(): Brush =
    if (isDark()) Brush.verticalGradient(listOf(DarkBgTop, DarkBgMid, DarkBgBottom))
    else Brush.verticalGradient(listOf(LightBgTop, LightBgMid, LightBgBottom))

@Composable
fun AppBackground() {
    val ctx = LocalContext.current
    val s = LocalGlassSettings.current
    val uriStr = s.bgImageUri

    Box(Modifier.fillMaxSize()) {
        if (!uriStr.isNullOrBlank()) {
            var bmp by remember(uriStr) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(uriStr) {
                bmp = withContext(Dispatchers.IO) {
                    try {
                        loadScaledBitmap(ctx, Uri.parse(uriStr))?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            val img = bmp
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (s.bgDim > 0.01f) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.bgDim)))
                }
            } else {
                Box(Modifier.fillMaxSize().background(backgroundBrush()))
            }
        } else {
            Box(Modifier.fillMaxSize().background(backgroundBrush()))
            Box(
                Modifier.size(320.dp).offset(x = (-110).dp, y = (-80).dp)
                    .background(Brush.radialGradient(listOf(BlobBlue, Color.Transparent)), CircleShape)
            )
            Box(
                Modifier.size(280.dp).align(Alignment.TopEnd).offset(x = 80.dp, y = 100.dp)
                    .background(Brush.radialGradient(listOf(BlobPurple, Color.Transparent)), CircleShape)
            )
            Box(
                Modifier.size(300.dp).align(Alignment.BottomStart).offset(x = (-90).dp, y = (-30).dp)
                    .background(Brush.radialGradient(listOf(BlobCyan, Color.Transparent)), CircleShape)
            )
        }
    }
}

@Composable
fun AlarmTheme(
    darkTheme: Boolean = isDark(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT

            // edge-to-edge：内容绘制到状态栏/导航栏后面
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // 窗口背景跟随主题，状态栏/导航栏区域透出它，避免浅色模式固定白条
            window.decorView.setBackgroundColor(
                (if (darkTheme) DarkBgMid else LightBgMid).toArgb()
            )

            // 图标亮暗由当前主题决定，切主题即时生效
            val light = !darkTheme
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }

            // 关闭部分 ROM(含 Android 16) 强制加的对比度遮罩，避免不跟主题的色块
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
