package com.alex.pilltime.domain

import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.domain.model.AnchorType
import com.alex.pilltime.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 锚点解析。对应测试用例 TC-TIME-01~07。 */
class AnchorResolveTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 11, 12)
    private val settings = AppSettings()

    private fun clockSchedule(hour: Int?, minute: Int?) = Schedule(
        medicineId = 1,
        anchorType = AnchorType.CLOCK,
        clockHour = hour,
        clockMinute = minute,
        doseQtyX10 = 10,
        startDate = date
    )

    private fun mealSchedule(meal: MealType?, offsetMin: Int = 0) = Schedule(
        medicineId = 1,
        anchorType = AnchorType.MEAL,
        mealType = meal,
        offsetMin = offsetMin,
        doseQtyX10 = 10,
        startDate = date
    )

    @Test
    fun `TC-TIME-01 固定钟点 8 点`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 12, 8, 0),
            clockSchedule(8, 0).resolveTime(date, settings)
        )
    }

    @Test
    fun `TC-TIME-02 早饭前30分钟`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 12, 7, 0),
            mealSchedule(MealType.BREAKFAST, -30).resolveTime(date, settings)
        )
    }

    @Test
    fun `TC-TIME-03 早饭后15分钟`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 12, 7, 45),
            mealSchedule(MealType.BREAKFAST, 15).resolveTime(date, settings)
        )
    }

    @Test
    fun `TC-TIME-04 睡前锚点`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 12, 22, 0),
            mealSchedule(MealType.BEDTIME, 0).resolveTime(date, settings)
        )
    }

    @Test
    fun `TC-TIME-05 晚饭后30分钟允许跨日`() {
        val lateSettings = settings.copy(dinner = LocalTime.of(23, 50))
        val expected = LocalDateTime.of(2026, 11, 13, 0, 20)
        assertEquals(expected, mealSchedule(MealType.DINNER, 30).resolveTime(date, lateSettings))
        assertEquals(
            expected.atZone(zone).toInstant(),
            mealSchedule(MealType.DINNER, 30).resolveInstant(date, lateSettings, zone)
        )
    }

    @Test
    fun `TC-TIME-06 未设置饭点时使用默认值`() {
        assertEquals(LocalTime.of(7, 30), settings.timeOf(MealType.BREAKFAST))
        assertEquals(LocalTime.of(12, 0), settings.timeOf(MealType.LUNCH))
        assertEquals(LocalTime.of(18, 30), settings.timeOf(MealType.DINNER))
        assertEquals(LocalTime.of(22, 0), settings.timeOf(MealType.BEDTIME))

        // CLOCK 缺省字段回退 8:00，MEAL 缺省饭次回退 8:00
        assertEquals(LocalDateTime.of(2026, 11, 12, 8, 0), clockSchedule(null, null).resolveTime(date, settings))
        assertEquals(LocalDateTime.of(2026, 11, 12, 8, 0), mealSchedule(null, 0).resolveTime(date, settings))
    }

    @Test
    fun `TC-TIME-07 当天临时改饭点只影响当天`() {
        val schedule = mealSchedule(MealType.BREAKFAST, -30)
        val todaySettings = settings.copy(breakfast = LocalTime.of(8, 30))

        assertEquals(
            LocalDateTime.of(2026, 11, 12, 8, 0),
            schedule.resolveTime(date, todaySettings)
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 13, 7, 0),
            schedule.resolveTime(date.plusDays(1), settings)
        )
    }
}