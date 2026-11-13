package com.medtimer.app.ui.medicine

import com.medtimer.app.domain.isDueOn
import com.medtimer.app.domain.model.AnchorType
import com.medtimer.app.domain.model.MealType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 计划表单 → 计划实体的纯函数映射（不依赖 android.*）。 */
class ScheduleDraftMapperTest {

    private val startDate: LocalDate = LocalDate.of(2026, 11, 12)

    private fun form(
        doseText: String = "1",
        anchorType: AnchorType = AnchorType.CLOCK,
        mealType: MealType = MealType.BREAKFAST,
        offsetMinText: String = "0",
        recurrence: RecurrenceKind = RecurrenceKind.DAILY,
        everyNDaysText: String = "2",
        weekdays: Set<Int> = emptySet(),
        monthlyDayText: String = "1",
        longTerm: Boolean = true,
        endDate: LocalDate? = null,
        earlyText: String = "30",
        lateText: String = "60",
        isPrn: Boolean = false,
        prnMaxPerDayText: String = ""
    ) = ScheduleForm(
        anchorType = anchorType,
        clockHour = 8,
        clockMinute = 0,
        mealType = mealType,
        offsetMinText = offsetMinText,
        recurrence = recurrence,
        everyNDaysText = everyNDaysText,
        weekdays = weekdays,
        monthlyDayText = monthlyDayText,
        startDate = startDate,
        longTerm = longTerm,
        endDate = endDate,
        doseText = doseText,
        earlyText = earlyText,
        lateText = lateText,
        isPrn = isPrn,
        prnMaxPerDayText = prnMaxPerDayText
    )

    @Test
    fun `剂量文本转 x10 整数 保留一位小数`() {
        assertEquals(5, parseDoseX10("0.5"))
        assertEquals(10, parseDoseX10("1"))
        assertEquals(15, parseDoseX10("1.5"))
        assertEquals(25, parseDoseX10("2.5"))
        assertEquals(20, parseDoseX10(" 2 "))
        assertEquals(5, parseDoseX10("0,5"))
    }

    @Test
    fun `剂量文本非法时返回 null`() {
        assertNull(parseDoseX10(""))
        assertNull(parseDoseX10("abc"))
        assertNull(parseDoseX10("1.55"))
        assertNull(parseDoseX10("0"))
        assertNull(parseDoseX10("-1"))
        assertNull(parseDoseX10("."))
    }

    @Test
    fun `星期集合转周掩码 周一为 1 周日为 64`() {
        assertEquals(1, toWeekdaysMask(setOf(1)))
        assertEquals(64, toWeekdaysMask(setOf(7)))
        assertEquals(69, toWeekdaysMask(setOf(1, 3, 7)))
        assertEquals(127, toWeekdaysMask((1..7).toSet()))
        assertNull(toWeekdaysMask(emptySet()))
        assertNull(toWeekdaysMask(setOf(0)))
        assertNull(toWeekdaysMask(setOf(8)))
        assertEquals(setOf(2, 6), maskToWeekdays((1 shl 1) or (1 shl 5)))
    }

    @Test
    fun `每月 31 号在小月按当月最后一天判定`() {
        val schedule = draftToSchedule(
            form = form(recurrence = RecurrenceKind.MONTHLY, monthlyDayText = "31")
                .copy(startDate = LocalDate.of(2026, 1, 1)),
            medicineId = 1L,
            splittable = true
        ).schedule!!
        assertEquals(31, schedule.monthlyDay)
        assertTrue(schedule.isDueOn(LocalDate.of(2026, 2, 28)))
        assertFalse(schedule.isDueOn(LocalDate.of(2026, 2, 27)))
        assertTrue(schedule.isDueOn(LocalDate.of(2026, 1, 31)))
    }

    @Test
    fun `饭前偏移保存为负 饭后为正`() {
        val before = draftToSchedule(
            form = form(anchorType = AnchorType.MEAL, offsetMinText = "-30"),
            medicineId = 1L,
            splittable = true
        ).schedule!!
        assertEquals(AnchorType.MEAL, before.anchorType)
        assertEquals(-30, before.offsetMin)
        assertEquals(MealType.BREAKFAST, before.mealType)
        assertNull(before.clockHour)
        assertNull(before.clockMinute)

        val after = draftToSchedule(
            form = form(anchorType = AnchorType.MEAL, offsetMinText = "15"),
            medicineId = 1L,
            splittable = true
        ).schedule!!
        assertEquals(15, after.offsetMin)
    }

