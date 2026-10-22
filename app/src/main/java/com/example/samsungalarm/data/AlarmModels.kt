package com.example.samsungalarm.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** 闹钟分组。 */
@Entity(tableName = "alarm_groups")
data class AlarmGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

/** 单个闹钟。 */
@Entity(
    tableName = "alarms",
    foreignKeys = [
        ForeignKey(
            entity = AlarmGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "",
    /** 重复日，1=周一 … 7=周日；空集合表示只响一次。 */
    val repeatDays: Set<Int> = emptySet(),
    val vibrate: Boolean = true,
    val ringtoneUri: String? = null,
    val snoozeMinutes: Int = 5
)

/**
 * Room 的类型转换器。
 * SQLite 只认识基本类型，Set<Int> 这种要手动转成字符串存，读的时候再还原。
 */
class Converters {

    @TypeConverter
    fun fromIntSet(value: Set<Int>?): String {
        if (value.isNullOrEmpty()) return ""
        return value.sorted().joinToString(",")
    }

    @TypeConverter
    fun toIntSet(value: String?): Set<Int> {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }
}

/** 真正会响 = 自身启用 且 分组启用。 */
fun Alarm.isActive(groupEnabled: Boolean): Boolean = enabled && groupEnabled

/** 计算下一次响铃毫秒。repeatDays 为空表示只响一次。 */
fun Alarm.nextTriggerAtMillis(now: java.util.Calendar): Long? {
    val from = now.timeInMillis
    val cal = now.clone() as java.util.Calendar
    cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
    cal.set(java.util.Calendar.MINUTE, minute)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)

    if (repeatDays.isEmpty()) {
        // 一次性闹钟：时间已过就顺延到明天，和系统时钟的行为一致。
        // 原实现直接返回 null，于是「设成今天已经过去的时间」的闹钟
        // 永远不会被登记，但列表里仍显示已开启 —— 用户完全看不出原因。
        if (cal.timeInMillis <= from) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    for (i in 0..7) {
        val iso = ((cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        if (repeatDays.contains(iso) && cal.timeInMillis > from) return cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    return null
}
