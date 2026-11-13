package com.medtimer.app.domain

import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.domain.model.AnchorType
import com.medtimer.app.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 规则引擎：节奏判定。对应测试用例 TC-SCH-01~11、13、14。 */
class ScheduleRecurrenceTest {

    private fun schedule(
        start: LocalDate,
        everyNDays: Int = 1,
        end: LocalDate? = null,
        weekdaysMask: Int? = null,
        monthlyDay: Int? = null,
        enabled: Boolean = true,
        anchorType: AnchorType = AnchorType.CLOCK,
        mealType: MealType? = null,
        clockHour: Int? = 8,
        clockMinute: Int? = 0,
        doseQtyX10: Int = 10,
        id: Long = 0,
        medicineId: Long = 1
    ) = Schedule(
        id = id,
        medicineId = medicineId,
        anchorType = anchorType,
        clockHour = clockHour,
        clockMinute = clockMinute,
        mealType = mealType,
        doseQtyX10 = doseQtyX10,
        startDate = start,
        endDate = end,
        everyNDays = everyNDays,
        weekdaysMask = weekdaysMask,
        monthlyDay = monthlyDay,
        enabled = enabled
    )

    @Test
    fun `TC-SCH-01 每天服用任意日期都应为 true`() {
        val s = schedule(LocalDate.of(2026, 1, 1))
        assertTrue(s.isDueOn(LocalDate.of(2026, 11, 12)))
        assertTrue(s.isDueOn(LocalDate.of(2027, 3, 5)))
        assertTrue(s.isDueOn(LocalDate.of(2027, 6, 30)))
    }

    @Test
    fun `TC-SCH-02 隔天药按基准日计算`() {
        val s = schedule(LocalDate.of(2026, 11, 12), everyNDays = 2)
        assertTrue(s.isDueOn(LocalDate.of(2026, 11, 12)))
        assertFalse(s.isDueOn(LocalDate.of(2026, 11, 13)))
        assertTrue(s.isDueOn(LocalDate.of(2026, 11, 14)))
    }

    @Test
    fun `TC-SCH-03 隔天药跨年不中断`() {
        val s = schedule(LocalDate.of(2026, 12, 30), everyNDays = 2)
        assertTrue(s.isDueOn(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `TC-SCH-04 隔天药跨闰日不中断`() {
        val s = schedule(LocalDate.of(2024, 2, 28), everyNDays = 2)
        assertTrue(s.isDueOn(LocalDate.of(2024, 3, 1)))
    }

    @Test
    fun `TC-SCH-05 跨月按天数差而非日期号`() {
        val s = schedule(LocalDate.of(2026, 1, 31), everyNDays = 2)
        assertTrue(s.isDueOn(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `TC-SCH-06 早于基准日不服用`() {
        val s = schedule(LocalDate.of(2026, 11, 12))
        assertFalse(s.isDueOn(LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun `TC-SCH-07 结束日当天仍服用`() {
        val s = schedule(
            LocalDate.of(2026, 11, 1),
            end = LocalDate.of(2026, 12, 31)
        )
        assertTrue(s.isDueOn(LocalDate.of(2026, 12, 31)))
        assertFalse(s.isDueOn(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `TC-SCH-08 每周固定几天`() {
        // 周一 = bit0，周三 = bit2，周五 = bit4
        val s = schedule(LocalDate.of(2026, 1, 1), weekdaysMask = 0b0010101)
        assertTrue(s.isDueOn(LocalDate.of(2026, 11, 9)))   // 周一
        assertFalse(s.isDueOn(LocalDate.of(2026, 11, 10))) // 周二
        assertTrue(s.isDueOn(LocalDate.of(2026, 11, 11)))  // 周三
        assertFalse(s.isDueOn(LocalDate.of(2026, 11, 12))) // 周四
        assertTrue(s.isDueOn(LocalDate.of(2026, 11, 13)))  // 周五
        assertFalse(s.isDueOn(LocalDate.of(2026, 11, 14))) // 周六
        assertFalse(s.isDueOn(LocalDate.of(2026, 11, 15))) // 周日
    }

    @Test
    fun `TC-SCH-09 每月31号在平年2月取月末`() {
        val s = schedule(LocalDate.of(2026, 1, 1), monthlyDay = 31)
        assertTrue(s.isDueOn(LocalDate.of(2026, 2, 28)))
        assertFalse(s.isDueOn(LocalDate.of(2026, 2, 27)))
    }

    @Test
    fun `TC-SCH-10 每月31号在闰年2月取29日`() {
        val s = schedule(LocalDate.of(2024, 1, 1), monthlyDay = 31)
        assertTrue(s.isDueOn(LocalDate.of(2024, 2, 29)))
        assertFalse(s.isDueOn(LocalDate.of(2024, 2, 28)))
    }

    @Test
    fun `TC-SCH-11 每月30号在2月取月末`() {
        val s = schedule(LocalDate.of(2026, 1, 1), monthlyDay = 30)
        assertTrue(s.isDueOn(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `TC-SCH-13 停用的计划不再服用`() {
        val s = schedule(LocalDate.of(2026, 11, 1), enabled = false)
        assertFalse(s.isDueOn(LocalDate.of(2026, 11, 12)))
    }

    @Test
    fun `TC-SCH-14 同一药品两条计划互不干扰`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.of(2026, 11, 12)
        val everyOtherMorning = schedule(
            start = date,
            everyNDays = 2,
            anchorType = AnchorType.MEAL,
            mealType = MealType.BREAKFAST,
            clockHour = null,
            clockMinute = null,
            id = 1,
            medicineId = 7
        )
        val everyNight = schedule(
            start = date,
            anchorType = AnchorType.CLOCK,
            clockHour = 22,
            clockMinute = 0,
            id = 2,
            medicineId = 7
        )

        val slots = buildDaySlots(date, listOf(everyOtherMorning, everyNight), zone = zone)

        assertEquals(2, slots.size)
        assertEquals(listOf<Long>(1, 2), slots.map { it.scheduleId })
        assertEquals(LocalTime.of(7, 30), slots[0].plannedLocal.toLocalTime())
        assertEquals(LocalTime.of(22, 0), slots[1].plannedLocal.toLocalTime())
        assertTrue(slots.all { it.medicineId == 7L })

        // 次日只有睡前计划，隔天计划不参与
        val tomorrow = buildDaySlots(date.plusDays(1), listOf(everyOtherMorning, everyNight), zone = zone)
        assertEquals(1, tomorrow.size)
        assertEquals(2L, tomorrow.single().scheduleId)
    }

    @Test
    fun `nextDueDate 找到下一个应服日期`() {
        val s = schedule(LocalDate.of(2026, 11, 12), everyNDays = 2)
        assertEquals(LocalDate.of(2026, 11, 14), s.nextDueDate(LocalDate.of(2026, 11, 13)))
        assertEquals(LocalDate.of(2026, 11, 12), s.nextDueDate(LocalDate.of(2026, 11, 12)))
        assertEquals(
            LocalDate.of(2026, 11, 12),
            s.nextDueDate(LocalDate.of(2026, 11, 1), maxDaysAhead = 20)
        )
    }
}