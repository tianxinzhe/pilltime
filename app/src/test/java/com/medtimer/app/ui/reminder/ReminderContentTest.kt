package com.medtimer.app.ui.reminder

import com.medtimer.app.domain.formatDoseQty
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** 提醒页文案构造（纯 JVM，不依赖 android.*）。 */
class ReminderContentTest {

    private fun doseText(qtyX10: Int, doseUnit: String): String {
        val qty = formatDoseQty(qtyX10, Locale.US)
        return if (doseUnit.isBlank()) qty else "$qty $doseUnit"
    }

    private fun itemFormat(name: String, dose: String): String = "$name $dose."

    private fun template(items: String): String = "Time to take your medicine. $items"

    @Test
    fun `单药生成一行列表文案与播报文案`() {
        val lines = ReminderContent.lines(
            slots = listOf(ReminderSlotText("Aspirin", doseQtyX10 = 10, doseUnit = "tablet")),
            unknownName = "Medication",
            formatDose = ::doseText
        )

        assertEquals(1, lines.size)
        assertEquals(ReminderLine("Aspirin", "1 tablet"), lines[0])
        assertEquals(
            listOf("Aspirin · 1 tablet"),
            ReminderContent.listTexts(lines) { name, dose -> "$name · $dose" }
        )
        assertEquals(
            "Time to take your medicine. Aspirin 1 tablet.",
            ReminderContent.spokenText(lines, ::itemFormat, " ", ::template)
        )
    }

    @Test
    fun `多药逐条列出并按顺序拼接播报`() {
        val lines = ReminderContent.lines(
            slots = listOf(
                ReminderSlotText("Aspirin", doseQtyX10 = 10, doseUnit = "tablet"),
                ReminderSlotText("Vitamin D", doseQtyX10 = 5, doseUnit = "tablet")
            ),
            unknownName = "Medication",
            formatDose = ::doseText
        )

        assertEquals(2, lines.size)
        assertEquals(
            "Time to take your medicine. Aspirin 1 tablet. Vitamin D 0.5 tablet.",
            ReminderContent.spokenText(lines, ::itemFormat, " ", ::template)
        )
    }

    @Test
    fun `剂量 0_5 按最小单位保留一位小数`() {
        val lines = ReminderContent.lines(
            slots = listOf(ReminderSlotText("Vitamin D", doseQtyX10 = 5, doseUnit = "tablet")),
            unknownName = "Medication",
            formatDose = ::doseText
        )

        assertEquals("0.5 tablet", lines[0].doseText)
    }

    @Test
    fun `药名缺失或空白时回退到占位文案`() {
        val lines = ReminderContent.lines(
            slots = listOf(
                ReminderSlotText(null, doseQtyX10 = 10, doseUnit = "tablet"),
                ReminderSlotText("   ", doseQtyX10 = 5, doseUnit = "tablet")
            ),
            unknownName = "Medication",
            formatDose = ::doseText
        )

        assertEquals(listOf("Medication", "Medication"), lines.map { it.medicineName })
    }

    @Test
    fun `时间文案在调用点格式化一次`() {
        val triggerAt = Instant.parse("2026-11-12T00:00:00Z")
        var received: Instant? = null
        var calls = 0

        val text = ReminderContent.timeText(triggerAt) { instant ->
            received = instant
            calls++
            "08:00"
        }

        assertEquals("08:00", text)
        assertEquals(triggerAt, received)
        assertEquals(1, calls)
    }
}