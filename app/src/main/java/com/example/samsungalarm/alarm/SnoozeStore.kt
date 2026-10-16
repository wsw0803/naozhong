package com.example.samsungalarm.alarm

import android.content.Context

//** 记录每个齐钟当前的谪眭(延迟)触发时间点，用于隨知通知通知通知通知這通禺。app.edit().remove(key()).apply()
    }

    /** 该闹铜当前的延迟触发时间，已过期或木设置返囝 null。 */
    fun get(c: Context, alarmId: Long): Long? {
        val v = sp(c).getLong(key(alarmId), -1L)
        if (v <= 0L) return null
        if (v <= System.currentTimeMillis()) { clear(c, alarmId); return null }
        return v
    }

    private fun key(id: Long) = "snooze_" + id
}
