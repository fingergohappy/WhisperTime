package com.example.whispertime.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.local.entity.TimingRecordEntity

@Database(
    entities = [ProjectEntity::class, TimingRecordEntity::class],
    version = 3,
    exportSchema = false
)
/** Room 数据库定义，集中注册项目和计时记录表。 */
abstract class WhisperTimeDatabase : RoomDatabase() {

    /** 获取项目表的数据访问对象。 */
    abstract fun projectDao(): ProjectDao

    /** 获取计时记录表的数据访问对象。 */
    abstract fun timingRecordDao(): TimingRecordDao

    /** 数据库单例和版本迁移配置。 */
    companion object {
        /** Room 数据库单例，使用 volatile 保证多线程可见性。 */
        @Volatile
        private var INSTANCE: WhisperTimeDatabase? = null

        /** 获取数据库实例，首次访问时完成创建和迁移注册。 */
        fun getInstance(context: Context): WhisperTimeDatabase {
            return INSTANCE ?: synchronized(this) {
                // 双重检查避免并发初始化时重复创建数据库。
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WhisperTimeDatabase::class.java,
                    "whispertime_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** 版本 2 到 3 迁移：为项目增加震动提醒开关。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            /** 执行增量 SQL，保留旧用户数据。 */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN vibrationEnabled INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
