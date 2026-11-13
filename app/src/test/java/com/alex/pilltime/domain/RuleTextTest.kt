package com.alex.pilltime.domain

import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.domain.model.AnchorType
import com.alex.pilltime.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** 规则整句回显与剂量格式化。 */
class RuleTextTest {

    private val date = LocalDate.of(2026, 11, 12)

    private fun everyOtherDayAfterBreakfast() = Schedule(
        medicineId = 1,
        anchorType = AnchorType.MEAL,
        mealType = MealType.BREAKFAST,
        offsetMin = 0,
        doseQtyX10 = 10,
        startDate = date,
        everyNDays = 2
    )

    /** 内存中的英文假实现，模拟 strings.xml。 */
    private class EnResources : RuleTextResources {
        override fun daily(dosePhrase: String) = "Every day · $dosePhrase"
        override fun everyOtherDay(dosePhrase: String) = "Every other day · $dosePhrase"
        override fun everyNDays(days: Int, dosePhrase: String) = "Every $days days · $dosePhrase"
        override fun weekly(days: String, dosePhrase: String) = "Every $days · $dosePhrase"
        override fun monthly(day: Int, dosePhrase: String) = "Day $day of each month · $dosePhrase"
        override fun clockPhrase(doseText: String, timeText: String) = "$doseText at $timeText"
        override fun mealPhrase(doseText: String, meal: MealType, offsetMin: Int): String {
            val name = when (meal) {
                MealType.BREAKFAST -> "breakfast"
                MealType.LUNCH -> "lunch"
                MealType.DINNER -> "dinner"
                MealType.BEDTIME -> "bedtime"
            }
            val relation = if (offsetMin < 0) "before" else "after"
            return "$doseText $relation $name"
        }

        override fun weekdayList(mask: Int) = "Monday"
    }

    /** 内存中的中文假实现，模拟 strings.xml。 */
    private class ZhResources : RuleTextResources {
        override fun daily(dosePhrase: String) = "每天 · $dosePhrase"
        override fun everyOtherDay(dosePhrase: String) = "隔天 · $dosePhrase"
        override fun everyNDays(days: Int, dosePhrase: String) = "每 $days 天 · $dosePhrase"
        override fun weekly(days: String, dosePhrase: String) = "每周$days · $dosePhrase"
        override fun monthly(day: Int, dosePhrase: String) = "每月 $day 号 · $dosePhrase"
        override fun clockPhrase(doseText: String, timeText: String) = "$timeText $doseText"
        override fun mealPhrase(doseText: String, meal: MealType, offsetMin: Int): String {
            val name = when (meal) {
                MealType.BREAKFAST -> "早饭"
                MealType.LUNCH -> "午饭"
                MealType.DINNER -> "晚饭"
                MealType.BEDTIME -> "睡前"
            }
            val relation = if (offsetMin < 0) "前" else "后"
            return "$name$relation $doseText"
        }

        override fun weekdayList(mask: Int) = "一"
    }

    @Test
    fun `英文规则整句量词在饭点前`() {
        val text = everyOtherDayAfterBreakfast().describeRule(
            EnResources(),
            RuleTextInput(doseText = "1 capsule", timeText = "8:00 AM")
        )
        assertEquals("Every other day · 1 capsule after breakfast", text)
    }

    @Test
    fun `中文规则整句`() {
        val text = everyOtherDayAfterBreakfast().describeRule(
            ZhResources(),
            RuleTextInput(doseText = "1 粒", timeText = "8:00")
        )
        assertEquals("隔天 · 早饭后 1 粒", text)
    }

    @Test
    fun `剂量格式化不引入浮点误差`() {
        assertEquals("0.5", formatDoseQty(5, Locale.US))
        assertEquals("1", formatDoseQty(10, Locale.US))
        assertEquals("2.5", formatDoseQty(25, Locale.US))
        assertEquals("0,5", formatDoseQty(5, Locale.GERMAN))

        assertTrue(isSingleUnit(10))
        assertFalse(isSingleUnit(5))
        assertFalse(isSingleUnit(20))
    }
}