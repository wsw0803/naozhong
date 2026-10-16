package com.example.samsungalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungalarm.AlarmApp
import com.example.samsungalarm.alarm.AlarmScheduler
import com.example.samsungalarm.data.Alarm
import com.example.samsungalarm.data.AlarmGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupWithAlarms(
    val group: AlarmGroup,
    val alarms: List<Alarm>
)

class AlarmViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as AlarmApp).repository
    init {
        refreshNextAlarmNotification()
    }

    fun refreshNextAlarmNotification() {
        viewModelScope.launch {
            val alarms = repo.allAlarms()
            val groups = repo.allGroups()
            AlarmScheduler.refreshNotification(getApplication(), alarms, groups)
        }
    }

    fun setAllInGroup(groupId: Long, enabled: Boolean) = viewModelScope.launch {
        repo.setAllEnabledInGroup(groupId, enabled)
        reschedule()
    }

    fun deleteAlarms(alarms: List<Alarm>) = viewModelScope.launch {
        alarms.forEach { AlarmScheduler.cancel(getApplication(), it.id) }
        repo.deleteAlarms(alarms)
        reschedule()
    }

    val grouped: StateFlow<List<GroupWithAlarms>> =
        combine(repo.groups, repo.alarms) { groups, alarms ->
            groups.map { g ->
                GroupWithAlarms(g, alarms.filter { it.groupId == g.id })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private suspend fun reschedule() {
        val alarms = repo.allAlarms()
        val groups = repo.allGroups()
        AlarmScheduler.rescheduleAll(getApplication(), alarms, groups)
    }

    fun addGroup(name: String) = viewModelScope.launch { repo.addGroup(name) }

    fun renameGroup(group: AlarmGroup, name: String) = viewModelScope.launch {
        repo.renameGroup(group, name)
    }

    fun deleteGroup(group: AlarmGroup) = viewModelScope.launch {
        repo.deleteGroup(group)
    }

    /** 分组一键总开关：组内闹钟各自开关保持不变。 */
    fun toggleGroup(group: AlarmGroup, enabled: Boolean) = viewModelScope.launch {
        repo.setGroupEnabled(group.id, enabled)
        reschedule()
    }

    fun toggleAlarm(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        repo.setAlarmEnabled(alarm.id, enabled)
        reschedule()
    }

    /** 复制闹钟：直接在当前分组下克隆一个。 */
    fun duplicateAlarm(alarm: Alarm) = viewModelScope.launch {
        repo.duplicateAlarm(alarm)
        reschedule()
    }

    fun saveAlarm(alarm: Alarm) = viewModelScope.launch {
        if (alarm.id == 0L) repo.addAlarm(alarm) else repo.updateAlarm(alarm)
        reschedule()
    }

    fun deleteAlarm(alarm: Alarm) = viewModelScope.launch {
        AlarmScheduler.cancel(getApplication(), alarm.id)
        repo.deleteAlarm(alarm)
        reschedule()
    }
}
