package com.medtimer.app.domain

import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.data.entity.TaperStep
import com.medtimer.app.domain.model.AnchorType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 递减阶段解析。 */
class TaperResolverTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 11, 12)

    private fun schedule(taperEnabled: Boolean = true) = Schedule(
        id = 1,
        medicineId = 1,
        anchorType = AnchorType.CLOCK,
        clockHour = 8,
        clockMinute = 0,
        doseQtyX10 = 10,
        startDate = LocalDate.of(2026, 10, 1),
        taperEnabled = taperEnabled
    )

    private val steps = listOf(
        TaperStep(scheduleId = 1, startDate = LocalDate.of(2026, 11, 5), doseQtyX10 = 5),
        TaperStep(scheduleId = 1, startDate = LocalDate.of(2026, 11, 10), doseQtyX10 = 2)
    )

    @Test
    fun `递减阶段按开始日期生效`() {
        val s = schedule()
        // 11-04 早于全部步骤，按规则取最早一步的剂量
        assertEquals(5, s.doseQtyX10On(LocalDate.of(2026, 11, 4), steps))
        assertEquals(5, s.doseQtyX10On(LocalDate.of(2026, 11, 5), steps))
        assertEquals(5, s.doseQtyX10On(LocalDate.of(2026, 11, 9), steps))
        assertEquals(2, s.doseQtyX10On(LocalDate.of(2026, 11, 10), steps))
        assertEquals(2, s.doseQtyX10On(date, steps))
    }

    @Test
    fun `所有步骤都晚于当天时回退到最早一步`() {
        assertEquals(5, schedule().doseQtyX10On(LocalDate.of(2026, 11, 1), steps))
    }

    @Test
    fun `未开启递减或无步骤时用计划剂量`() {
        assertEquals(10, schedule(taperEnabled = false).doseQtyX10On(date, steps))
        assertEquals(10, schedule().doseQtyX10On(date, emptyList()))
    }

    @Test
    fun `slot 使用当天递减剂量`() {
        val slots = buildDaySlots(date, listOf(schedule()), mapOf(1L to steps), zone = zone)
        assertEquals(1, slots.size)
        assertEquals(2, slots.single().doseQtyX10)
    }
}