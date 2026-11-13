package com.alex.pilltime.ui.history

import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.domain.DoseSlot
import com.alex.pilltime.domain.model.DoseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 历史统计口径（纯 JVM，不依赖 android.*）。 */
class HistoryStatsMapperTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val day: LocalDate = LocalDate.of(2026, 11, 12)

    private fun at(hour: Int, minute: Int): Instant =
        day.atTime(hour, minute).atZone(zone).toInstant()

    private fun slot(scheduleId: Long, medicineId: Long, hour: Int, minute: Int) = DoseSlot(
        scheduleId = scheduleId,
        medicineId = medicineId,
        plannedAt = at(hour, minute),
        plannedLocal = day.atTime(hour, minute),
        doseQtyX10 = 10,
        earlyMinutes = 30,
        lateMinutes = 60
    )

    private fun log(
        id: Long,
        slot: DoseSlot,
        status: DoseStatus,
        actedAt: Instant? = null,
        delayMinutes: Int? = null,
        certain: Boolean = true,
        voidedAt: Instant? = null
    ) = DoseLog(
        id = id,
        scheduleId = slot.scheduleId,
        medicineId = slot.medicineId,
        plannedAt = slot.plannedAt,
        actedAt = actedAt,
        status = status,
        delayMinutes = delayMinutes,
        certain = certain,
        voidedAt = voidedAt
    )

    @Test
    fun `按时延迟未确认三类之和等于应服总次数`() {
        val taken = slot(1, 1, 8, 0)
        val late = slot(2, 2, 12, 0)
        val missed = slot(3, 3, 18, 0)
        val noLog = slot(4, 4, 20, 0)
        val slots = listOf(taken, late, missed, noLog)

        val items = mergeDayRecords(
            day = day,
            slots = slots,
            logs = listOf(
                log(1, taken, DoseStatus.TAKEN, actedAt = at(8, 0)),
                log(2, late, DoseStatus.LATE, actedAt = at(14, 0), delayMinutes = 120),
                log(3, missed, DoseStatus.MISSED)
            ),
            now = at(23, 0)
        )
        val counts = summarize(items)

        assertEquals(1, counts.onTime)
        assertEquals(1, counts.late)
        assertEquals(2, counts.unconfirmed)
        assertEquals(0, counts.uncertain)
        assertEquals(0, counts.skipped)
        assertEquals(4, counts.total)
        assertEquals(
            counts.total,
            counts.onTime + counts.late + counts.unconfirmed + counts.uncertain + counts.skipped
        )
    }

    @Test
    fun `跳过计入应服总次数但不计入未确认`() {
        val taken = slot(1, 1, 8, 0)
        val skipped = slot(2, 2, 12, 0)

        val counts = summarize(
            mergeDayRecords(
                day = day,
                slots = listOf(taken, skipped),
                logs = listOf(
                    log(1, taken, DoseStatus.TAKEN, actedAt = at(8, 0)),
                    log(2, skipped, DoseStatus.SKIPPED, actedAt = at(12, 0))
                ),
                now = at(23, 0)
            )
        )

        assertEquals(1, counts.skipped)
        assertEquals(1, counts.onTime)
        assertEquals(0, counts.unconfirmed)
        assertEquals(2, counts.total)
    }

    @Test
    fun `记不清单列且不重复计入未确认`() {
        val uncertain = slot(1, 1, 8, 0)

        val counts = summarize(
            mergeDayRecords(
                day = day,
                slots = listOf(uncertain),
                logs = listOf(log(1, uncertain, DoseStatus.UNCERTAIN, certain = false)),
                now = at(23, 0)
            )
        )

        assertEquals(1, counts.uncertain)
        assertEquals(0, counts.unconfirmed)
        assertEquals(1, counts.total)
    }

    @Test
    fun `未到提醒窗口终点的时刻不算未确认`() {
        val future = slot(1, 1, 20, 0)

        val items = mergeDayRecords(
            day = day,
            slots = listOf(future),
            logs = emptyList(),
            // 计划 20:00、延迟 60 分钟，20:30 尚未到窗口终点 21:00
            now = at(20, 30)
        )

        assertTrue(items.isEmpty())
        assertEquals(0, summarize(items).total)
    }

    @Test
    fun `作废记录不参与统计`() {
        val taken = slot(1, 1, 8, 0)
        val voided = log(
            id = 1,
            slot = taken,
            status = DoseStatus.TAKEN,
            actedAt = at(8, 0),
            voidedAt = at(8, 2)
        )

        val counts = summarize(
            mergeDayRecords(
                day = day,
                slots = listOf(taken),
                logs = listOf(voided),
                now = at(23, 0)
            )
        )

        assertEquals(0, counts.onTime)
        assertEquals(1, counts.unconfirmed)
        assertEquals(1, counts.total)
    }
}