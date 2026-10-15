package com.example.samsungalarm.data

import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val dao: AlarmDao) {

    val groups: Flow<List<AlarmGroup>> = dao.groups()
    val alarms: Flow<List<Alarm>> = dao.alarms()

    suspend fun addGroup(name: String): Long {
        val next = dao.groupsOnce().size
        return dao.insertGroup(AlarmGroup(name = name, sortOrder = next))
    }

    suspend fun renameGroup(group: AlarmGroup, name: String) =
        dao.updateGroup(group.copy(name = name))

    suspend fun deleteGroup(group: AlarmGroup) = dao.deleteGroup(group)

    suspend fun addAlarm(alarm: Alarm): Long = dao.insertAlarm(alarm)

    suspend fun updateAlarm(alarm: Alarm) = dao.updateAlarm(alarm)

    suspend fun deleteAlarm(alarm: Alarm) = dao.deleteAlarm(alarm)

    suspend fun alarmById(id: Long) = dao.alarmById(id)

    /** 复制闹钟：新建一个内容完全相同、默认关闭的副本。 */
    suspend fun duplicateAlarm(alarm: Alarm): Long =
        dao.insertAlarm(alarm.copy(id = 0, enabled = false))

    /** 分组总开关：只切分组状态，不逐个改闹钟自身开关。 */
    suspend fun setGroupEnabled(groupId: Long, enabled: Boolean) =
        dao.setGroupEnabled(groupId, enabled)

    suspend fun setAlarmEnabled(id: Long, enabled: Boolean) =
        dao.setAlarmEnabled(id, enabled)

    suspend fun allAlarms(): List<Alarm> = dao.allAlarmsOnce()

    suspend fun allGroups(): List<AlarmGroup> = dao.groupsOnce()
}
