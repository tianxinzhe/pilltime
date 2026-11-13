package com.alex.pilltime.alarm

import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.domain.DoseSlot
import com.alex.pilltime.domain.model.DoseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 下一组选择逻辑（纯 JVM，不依赖 Android）。 */
class AlarmPlanTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 11, 12)

    private fun instant(dayOffset: Long, hour: Int, minute: Int): Instant =
        LocalDateTime.of(date.plusDays(dayOffset), java.time.LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()

    private fun slot(
        scheduleId: Long,
        hour: Int,
        minute: Int,
        lateMinutes: Int = 60,
        dayOffset: Long = 0
    ): DoseSlot {
        val local = LocalDateTime.of(date.plusDays(dayOffset), java.time.LocalTime.of(hour, minute))
        return DoseSlot(
            scheduleId = scheduleId,
            medicineId = scheduleId,
            plannedAt = local.atZone(zone).toInstant(),
            plannedLocal = local,
            doseQtyX10 = 10,
            earlyMinutes = 30,
            lateMinutes = lateMinutes
        )
    }

    private fun log(slot: DoseSlot, status: DoseStatus = DoseStatus.TAKEN): DoseLog = DoseLog(
        scheduleId = slot.scheduleId,
        medicineId = slot.medicineId,
        plannedAt = slot.plannedAt,
        actedAt = slot.plannedAt,
        status = status
    )

    @Test
    fun `窗口已结束的 slot 不再响`() {
        val now = instant(0, 10, 0)
        val expired = slot(1, 8, 0, lateMinutes = 60) // 窗口到 09:00

        val result = AlarmPlanner.pickNextGroup(listOf(expired), emptyList(), now, 10)

        assertNull(result)
    }

    @Test
    fun `已有有效记录跳过`() {
        val now = instant(0, 7, 0)
        val taken = slot(1, 8, 0)

        val result = AlarmPlanner.pickNextGroup(listOf(taken), listOf(log(taken)), now, 10)

        assertNull(result)
    }

    @Test
    fun `作废记录不算已处理`() {
        val now = instant(0, 7, 0)
        val slot = slot(1, 8, 0)
        val voided = log(slot).copy(voidedAt = instant(0, 7, 0))

        val result = AlarmPlanner.pickNextGroup(listOf(slot), listOf(voided), now, 10)

        assertEquals(slot.plannedAt, result?.triggerAt)
    }

    @Test
    fun `合并窗口内的相邻 slot 合并为一组`() {
        val now = instant(0, 7, 0)
        val first = slot(1, 8, 0)
        val second = slot(2, 8, 8)

        val result = AlarmPlanner.pickNextGroup(listOf(first, second), emptyList(), now, 10)

        assertEquals(2, result?.slots?.size)
        assertEquals(first.plannedAt, result?.triggerAt)
        assertEquals("1@${first.plannedAt.toEpochMilli()}", result?.groupKey)
    }

    @Test
    fun `跨天挑选最早的一组`() {
        val now = instant(0, 6, 0)
        val tomorrow = slot(2, 7, 0, dayOffset = 1)
        val today = slot(1, 9, 0)

        val result = AlarmPlanner.pickNextGroup(listOf(tomorrow, today), emptyList(), now, 10)

        assertEquals(today.plannedAt, result?.triggerAt)
    }

    @Test
    fun `没有任何 slot 时返回 null`() {
        val result = AlarmPlanner.pickNextGroup(emptyList(), emptyList(), instant(0, 7, 0), 10)

        assertNull(result)
    }
}