package com.example.samsungalarm.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarm_groups ORDER BY sortOrder ASC, id ASC")
    fun groups(): Flow<List<AlarmGroup>>

    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun alarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    suspend fun alarmById(id: Long): Alarm?

    @Query("SELECT * FROM alarms")
    suspend fun allAlarmsOnce(): List<Alarm>

    @Query("SELECT * FROM alarm_groups ORDER BY sortOrder ASC, id ASC")
    suspend fun groupsOnce(): List<AlarmGroup>

    @Insert
    suspend fun insertGroup(group: AlarmGroup): Long

    @Insert
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateGroup(group: AlarmGroup)

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteGroup(group: AlarmGroup)

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteAlarms(alarms: List<Alarm>)

    @Query("UPDATE alarm_groups SET enabled = :enabled WHERE id = :groupId")
    suspend fun setGroupEnabled(groupId: Long, enabled: Boolean)

    @Query("UPDATE alarms SET enabled = :enabled WHERE groupId = :groupId")
    suspend fun setAlarmsEnabledInGroup(groupId: Long, enabled: Boolean)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setAlarmEnabled(id: Long, enabled: Boolean)

    /** 写入单个分组的排序位。 */
    @Query("UPDATE alarm_groups SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateGroupSortOrder(id: Long, sortOrder: Int)

    /**
     * 按传入顺序整体重排分组。
     *
     * 必须放在同一个事务里：逐条 update 会触发多次 Flow 刷新，
     * 列表可能瞬间出现「两个分组同序号」的中间态，导致界面闪烁或顺序错乱。
     */
    @Transaction
    suspend fun updateGroupSortOrders(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updateGroupSortOrder(id, index) }
    }
}

/**
 * 注册类型转换器。
 * Set 这种 Room 不认识，必须靠 Converters 转成字符串。
 */
@Database(
    entities = [AlarmGroup::class, Alarm::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun dao(): AlarmDao
}
