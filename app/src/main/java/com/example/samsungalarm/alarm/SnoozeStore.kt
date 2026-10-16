package com.example.samsungalarm.alarm

import android.content.Context

object SnoozeStore {
    private const val PREF = "snooze_state"
    private fun sp(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun key(id: Long) = "snooze_" + id

    fun set(c: Context, alarmId: Long, triggerAt: Long) {
        sp(c).edit().putLong(key(alarmId), triggerAt).apply()
    }

    fun clear(c: Context, alarmId: Long) {
        sp(c).edit().remove(key(alarmId)).apply()
    }

    fun get(c: Context, alarmId: Long): Long? {
        val v = sp(c).getLong(key(alarmId), -1L)
        if (v <= 0L) return null
        if (v <= System.currentTimeMillis()) { clear(c, alarmId); return null }
        return v
    }
}
