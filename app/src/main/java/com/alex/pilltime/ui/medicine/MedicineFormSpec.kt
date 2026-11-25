package com.alex.pilltime.ui.medicine

import com.alex.pilltime.R
import com.alex.pilltime.domain.model.MedicineForm

/**
 * 剂型驱动的表单规则：剂量单位、可选单位、剂量能否取半。
 * 数据库里保存的是稳定值（片/粒/ml/滴/喷/U），显示文案由 doseUnitLabelRes 本地化。
 */
internal fun formDefaultDoseUnit(form: MedicineForm): String = when (form) {
    MedicineForm.TABLET -> "片"
    MedicineForm.CAPSULE -> "粒"
    MedicineForm.LIQUID -> "ml"
    MedicineForm.DROPS -> "滴"
    MedicineForm.SPRAY -> "喷"
    MedicineForm.PEN -> "U"
}

/** 剂量单位候选：只有液体/滴剂在 ml 与滴之间二选一，其余剂型由剂型唯一决定。 */
internal fun formDoseUnitOptions(form: MedicineForm): List<String> = when (form) {
    MedicineForm.LIQUID -> listOf("ml", "滴")
    MedicineForm.DROPS -> listOf("滴", "ml")
    else -> listOf(formDefaultDoseUnit(form))
}

/** 非片剂剂型的"可以掰开"固定值：ml 常见 2.5 ml 这类小数剂量，其余都是整单位。 */
internal fun formFixedSplittable(form: MedicineForm): Boolean = form == MedicineForm.LIQUID

/** 只有片剂由用户勾选"可以掰开"。 */
internal fun isSplittableByUser(form: MedicineForm): Boolean = form == MedicineForm.TABLET

/** 剂型显示文案，取自 strings.xml，数据里保存枚举。 */
internal fun formLabelRes(form: MedicineForm): Int = when (form) {
    MedicineForm.TABLET -> R.string.medicine_form_tablet
    MedicineForm.CAPSULE -> R.string.medicine_form_capsule
    MedicineForm.LIQUID -> R.string.medicine_form_liquid
    MedicineForm.DROPS -> R.string.medicine_form_drops
    MedicineForm.SPRAY -> R.string.medicine_form_spray
    MedicineForm.PEN -> R.string.medicine_form_pen
}

/** 规格含量按整数优先展示，避免 500.0 这类多余小数。 */
internal fun specToText(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
