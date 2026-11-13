package com.medtimer.app.domain

import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.domain.model.AnchorType
import com.medtimer.app.domain.model.MealType

/**
 * 规则整句回显的文案来源。UI 层用 strings.xml 实现，本层不得出现硬编码文案。
 */
interface RuleTextResources {
    fun daily(dosePhrase: String): String
    fun everyOtherDay(dosePhrase: String): String
    fun everyNDays(days: Int, dosePhrase: String): String
    fun weekly(days: String, dosePhrase: String): String
    fun monthly(day: Int, dosePhrase: String): String
    fun clockPhrase(doseText: String, timeText: String): String
    fun mealPhrase(doseText: String, meal: MealType, offsetMin: Int): String
    fun weekdayList(mask: Int): String
}

/** 剂量与时刻的已本地化片段。 */
data class RuleTextInput(val doseText: String, val timeText: String)

/**
 * 组合成一句完整的规则描述。
 * 先由锚点与剂量得到 dosePhrase，再按节奏选择整句模板，禁止片段拼接。
 */
fun Schedule.describeRule(resources: RuleTextResources, input: RuleTextInput): String {
    val dosePhrase = when (anchorType) {
        AnchorType.CLOCK -> resources.clockPhrase(input.doseText, input.timeText)
        AnchorType.MEAL -> resources.mealPhrase(
            input.doseText,
            mealType ?: MealType.BREAKFAST,
            offsetMin
        )
    }
    weekdaysMask?.let { mask ->
        return resources.weekly(resources.weekdayList(mask), dosePhrase)
    }
    monthlyDay?.let { day ->
        return resources.monthly(day, dosePhrase)
    }
    return when {
        everyNDays <= 1 -> resources.daily(dosePhrase)
        everyNDays == 2 -> resources.everyOtherDay(dosePhrase)
        else -> resources.everyNDays(everyNDays, dosePhrase)
    }
}