package com.medtimer.app.data.repo

import androidx.room.withTransaction
import com.medtimer.app.data.db.AppDatabase
import com.medtimer.app.data.entity.DoseLog
import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.data.entity.StockChange
import com.medtimer.app.domain.buildDaySlots
import com.medtimer.app.domain.canUndo
import com.medtimer.app.domain.doseQtyX10On
import com.medtimer.app.domain.isActedAtAcceptable
import com.medtimer.app.domain.judgeDose
import com.medtimer.app.domain.model.DoseStatus
import com.medtimer.app.domain.model.StockReason
import com.medtimer.app.domain.windowEnd
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** 漏服补算结果：写入条数与涉及日期（升序去重）。 */
data class MissedRecomputeResult(val missedCount: Int, val affectedDates: List<LocalDate>)

/**
 * 服药记录仓库：状态判定、幂等写入与库存扣减/回补在同一事务内完成。
 * 幂等键为 (scheduleId, plannedAt)。
 */
class DoseRepository(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository
) {

    private val doseLogDao = db.doseLogDao()
    private val medicineDao = db.medicineDao()
    private val scheduleDao = db.scheduleDao()
    private val stockChangeDao = db.stockChangeDao()
    private val taperStepDao = db.taperStepDao()

    /**
     * 确认服用。已存在有效记录时直接返回该条（不重复插入、不重复扣库存）。
     * actedAt 晚于当前 30 分钟以上时拒绝写入并返回 null。
     */
    suspend fun markTaken(
        schedule: Schedule,
        plannedAt: Instant,
        actedAt: Instant = Instant.now(),
        certain: Boolean = true
    ): DoseLog? {
        val now = Instant.now()
        if (!isActedAtAcceptable(actedAt, now)) return null

        return db.withTransaction {
            doseLogDao.findActive(schedule.id, plannedAt)?.let { return@withTransaction it }

            val decision = judgeDose(
                plannedAt = plannedAt,
                actedAt = actedAt,
                now = now,
                earlyMinutes = schedule.earlyMinutes,
                lateMinutes = schedule.lateMinutes
            )
            val log = DoseLog(
                scheduleId = schedule.id,
                medicineId = schedule.medicineId,
                profileId = profileIdOf(schedule.medicineId),
                plannedAt = plannedAt,
                actedAt = actedAt,
                status = decision.status,
                delayMinutes = decision.delayMinutes,
                certain = certain
            )
            val saved = log.copy(id = doseLogDao.insert(log))
            deductStock(saved, schedule, doseQtyFor(schedule, plannedAt), actedAt)
            saved
        }
    }

    /** 跳过服药：不写库存流水。 */
    suspend fun markSkipped(
        schedule: Schedule,
        plannedAt: Instant,
        reason: String? = null,
        actedAt: Instant = Instant.now()
    ): DoseLog? = db.withTransaction {
        doseLogDao.findActive(schedule.id, plannedAt)?.let { return@withTransaction it }

        val log = DoseLog(
            scheduleId = schedule.id,
            medicineId = schedule.medicineId,
            profileId = profileIdOf(schedule.medicineId),
            plannedAt = plannedAt,
            actedAt = actedAt,
            status = DoseStatus.SKIPPED,
            reason = reason
        )
        log.copy(id = doseLogDao.insert(log))
    }

    /** 记不清是否服用：certain=false，不写库存流水。 */
    suspend fun markUncertain(
        schedule: Schedule,
        plannedAt: Instant,
        actedAt: Instant = Instant.now()
    ): DoseLog? = db.withTransaction {
        doseLogDao.findActive(schedule.id, plannedAt)?.let { return@withTransaction it }

        val log = DoseLog(
            scheduleId = schedule.id,
            medicineId = schedule.medicineId,
            profileId = profileIdOf(schedule.medicineId),
            plannedAt = plannedAt,
            actedAt = actedAt,
            status = DoseStatus.UNCERTAIN,
            certain = false
        )
        log.copy(id = doseLogDao.insert(log))
    }

    /** 写入漏服。已有有效记录时直接返回该条，不写库存。 */
    suspend fun markMissed(schedule: Schedule, plannedAt: Instant): DoseLog? = db.withTransaction {
        doseLogDao.findActive(schedule.id, plannedAt)?.let { return@withTransaction it }

        val log = DoseLog(
            scheduleId = schedule.id,
            medicineId = schedule.medicineId,
            profileId = profileIdOf(schedule.medicineId),
            plannedAt = plannedAt,
            status = DoseStatus.MISSED,
            certain = true
        )
        log.copy(id = doseLogDao.insert(log))
    }

    /**
     * 撤销：仅 TAKEN/LATE 且 5 分钟内允许。
     * 保留原记录，只写 voidedAt；若原记录扣过库存，则补一条 UNDO 正向流水。
     */
    suspend fun undo(doseLogId: Long, now: Instant = Instant.now()): Boolean = db.withTransaction {
        val log = doseLogDao.getById(doseLogId) ?: return@withTransaction false
        if (log.voidedAt != null) return@withTransaction false
        if (log.status != DoseStatus.TAKEN && log.status != DoseStatus.LATE) return@withTransaction false
        val actedAt = log.actedAt ?: return@withTransaction false
        if (!canUndo(actedAt, now, 5)) return@withTransaction false

        doseLogDao.update(log.copy(voidedAt = now))

        // 仅当原记录确实扣过库存时回补
        if (stockChangeDao.countTakenByRef(log.id) > 0) {
            val schedule = scheduleDao.getById(log.scheduleId)
            if (schedule != null) {
                val qty = doseQtyFor(schedule, log.plannedAt)
                stockChangeDao.insert(
                    StockChange(
                        medicineId = log.medicineId,
                        deltaQtyX10 = qty,
                        reason = StockReason.UNDO,
                        refId = log.id,
                        at = now
                    )
                )
            }
        }
        true
    }

    suspend fun getActive(scheduleId: Long, plannedAt: Instant): DoseLog? =
        doseLogDao.findActive(scheduleId, plannedAt)

    fun observeDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<DoseLog>> {
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        return doseLogDao.observeInRange(from, to)
    }

    fun observeRange(
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<List<DoseLog>> {
        val fromInstant = from.atStartOfDay(zone).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(zone).toInstant()
        return doseLogDao.observeInRange(fromInstant, toInstant)
    }

    /**
     * 补算漏服：回看 [lookBackDays] 天，对已过窗口且无有效记录的 slot 写入 MISSED。
     * 幂等：已有有效记录（含 MISSED）的 slot 不会被重复插入。
     */
    suspend fun recomputeMissed(
        now: Instant = Instant.now(),
        lookBackDays: Int = 7,
        zone: ZoneId = ZoneId.systemDefault()
    ): MissedRecomputeResult = db.withTransaction {
        val today = now.atZone(zone).toLocalDate()
        val from = today.minusDays(lookBackDays.toLong())
        val schedules = scheduleDao.getAllTimedEnabled()
        val stepsBySchedule = schedules.associate { it.id to taperStepDao.getBySchedule(it.id) }
        val settings = settingsRepository.get()

        val affected = mutableListOf<LocalDate>()
        var missed = 0
        var date = from
        while (!date.isAfter(today)) {
            val slots = buildDaySlots(date, schedules, stepsBySchedule, settings, zone)
            for (slot in slots) {
                if (windowEnd(slot.plannedAt, slot.lateMinutes) > now) continue
                if (doseLogDao.findActive(slot.scheduleId, slot.plannedAt) != null) continue

                doseLogDao.insert(
                    DoseLog(
                        scheduleId = slot.scheduleId,
                        medicineId = slot.medicineId,
                        profileId = profileIdOf(slot.medicineId),
                        plannedAt = slot.plannedAt,
                        status = DoseStatus.MISSED,
                        certain = true
                    )
                )
                missed++
                affected += date
            }
            date = date.plusDays(1)
        }
        MissedRecomputeResult(missed, affected.distinct().sorted())
    }

    /**
     * PRN 快捷记录：一次点击即一条 TAKEN。
     * prnMaxPerDay 仅作提示，不阻止写入；库存不足也不阻止。
     */
    suspend fun markPrnTaken(schedule: Schedule, at: Instant = Instant.now()): DoseLog? =
        db.withTransaction {
            val log = DoseLog(
                scheduleId = schedule.id,
                medicineId = schedule.medicineId,
                profileId = profileIdOf(schedule.medicineId),
                plannedAt = at,
                actedAt = at,
                status = DoseStatus.TAKEN,
                delayMinutes = 0,
                certain = true
            )
            val saved = log.copy(id = doseLogDao.insert(log))
            deductStock(saved, schedule, schedule.doseQtyX10, at)
            saved
        }

    /** 按计划当天剂量（含递减步骤）扣库存；关闭自动扣减或已扣过则跳过。 */
    private suspend fun deductStock(log: DoseLog, schedule: Schedule, qtyX10: Int, at: Instant) {
        val medicine = medicineDao.getById(schedule.medicineId) ?: return
        if (!medicine.autoDeductStock) return
        if (stockChangeDao.countTakenByRef(log.id) > 0) return

        stockChangeDao.insert(
            StockChange(
                medicineId = log.medicineId,
                deltaQtyX10 = -qtyX10,
                reason = StockReason.TAKEN,
                refId = log.id,
                at = at
            )
        )
    }

    /** PRN 用计划自身剂量，定时计划按计划时刻所在本地日期取递减剂量。 */
    private suspend fun doseQtyFor(schedule: Schedule, plannedAt: Instant): Int =
        if (schedule.isPrn) {
            schedule.doseQtyX10
        } else {
            schedule.doseQtyX10On(
                plannedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                taperStepDao.getBySchedule(schedule.id)
            )
        }

    private suspend fun profileIdOf(medicineId: Long): Long =
        medicineDao.getById(medicineId)?.profileId ?: 1L
}