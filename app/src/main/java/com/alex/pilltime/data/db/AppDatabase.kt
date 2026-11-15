package com.alex.pilltime.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.alex.pilltime.data.db.dao.DoseLogDao
import com.alex.pilltime.data.db.dao.MedicineDao
import com.alex.pilltime.data.db.dao.ProfileDao
import com.alex.pilltime.data.db.dao.ScheduleDao
import com.alex.pilltime.data.db.dao.SettingsDao
import com.alex.pilltime.data.db.dao.StockChangeDao
import com.alex.pilltime.data.db.dao.TaperStepDao
import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.data.entity.Profile
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.data.entity.StockChange
import com.alex.pilltime.data.entity.TaperStep
import kotlinx.coroutines.flow.first

@Database(
    entities = [
        Medicine::class,
        Schedule::class,
        DoseLog::class,
        StockChange::class,
        AppSettings::class,
        Profile::class,
        TaperStep::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicineDao(): MedicineDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun stockChangeDao(): StockChangeDao
    abstract fun settingsDao(): SettingsDao
    abstract fun profileDao(): ProfileDao
    abstract fun taperStepDao(): TaperStepDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pilltime.db"
                )
                    // 开发期未发布：字段删减直接重建，正式发布后必须改为增量迁移
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
    }
}

/**
 * 幂等种子数据：默认档案与默认设置。
 * 先插入档案，再保证 settings.activeProfileId 指向该档案。
 */
suspend fun AppDatabase.ensureSeeded() {
    var createdProfileId: Long? = null
    if (profileDao().count() == 0) {
        createdProfileId = profileDao().upsert(Profile(name = "", isDefault = true))
    }

    val current = settingsDao().get()
    if (current == null) {
        val activeId = createdProfileId
            ?: profileDao().observeAll().first().firstOrNull()?.id
            ?: 1L
        settingsDao().upsert(AppSettings(activeProfileId = activeId))
    } else if (createdProfileId != null && current.activeProfileId != createdProfileId) {
        settingsDao().upsert(current.copy(activeProfileId = createdProfileId))
    }
}