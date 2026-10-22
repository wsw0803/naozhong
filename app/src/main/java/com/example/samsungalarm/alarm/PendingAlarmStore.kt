package com.example.samsungalarm.alarm

import android.content.Context
import com.example.samsungalarm.data.Alarm

/** 一条待响闹钟的镜像记录。 */
data class PendingAlarm(
    val id: Long,
    val triggerAt: Long,
    val label: String,
    val ringtone: String?,
    val vibrate: Boolean,
    val snoozeMinutes: Int
)

/**
 * 待响闹钟的「设备加密存储」镜像。
 *
 * 为什么需要它：AlarmManager 的闹钟在重启时会被系统全部清空，而闹钟数据存在
 * Room 里，Room 的库文件在凭据加密区（credential-encrypted），开机后没解锁
 * 根本读不到；同时 BOOT_COMPLETED 也要等第一次解锁才发。
 * 于是「重启 -> 一直没解锁」这段时间里，闹钟一定不响。
 *
 * 这里把登记信息额外抄一份到设备加密区（解锁前即可读写），
 * 配合 LOCKED_BOOT_COMPLETED + directBootAware，就能在解锁前把闹钟重新登记。
 * 铃声只是个 Uri 字符串，解锁前读不到时会自动回退到系统默认铃声。
 */
object PendingAlarmStore {

    private const val PREF = "pending_alarms"
    private const val KEY_IDS = "ids"

    private fun sp(c: Context) =
        c.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun k(id: Long, field: String) = "a_" + id + "_" + field

    fun save(c: Context, alarm: Alarm, triggerAt: Long) {
        try {
            val cur = ids(c)
            sp(c).edit()
                .putLong(k(alarm.id, "at"), triggerAt)
                .putString(k(alarm.id, "label"), alarm.label)
                .putString(k(alarm.id, "tone"), alarm.ringtoneUri)
                .putBoolean(k(alarm.id, "vib"), alarm.vibrate)
                .putInt(k(alarm.id, "snooze"), alarm.snoozeMinutes)
                .putString(KEY_IDS, addId(cur, alarm.id))
                .apply()
        } catch (_: Exception) {
        }
    }

    /** 延迟触发只更新时间；其余字段沿用之前登记的内容。 */
    fun updateTriggerAt(c: Context, alarmId: Long, triggerAt: Long, snoozeMinutes: Int) {
        try {
            val cur = ids(c)
            val e = sp(c).edit().putLong(k(alarmId, "at"), triggerAt)
            if (!cur.contains(alarmId)) {
                e.putString(k(alarmId, "label"), "")
                e.putString(k(alarmId, "tone"), null)
                e.putBoolean(k(alarmId, "vib"), true)
                e.putInt(k(alarmId, "snooze"), snoozeMinutes)
                e.putString(KEY_IDS, addId(cur, alarmId))
            }
            e.apply()
        } catch (_: Exception) {
        }
    }

    fun clear(c: Context, alarmId: Long) {
        try {
            val rest = ids(c).filter { it != alarmId }
            sp(c).edit()
                .remove(k(alarmId, "at"))
                .remove(k(alarmId, "label"))
                .remove(k(alarmId, "tone"))
                .remove(k(alarmId, "vib"))
                .remove(k(alarmId, "snooze"))
                .putString(KEY_IDS, rest.joinToString(","))
                .apply()
        } catch (_: Exception) {
        }
    }

    fun get(c: Context, alarmId: Long): PendingAlarm? {
        return try {
            val p = sp(c)
            val at = p.getLong(k(alarmId, "at"), -1L)
            if (at <= 0L) {
                null
            } else {
                PendingAlarm(
                    id = alarmId,
                    triggerAt = at,
                    label = p.getString(k(alarmId, "label"), "").orEmpty(),
                    ringtone = p.getString(k(alarmId, "tone"), null),
                    vibrate = p.getBoolean(k(alarmId, "vib"), true),
                    snoozeMinutes = p.getInt(k(alarmId, "snooze"), 5)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 所有已登记、且还没到点的记录。解锁前重启恢复用。 */
    fun allPending(c: Context, now: Long = System.currentTimeMillis()): List<PendingAlarm> =
        ids(c).mapNotNull { get(c, it) }.filter { it.triggerAt > now }

    private fun ids(c: Context): List<Long> =
        try {
            sp(c).getString(KEY_IDS, "").orEmpty()
                .split(",")
                .mapNotNull { it.trim().toLongOrNull() }
        } catch (_: Exception) {
            emptyList()
        }

    private fun addId(cur: List<Long>, id: Long): String =
        (cur + id).distinct().joinToString(",")
}
