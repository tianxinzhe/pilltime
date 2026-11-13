package com.medtimer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.medtimer.app.data.db.dao.DoseLogDao
import com.medtimer.app.data.db.dao.MedicineDao
import com.medtimer.app.data.db.dao.ProfileDao
import com.medtimer.app.data.db.dao.ScheduleDao
import com.medtimer.app.data.db.dao.SettingsDao
import com.medtimer.app.data.db.dao.StockChangeDao
import com.medtimer.app.data.db.dao.TaperStepDao
import com.medtimer.app.data.entity.AppSettings
import com.medtimer.app.data.entity.DoseLog
import com.medtimer.app.data.entity.Medicine
import com.medtimer.app.data.entity.Profile
import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.data.entity.StockChange
import com.medtimer.app.data.entity.TaperStep
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
    version = 1,
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
                    "medtimer.db"
                ).build().also { INSTANCE = it }
            }
        // version 1 无迁移，暂不需要 fallbackToDestructiveMigration
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