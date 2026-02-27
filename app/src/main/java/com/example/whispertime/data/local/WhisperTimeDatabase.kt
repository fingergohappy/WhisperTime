package com.example.whispertime.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.local.entity.TimingRecordEntity

/**
 * WhisperTime 应用的 Room 数据库。
 *
 * 负责定义数据库配置，包括包含的实体类和版本号。
 * 提供获取各种 Data Access Object (DAO) 的抽象方法。
 */
@Database(
    entities = [ProjectEntity::class, TimingRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class WhisperTimeDatabase : RoomDatabase() {

    /**
     * 获取项目数据的访问对象。
     */
    abstract fun projectDao(): ProjectDao

    /**
     * 获取计时记录数据的访问对象。
     */
    abstract fun timingRecordDao(): TimingRecordDao

    companion object {
        @Volatile
        private var INSTANCE: WhisperTimeDatabase? = null

        /**
         * 获取数据库的单例实例。
         *
         * 使用双重检查锁定确保线程安全的单例创建。
         * 启用了 fallbackToDestructiveMigration，这意味着在版本升级且未提供迁移路径时会清空数据。
         *
         * @param context 应用程序上下文
         */
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
