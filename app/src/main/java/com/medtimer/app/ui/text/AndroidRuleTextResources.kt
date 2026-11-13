package com.medtimer.app.ui.text

import android.content.Context
import com.medtimer.app.R
import com.medtimer.app.domain.RuleTextResources
import com.medtimer.app.domain.formatDoseQty
import com.medtimer.app.domain.isSingleUnit
import com.medtimer.app.domain.model.MealType

/**
 * 规则整句回显的 Android 实现：所有模板取自 strings.xml，不在代码里拼文案。
 */
class AndroidRuleTextResources(private val context: Context) : RuleTextResources {

    override fun daily(dosePhrase: String): String =
        context.getString(R.string.rule_daily, dosePhrase)

    override fun everyOtherDay(dosePhrase: String): String =
        context.getString(R.string.rule_every_other_day, dosePhrase)

    override fun everyNDays(days: Int, dosePhrase: String): String =
        context.getString(R.string.rule_every_n_days, days, dosePhrase)

    override fun weekly(days: String, dosePhrase: String): String =
        context.getString(R.string.rule_weekly, days, dosePhrase)

    override fun monthly(day: Int, dosePhrase: String): String =
        context.getString(R.string.rule_monthly, day, dosePhrase)

    override fun clockPhrase(doseText: String, timeText: String): String =
        context.getString(R.string.rule_clock_phrase, doseText, timeText)

    /** offsetMin 为负表示饭前，正数表示饭后，0 表示与饭点同时。 */
    override fun mealPhrase(doseText: String, meal: MealType, offsetMin: Int): String {
        val mealName = context.getString(mealResId(meal))
        return when {
            offsetMin < 0 -> context.getString(
                R.string.rule_meal_before, doseText, -offsetMin, mealName
            )
            offsetMin > 0 -> context.getString(
                R.string.rule_meal_after, doseText, offsetMin, mealName
            )
            else -> context.getString(R.string.rule_meal_on, doseText, mealName)
        }
    }

    /** 把周掩码（周一为 bit0）转成按 locale 拼接的星期列表。 */
    override fun weekdayList(mask: Int): String {
        val separator = context.getString(R.string.weekday_separator)
        val names = (0 until 7).mapNotNull { index ->
            if ((mask and (1 shl index)) != 0) context.getString(WEEKDAY_RES_IDS[index]) else null
        }
        return names.joinToString(separator)
    }

    private fun mealResId(meal: MealType): Int = when (meal) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.BEDTIME -> R.string.meal_bedtime
    }

    private companion object {
        /** 周一至周日对应 strings.xml 的 weekday_mon..weekday_sun。 */
        val WEEKDAY_RES_IDS = intArrayOf(
            R.string.weekday_mon,
            R.string.weekday_tue,
            R.string.weekday_wed,
            R.string.weekday_thu,
            R.string.weekday_fri,
            R.string.weekday_sat,
            R.string.weekday_sun
        )
    }
}

/**
 * 剂量文案：英文用复数（1 tablet / 2 tablets / 0.5 tablets），
 * 中文统一为"1 片 / 2 片 / 0.5 片"。
 */
fun formatDoseText(context: Context, qtyX10: Int, doseUnit: String): String {
    val locale = context.resources.configuration.locales[0]
    val qty = formatDoseQty(qtyX10, locale)
    val pluralsResId = dosePluralsResId(doseUnit)
        ?: return context.getString(R.string.dose_unknown_format, qty, doseUnit)
    // 只有"恰好 1 个单位"才用单数；0.5 与 2 都用复数形式
    val quantity = if (isSingleUnit(qtyX10)) 1 else 2
    return context.resources.getQuantityString(pluralsResId, quantity, qty)
}

/** 按 Medicine.doseUnit 映射到剂量复数资源，未知单位返回 null。 */
private fun dosePluralsResId(doseUnit: String): Int? = when (doseUnit.trim().lowercase()) {
    "片", "tablet", "tablets" -> R.plurals.dose_tablet
    "粒", "capsule", "capsules" -> R.plurals.dose_capsule
    "袋", "sachet", "sachets" -> R.plurals.dose_sachet
    "ml" -> R.plurals.dose_ml
    "滴", "drop", "drops" -> R.plurals.dose_drop
    "喷", "spray", "sprays" -> R.plurals.dose_spray
    "u", "unit", "units" -> R.plurals.dose_unit
    else -> null
}