package com.example.samsungalarm.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.samsungalarm.ui.theme.AppBackground
import com.example.samsungalarm.ui.theme.GlassPrimary
import com.example.samsungalarm.ui.theme.glassBorder
import com.example.samsungalarm.ui.theme.glassFill
import com.example.samsungalarm.ui.theme.glassShadow
import com.example.samsungalarm.ui.theme.isDark
import com.example.samsungalarm.ui.theme.textPrimary
import com.example.samsungalarm.ui.theme.textSecondary

/** 外观设置：调玻璃透明度、换背景图。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = LocalGlassStore.current
    val s = LocalGlassSettings.current

    val pickBg = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            store?.update(s.copy(bgImageUri = uri.toString()))
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text("外观设置", fontWeight = FontWeight.SemiBold, color = textPrimary())
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = textPrimary()
                            )
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
                GlassPanel {
                    Text("玻璃透明度", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary())
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "越低越透，越高越接近实心卡片",
                        fontSize = 11.sp,
                        color = textSecondary()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = s.opacity,
                            onValueChange = { v -> store?.update(s.copy(opacity = v)) },
                            valueRange = 0.15f..1f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = GlassPrimary,
                                activeTrackColor = GlassPrimary
                            )
                        )
                        Text(
                            "${(s.opacity * 100).toInt()}%",
                            fontSize = 13.sp,
                            color = textPrimary(),
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }

                GlassPanel {
                    Text("背景图片", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary())
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (s.bgImageUri.isNullOrBlank()) "当前使用默认渐变背景"
                        else "已设置自定义背景",
                        fontSize = 11.sp,
                        color = textSecondary()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { pickBg.launch(arrayOf("image/*")) }) {
                            Text("选择图片", color = GlassPrimary)
                        }
                        if (!s.bgImageUri.isNullOrBlank()) {
                            TextButton(onClick = {
                                store?.update(s.copy(bgImageUri = null))
                            }) { Text("恢复默认", color = textSecondary()) }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text("背景压暗", fontSize = 13.sp, color = textPrimary())
                    Text(
                        "背景图太亮时调高，文字更清楚",
                        fontSize = 11.sp,
                        color = textSecondary()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = s.bgDim,
                            onValueChange = { v -> store?.update(s.copy(bgDim = v)) },
                            valueRange = 0f..0.8f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = GlassPrimary,
                                activeTrackColor = GlassPrimary
                            )
                        )
                        Text(
                            "${(s.bgDim * 100).toInt()}%",
                            fontSize = 13.sp,
                            color = textPrimary(),
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }

                GlassPanel {
                    Text("卡片圆角", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = s.corner,
                            onValueChange = { v -> store?.update(s.copy(corner = v)) },
                            valueRange = 0f..32f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = GlassPrimary,
                                activeTrackColor = GlassPrimary
                            )
                        )
                        Text(
                            "${s.corner.toInt()}",
                            fontSize = 13.sp,
                            color = textPrimary(),
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }

                TextButton(
                    onClick = { store?.reset() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("恢复全部默认", color = textSecondary())
                }

                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

/** 设置页里的小玻璃面板。 */
@Composable
private fun GlassPanel(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                10.dp,
                shape,
                clip = false,
                ambientColor = glassShadow(),
                spotColor = glassShadow()
            )
            .clip(shape)
            .background(glassFill())
            .border(BorderStroke(1.dp, glassBorder()), shape)
            .padding(16.dp)
    ) {
        content()
    }
}
