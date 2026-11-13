package com.medtimer.app

import android.content.Context
import com.medtimer.app.alarm.AlarmScheduler
import com.medtimer.app.data.db.AppDatabase
import com.medtimer.app.data.db.ensureSeeded
import com.medtimer.app.data.repo.DoseRepository
import com.medtimer.app.data.repo.MedicineRepository
import com.medtimer.app.data.repo.ProfileRepository
import com.medtimer.app.data.repo.ScheduleRepository
import com.medtimer.app.data.repo.SettingsRepository
import com.medtimer.app.data.repo.StockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 全局依赖入口：数据库与仓库。
 */
object ServiceLocator {

    lateinit var appContext: Context
        private set

    /** 应用级协程作用域，用于后台初始化。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.settingsDao())
    }

    val medicineRepository: MedicineRepository by lazy {
        MedicineRepository(database)
    }

    val doseRepository: DoseRepository by lazy {
        DoseRepository(database, settingsRepository)
    }

    val stockRepository: StockRepository by lazy {
        StockRepository(database)
    }

    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(database)
    }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(database)
    }

    /** 提醒排程器：广播接收器与界面共用。 */
    val alarmScheduler: AlarmScheduler by lazy {
        AlarmScheduler(appContext)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        // 幂等写入默认档案与设置
        appScope.launch { database.ensureSeeded() }
    }
}