package com.example.samsungalarm.ui.theme

import android.app.Activity
import android.net.Uri
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
val LightTextPrimary = Color(0xFF16181D)
val LightTextSecondary = Color(0xFF6B7280)

val DarkBgTop = Color(0xFF1B2138)
val DarkBgMid = Color(0xFF141828)
val DarkBgBottom = Color(0xFF0E1018)
val DarkTextPrimary = Color(0xFFF2F4F8)
val DarkTextSecondary = Color(0xFF9AA3B2)

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

@Composable
fun isDark(): Boolean = isSystemInDarkTheme()

/** 卡片填充：受设置里的不透明度影响。 */
@Composable
fun glassFill(): Color {
    val s = LocalGlassSettings.current
    return if (isDark()) Color.White.copy(alpha = 0.03f + s.opacity * 0.17f)
    else Color.White.copy(alpha = 0.22f + s.opacity * 0.72f)
}

/** 卡片描边：不透明度越高，描边越清晰。 */
@Composable
fun glassBorder(): Color {
    val s = LocalGlassSettings.current
    return if (isDark()) Color.White.copy(alpha = 0.10f + s.opacity * 0.22f)
    else Color.White.copy(alpha = 0.30f + s.opacity * 0.50f)
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

/**
 * 整屏背景：有自定义图就画图并叠一层压暗；没有就用渐变加光斑。
 * 放在页面最底层。
 */
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
