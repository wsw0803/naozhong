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
        val db = Room.databaseBuilder(this, AlarmDatabase::class.java, "alarm.db").build()
        repository = AlarmRepository(db.dao())
    }
}
