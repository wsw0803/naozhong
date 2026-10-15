package com.example.samsungalarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.samsungalarm.ui.AlarmViewModel
import com.example.samsungalarm.ui.GlassSettingsStore
import com.example.samsungalarm.ui.GroupScreen
import com.example.samsungalarm.ui.LocalGlassSettings
import com.example.samsungalarm.ui.LocalGlassStore
import com.example.samsungalarm.ui.SettingsScreen
import com.example.samsungalarm.ui.theme.AlarmTheme

class MainActivity : ComponentActivity() {

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()

        val store = GlassSettingsStore(applicationContext)

        setContent {
            CompositionLocalProvider(
                LocalGlassSettings provides store.settings,
                LocalGlassStore provides store
            ) {
                AlarmTheme {
                    var showSettings by remember { mutableStateOf(false) }

                    BackHandler(enabled = showSettings) {
                        showSettings = false
                    }

                    // 页面切换动画：进入设置从右滑入，返回时对称滑回
                    AnimatedContent(
                        targetState = showSettings,
                        transitionSpec = {
                            val spec = tween<IntOffset>(durationMillis = 320)
                            if (targetState) {
                                slideInHorizontally(spec) { it } togetherWith
                                        slideOutHorizontally(spec) { -it }
                            } else {
                                slideInHorizontally(spec) { -it } togetherWith
                                        slideOutHorizontally(spec) { it }
                            }
                        },
                        label = "page"
                    ) { show ->
                        if (show) {
                            SettingsScreen(onBack = { showSettings = false })
                        } else {
                            AlarmRoot(onOpenSettings = { showSettings = true })
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:" + packageName)
                    })
                } catch (_: Exception) {
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AlarmRoot(
    onOpenSettings: () -> Unit,
    vm: AlarmViewModel = viewModel()
) {
    GroupScreen(vm = vm, onOpenSettings = onOpenSettings)
}
