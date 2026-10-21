package com.example.samsungalarm

import android.app.Application
import androidx.room.Room
import com.example.samsungalarm.data.AlarmDatabase
import com.example.samsungalarm.data.AlarmRepository

class AlarmApp : Application() {

    lateinit var repository: AlarmRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // 版本升级后如果表结构变了，Room 默认会直接抛异常，
        // 结果是整个进程起不来 —— 所有闹钟一起失联，而且用户完全看不出原因。
        // 闹钟这种场景宁可清库重建，也不能让「不响 / 打不开」发生。
        val db = Room.databaseBuilder(this, AlarmDatabase::class.java, "alarm.db")
            .fallbackToDestructiveMigration()
            .build()
        repository = AlarmRepository(db.dao())
    }
}