    @Test
    fun `固定时刻锚点保存时清除餐次字段`() {
        val schedule = draftToSchedule(
            form = form(anchorType = AnchorType.CLOCK),
            medicineId = 1L,
            splittable = true
        ).schedule!!
        assertEquals(AnchorType.CLOCK, schedule.anchorType)
        assertEquals(8, schedule.clockHour)
        assertEquals(0, schedule.clockMinute)
        assertNull(schedule.mealType)
        assertEquals(0, schedule.offsetMin)
    }

    @Test
    fun `PRN 与定时节奏互斥 不写入周掩码与月日`() {
        val schedule = draftToSchedule(
            form = form(
                recurrence = RecurrenceKind.WEEKLY,
                weekdays = setOf(1, 3),
                monthlyDayText = "15",
                everyNDaysText = "3",
                isPrn = true,
                prnMaxPerDayText = "3"
            ),
            medicineId = 7L,
            splittable = true
        ).schedule!!
        assertTrue(schedule.isPrn)
        assertNull(schedule.weekdaysMask)
        assertNull(schedule.monthlyDay)
        assertEquals(1, schedule.everyNDays)
        assertEquals(3, schedule.prnMaxPerDay)
    }

    @Test
    fun `不可掰开药品的非整单位剂量校验失败`() {
        val notSplittable = draftToSchedule(
            form = form(doseText = "0.5"),
            medicineId = 1L,
            splittable = false
        )
        assertNull(notSplittable.schedule)
        assertTrue(notSplittable.errors.contains(ScheduleFormError.DOSE_NOT_INTEGER))

        val splittable = draftToSchedule(
            form = form(doseText = "0.5"),
            medicineId = 1L,
            splittable = true
        )
        assertEquals(5, splittable.schedule!!.doseQtyX10)

        val wholeUnit = draftToSchedule(
            form = form(doseText = "2"),
            medicineId = 1L,
            splittable = false
        )
        assertEquals(20, wholeUnit.schedule!!.doseQtyX10)
    }

    @Test
    fun `每周节奏与结束日期往返一致`() {
        val endDate = LocalDate.of(2027, 1, 31)
        val schedule = draftToSchedule(
            form = form(
                recurrence = RecurrenceKind.WEEKLY,
                weekdays = setOf(1, 5),
                longTerm = false,
                endDate = endDate,
                doseText = "1.5"
            ),
            medicineId = 2L,
            splittable = true
        ).schedule!!
        assertEquals(17, schedule.weekdaysMask) // 周一 1 + 周五 16
        assertEquals(endDate, schedule.endDate)
        assertEquals(15, schedule.doseQtyX10)

        val draft = scheduleToDraft(schedule)
        assertEquals(RecurrenceKind.WEEKLY, draft.recurrence)
        assertEquals(setOf(1, 5), draft.weekdays)
        assertEquals("1.5", draft.doseText)
        assertFalse(draft.longTerm)
        assertEquals(endDate, draft.endDate)
        assertEquals("30", draft.earlyText)
        assertEquals("60", draft.lateText)
    }

    @Test
    fun `缺少年月日或间隔非法时返回对应错误`() {
        val weekly = draftToSchedule(
            form = form(recurrence = RecurrenceKind.WEEKLY),
            medicineId = 1L,
            splittable = true
        )
        assertNull(weekly.schedule)
        assertTrue(weekly.errors.contains(ScheduleFormError.WEEKDAYS_EMPTY))

        val everyN = draftToSchedule(
            form = form(recurrence = RecurrenceKind.EVERY_N_DAYS, everyNDaysText = "1"),
            medicineId = 1L,
            splittable = true
        )
        assertTrue(everyN.errors.contains(ScheduleFormError.EVERY_N_DAYS_INVALID))

        val monthly = draftToSchedule(
            form = form(recurrence = RecurrenceKind.MONTHLY, monthlyDayText = "32"),
            medicineId = 1L,
            splittable = true
        )
        assertTrue(monthly.errors.contains(ScheduleFormError.MONTHLY_DAY_INVALID))
    }

    @Test
    fun `结束日期早于开始日期校验失败`() {
        val result = draftToSchedule(
            form = form(longTerm = false, endDate = startDate.minusDays(1)),
            medicineId = 1L,
            splittable = true
        )
        assertNull(result.schedule)
        assertTrue(result.errors.contains(ScheduleFormError.END_DATE_INVALID))
    }
}