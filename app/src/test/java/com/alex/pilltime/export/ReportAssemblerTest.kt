package com.alex.pilltime.export

import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.domain.model.AnchorType
import com.alex.pilltime.domain.model.DoseStatus
import com.alex.pilltime.domain.model.MedicineForm
import com.alex.pilltime.ui.history.HistoryStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 报告组装口径（纯 JVM，不依赖 android.*）。 */
class ReportAssemblerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val day: LocalDate = LocalDate.of(2026, 11, 12)

    private val medicine = Medicine(
        id = 1,
        name = "阿司匹林",
        form = MedicineForm.TABLET,
        specValue = 100.0,
        specUnit = "mg",
        doseUnit = "片",
        splittable = false,
        packSize = 30,
        packUnit = "盒"
    )

    private val settings = AppSettings()

    /** 测试用文案：全部为事实描述，不含任何结论性用语。 */
    private val texts = object : ReportTextProvider {
        override fun ruleText(schedule: Schedule, doseUnit: String): String = "每天 · 1 片"

        override fun doseText(qtyX10: Int, doseUnit: String): String = "${qtyX10 / 10} 片"

        override fun timeText(time: java.time.LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

        override fun statusText(status: HistoryStatus): String = when (status) {
            HistoryStatus.ON_TIME -> "已服用"
            HistoryStatus.LATE -> "延迟服用"
            HistoryStatus.UNCONFIRMED -> "未确认"
            HistoryStatus.UNCERTAIN -> "记不清"
            HistoryStatus.SKIPPED -> "已跳过"
        }

        override fun remaining(stockText: String): String = "剩余 $stockText"

        override fun ruleSeparator(): String = "；"

        override fun unconfirmedAnomaly(period: ReportPeriod, count: Int): String =
            when (period) {
                ReportPeriod.MORNING -> "上午有 $count 次未确认"
                ReportPeriod.AFTERNOON -> "下午有 $count 次未确认"
                ReportPeriod.EVENING -> "晚间有 $count 次未确认"
            }

        override fun backfilledAnomaly(count: Int): String = "有 $count 次为事后补记"
    }

    private fun schedule(id: Long, hour: Int, startDate: LocalDate = day.minusDays(30)) = Schedule(
        id = id,
        medicineId = medicine.id,
        anchorType = AnchorType.CLOCK,
        clockHour = hour,
        clockMinute = 0,
        doseQtyX10 = 10,
        startDate = startDate,
        lateMinutes = 60
    )

    private fun at(hour: Int, minute: Int = 0): Instant =
        day.atTime(hour, minute).atZone(zone).toInstant()

    private fun log(
        schedule: Schedule,
        status: DoseStatus,
        certain: Boolean = true
    ): DoseLog = DoseLog(
        scheduleId = schedule.id,
        medicineId = medicine.id,
        plannedAt = at(schedule.clockHour ?: 0),
        actedAt = when (status) {
            DoseStatus.TAKEN, DoseStatus.LATE -> at((schedule.clockHour ?: 0) + 1)
            else -> null
        },
        status = status,
        certain = certain
    )

    private fun assemble(
        schedules: List<Schedule>,
        logs: List<DoseLog>,
        today: LocalDate = day,
        rangeDays: Int = 1
    ): ReportAssembly = assembleReport(
        rangeDays = rangeDays,
        today = today,
        zone = zone,
        settings = settings,
        medicines = listOf(medicine),
        schedules = schedules,
        taperSteps = emptyMap(),
        logs = logs,
        stockX10ByMedicine = mapOf(medicine.id to 50),
        texts = texts,
        now = at(23, 59)
    )

    @Test
    fun `统计五类之和等于应服总次数`() {
        val morning = schedule(1, 8)
        val noon = schedule(2, 12)
        val evening = schedule(3, 20)

        val result = assemble(
            schedules = listOf(morning, noon, evening),
            logs = listOf(
                log(morning, DoseStatus.TAKEN),
                log(noon, DoseStatus.LATE)
            )
        )

        val data = (result as ReportAssembly.Ready).data
        val counts = data.counts

        assertEquals(1, counts.onTime)
        assertEquals(1, counts.late)
        assertEquals(1, counts.unconfirmed)
        assertEquals(0, counts.uncertain)
        assertEquals(0, counts.skipped)
        assertEquals(3, counts.total)
        assertEquals(
            counts.total,
            counts.onTime + counts.late + counts.unconfirmed + counts.uncertain + counts.skipped
        )
    }

    @Test
    fun `范围无任何应服时刻且无记录时返回空信号`() {
        val notStarted = schedule(1, 8, startDate = day.plusDays(1))

        val result = assemble(schedules = listOf(notStarted), logs = emptyList())

        assertTrue(result is ReportAssembly.Empty)
    }

    @Test
    fun `异常提示只陈述事实且不含结论性用语`() {
        val morning = schedule(1, 8)
        val evening = schedule(2, 20)

        val result = assemble(
            schedules = listOf(morning, evening),
            // 早晨为事后补记，晚间无记录 → 未确认
            logs = listOf(log(morning, DoseStatus.TAKEN, certain = false))
        )

        val anomalies = (result as ReportAssembly.Ready).data.anomalies

        assertTrue(anomalies.contains("晚间有 1 次未确认"))
        assertTrue(anomalies.contains("有 1 次为事后补记"))

        val banned = listOf("建议", "诊断", "应当", "依从性", "漏服严重")
        for (text in anomalies) {
            for (word in banned) {
                assertFalse("异常提示不应包含结论性用语：$word → $text", text.contains(word))
            }
        }
    }

    @Test
    fun `文件名只含前缀与日期不含药名`() {
        val result = assemble(
            schedules = listOf(schedule(1, 8)),
            logs = listOf(log(schedule(1, 8), DoseStatus.TAKEN))
        )
        val data = (result as ReportAssembly.Ready).data

        val fileName = reportFileName("用药记录", data.generatedAt)

        assertEquals("用药记录_20261112.pdf", fileName)
        assertFalse(fileName.contains(medicine.name))
        assertFalse(fileName.contains("阿司匹林"))
    }
}