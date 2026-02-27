package com.example.whispertime.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.local.entity.TimingRecordEntity

@Database(
    entities = [ProjectEntity::class, TimingRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class WhisperTimeDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun timingRecordDao(): TimingRecordDao

    companion object {
        @Volatile
        private var INSTANCE: WhisperTimeDatabase? = null

        fun getInstance(context: Context): WhisperTimeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WhisperTimeDatabase::class.java,
                    "whispertime_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
