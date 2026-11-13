package com.alex.pilltime.ui.reminder

import java.time.Instant

/**
 * 提醒页单个药品的文本素材：药名可能缺失，剂量按最小单位 x10 传入。
 */
data class ReminderSlotText(
    val medicineName: String?,
    val doseQtyX10: Int,
    val doseUnit: String
)

/** 提醒页一行：已解析好的药名与剂量文案。 */
data class ReminderLine(
    val medicineName: String,
    val doseText: String
)

/**
 * 提醒页的纯文本构造：不依赖 android.*，所有资源文案通过回调传入，便于 JVM 测试。
 */
object ReminderContent {

    /** 时间文案：格式化动作由调用方提供，可跟随系统 12/24 小时制。 */
    fun timeText(triggerAt: Instant, format: (Instant) -> String): String = format(triggerAt)

    /** 组内每个 slot 一行；药名缺失或为空白时回退到 [unknownName]。 */
    fun lines(
        slots: List<ReminderSlotText>,
        unknownName: String,
        formatDose: (qtyX10: Int, doseUnit: String) -> String
    ): List<ReminderLine> = slots.map { slot ->
        ReminderLine(
            medicineName = slot.medicineName?.takeIf { it.isNotBlank() } ?: unknownName,
            doseText = formatDose(slot.doseQtyX10, slot.doseUnit)
        )
    }

    /** 列表文案：药名与剂量合成一行。 */
    fun listTexts(
        lines: List<ReminderLine>,
        formatLine: (medicineName: String, doseText: String) -> String
    ): List<String> = lines.map { formatLine(it.medicineName, it.doseText) }

    /** 播报正文：逐条"药名 剂量."，再用整句模板包裹。 */
    fun spokenText(
        lines: List<ReminderLine>,
        itemFormat: (medicineName: String, doseText: String) -> String,
        separator: String,
        template: (items: String) -> String
    ): String {
        if (lines.isEmpty()) return ""
        val items = lines.joinToString(separator) { itemFormat(it.medicineName, it.doseText) }
        return template(items)
    }
}