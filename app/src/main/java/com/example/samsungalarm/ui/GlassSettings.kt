package com.example.samsungalarm.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 玻璃外观设置，用户可调。 */
data class GlassSettings(
    val opacity: Float = 0.65f,
    val bgImageUri: String? = null,
    val bgDim: Float = 0.35f,
    val corner: Float = 22f,
    val darkMode: Int = 0,
    val dpiScale: Float = 1f
)

class GlassSettingsStore(context: Context) {

    private val sp = context.getSharedPreferences("glass_settings", Context.MODE_PRIVATE)

    var settings by mutableStateOf(load())
        private set

    private fun load() = GlassSettings(
        opacity = sp.getFloat("opacity", 0.65f),
        bgImageUri = sp.getString("bgImageUri", null),
        bgDim = sp.getFloat("bgDim", 0.35f),
        corner = sp.getFloat("corner", 22f),
        darkMode = sp.getInt("darkMode", 0),
        dpiScale = sp.getFloat("dpiScale", 1f)
    )

    fun update(new: GlassSettings) {
        settings = new
        sp.edit()
            .putFloat("opacity", new.opacity)
            .putString("bgImageUri", new.bgImageUri)
            .putFloat("bgDim", new.bgDim)
            .putFloat("corner", new.corner)
            .putInt("darkMode", new.darkMode)
            .putFloat("dpiScale", new.dpiScale)
            .apply()
    }

    fun reset() = update(GlassSettings())
}

val LocalGlassSettings = compositionLocalOf { GlassSettings() }
val LocalGlassStore = compositionLocalOf<GlassSettingsStore?> { null }

/** 解码图片并降采样，避免大图直接爆内存。 */
fun loadScaledBitmap(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (e: Exception) {
        null
    }
}
