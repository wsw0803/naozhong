package com.example.samsungalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.samsungalarm.AlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机 / 应用更新后重新登记所有闹钟。
 *
 * 两个开机广播都要接：
 * - LOCKED_BOOT_COMPLETED：开机即发，此时还没解锁。凭据加密区的 Room 读不到，
 *   只能用设备加密区的闹钟镜像恢复，所以这条分支绝对不能碰数据库。
 * - BOOT_COMPLETED：第一次解锁之后才发，这时数据库可用，做完整重登记。
 *
 * 之前只接了 BOOT_COMPLETED，于是「重启 -> 一直没解锁」这段时间闹钟全丢。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AlarmScheduler.rearmFromMirror(context)
                    } catch (_: Exception) {
                    } finally {
                        pending.finish()
                    }
                }
                return
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
            }
            else -> return
        }

        KeepAliveService.start(context)
        val app = context.applicationContext as? AlarmApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = app.repository.allAlarms()
                val groups = app.repository.allGroups()
                AlarmScheduler.rescheduleAll(context, alarms, groups)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
