package com.medtimer.app.data.repo

import com.medtimer.app.data.db.AppDatabase
import com.medtimer.app.data.entity.StockChange
import com.medtimer.app.domain.daysLeft
import com.medtimer.app.domain.isExpiringBeforeRunOut
import com.medtimer.app.domain.model.StockReason
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow

/**
 * 库存仓库：库存值始终由流水求和得出，不单独存字段。
 */
class StockRepository(private val db: AppDatabase) {

    private val stockChangeDao = db.stockChangeDao()
    private val medicineDao = db.medicineDao()
    private val scheduleDao = db.scheduleDao()
    private val taperStepDao = db.taperStepDao()

    fun observeStock(medicineId: Long): Flow<Int> = stockChangeDao.observeStock(medicineId)

    suspend fun currentStock(medicineId: Long): Int = stockChangeDao.sumByMedicine(medicineId)

    /** 入库：正数 PURCHASE 流水。 */
    suspend fun addPurchase(
        medicineId: Long,
        qtyX10: Int,
        at: Instant = Instant.now(),
        note: String? = null
    ): Long = stockChangeDao.insert(
        StockChange(
            medicineId = medicineId,
            deltaQtyX10 = abs(qtyX10),
            reason = StockReason.PURCHASE,
            at = at,
            note = note
        )
    )

    /** 盘点：写 ADJUST = 实际值 − 当前值；无差异时不写流水，返回 0。 */
    suspend fun adjustTo(
        medicineId: Long,
        actualQtyX10: Int,
        at: Instant = Instant.now(),
        note: String? = null
    ): Long {
        val delta = actualQtyX10 - stockChangeDao.sumByMedicine(medicineId)
        if (delta == 0) return 0L
        return stockChangeDao.insert(
            StockChange(
                medicineId = medicineId,
                deltaQtyX10 = delta,
                reason = StockReason.ADJUST,
                at = at,
                note = note
            )
        )
    }

    fun observeRecent(medicineId: Long, limit: Int = 20): Flow<List<StockChange>> =
        stockChangeDao.observeRecent(medicineId, limit)

    /** 剩余可用天数，按未来 30 天实际规则逐日求和估算。 */
    suspend fun remainingDays(
        medicineId: Long,
        today: LocalDate = LocalDate.now()
    ): Double {
        val schedules = scheduleDao.getByMedicine(medicineId)
        val steps = schedules.associate { it.id to taperStepDao.getBySchedule(it.id) }
        return daysLeft(stockChangeDao.sumByMedicine(medicineId), schedules, today, 30, steps)
    }

    /** 预计吃完日期是否晚于有效期。 */
    suspend fun expiringBeforeRunOut(
        medicineId: Long,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val medicine = medicineDao.getById(medicineId) ?: return false
        return isExpiringBeforeRunOut(medicine.expireDate, today, remainingDays(medicineId, today))
    }
}