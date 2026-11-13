package com.medtimer.app.data.repo

import androidx.room.withTransaction
import com.medtimer.app.data.db.AppDatabase
import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.data.entity.TaperStep
import com.medtimer.app.domain.nextDueDate
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 用药计划仓库。计划时刻由计划实时推导，这里只负责持久化。
 */
class ScheduleRepository(private val db: AppDatabase) {

    private val scheduleDao = db.scheduleDao()
    private val taperStepDao = db.taperStepDao()
    private val profileDao = db.profileDao()

    fun observeSchedules(profileId: Long): Flow<List<Schedule>> =
        scheduleDao.observeByProfile(profileId)

    fun observeMedicineWithSchedules(medicineId: Long): Flow<List<Schedule>> =
        scheduleDao.observeByMedicine(medicineId)

    suspend fun getTimedEnabled(): List<Schedule> = scheduleDao.getAllTimedEnabled()

    /** 当前档案下启用的定时计划；PRN 不参与提醒排程。 */
    suspend fun getTimedEnabledForProfile(profileId: Long): List<Schedule> =
        scheduleDao.getTimedEnabledForProfile(profileId)

    suspend fun getById(id: Long): Schedule? = scheduleDao.getById(id)

    suspend fun save(schedule: Schedule): Long = scheduleDao.upsert(schedule)

    suspend fun delete(id: Long) {
        scheduleDao.deleteById(id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val schedule = scheduleDao.getById(id) ?: return
        scheduleDao.upsert(schedule.copy(enabled = enabled))
    }

    /** 整体替换某计划的递减步骤。 */
    suspend fun saveTaperSteps(scheduleId: Long, steps: List<TaperStep>) = db.withTransaction {
        taperStepDao.deleteBySchedule(scheduleId)
        if (steps.isNotEmpty()) {
            taperStepDao.upsertAll(steps.map { it.copy(id = 0, scheduleId = scheduleId) })
        }
    }

    suspend fun getTaperSteps(scheduleId: Long): List<TaperStep> =
        taperStepDao.getBySchedule(scheduleId)

    /** 批量读取指定计划的递减步骤，避免为无关档案多做查询。 */
    suspend fun getTaperStepsFor(scheduleIds: Collection<Long>): Map<Long, List<TaperStep>> =
        scheduleIds.associateWith { taperStepDao.getBySchedule(it) }

    suspend fun getAllTaperSteps(): Map<Long, List<TaperStep>> {
        val scheduleIds = profileDao.observeAll().first()
            .flatMap { profile -> scheduleDao.observeByProfile(profile.id).first() }
            .map { it.id }
        return scheduleIds.associateWith { taperStepDao.getBySchedule(it) }
    }

    /** 400 天内最近一次应服日期；多条计划取最早。 */
    suspend fun nextDueDateFor(medicineId: Long, from: LocalDate): LocalDate? =
        scheduleDao.getByMedicine(medicineId)
            .mapNotNull { it.nextDueDate(from, 400) }
            .minOrNull()
}