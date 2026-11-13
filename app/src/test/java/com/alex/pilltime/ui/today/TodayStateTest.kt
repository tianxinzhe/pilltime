package com.alex.pilltime.ui.today

import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.domain.model.AnchorType
import com.alex.pilltime.domain.model.DoseStatus
import com.alex.pilltime.domain.model.MedicineForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 今日分组 → UI 状态映射（纯 JVM，不依赖 android.*）。 */
class TodayStateTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val date: LocalDate = LocalDate.of(2026, 11, 12)
    private val settings = AppSettings()

    /** 用固定格式模拟 strings.xml 的文案输出。 */
    private class FakeTexts : TodayTextProvider {
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        override fun date(date: LocalDate) = date.toString()
        override fun time(time: LocalDateTime) = time.format(timeFormatter)
        override fun dose(qtyX10: Int, doseUnit: String) = "$qtyX10 $doseUnit"
        override fun status(status: DoseStatus) = when (status) {
            DoseStatus.DUE -> "Due"
            DoseStatus.MISSED -> "Unconfirmed"
            DoseStatus.TAKEN -> "Taken"
            DoseStatus.LATE -> "Taken late"
            DoseStatus.SKIPPED -> "Skipped"
            DoseStatus.UNCERTAIN -> "Unsure"
            DoseStatus.PENDING -> "Pending"
        }

        override fun notDueNext(nextDate: LocalDate) = "Not needed today · Next $nextDate"
        override fun stockDays(days: Int) = "In stock for about $days days"
        override fun stockEmpty() = "Low stock, please restock"
    }

    private fun at(hour: Int, minute: Int): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()

    private fun clockSchedule(
        id: Long,
        medicineId: Long,
        hour: Int,
        minute: Int,
        everyNDays: Int = 1,
        startDate: LocalDate = date
    ) = Schedule(
        id = id,
        medicineId = medicineId,
        anchorType = AnchorType.CLOCK,
        clockHour = hour,
        clockMinute = minute,
        doseQtyX10 = 10,
        startDate = startDate,
        everyNDays = everyNDays
    )

    private fun medicine(id: Long, name: String) = Medicine(
        id = id,
        name = name,
        form = MedicineForm.TABLET,
        specValue = 500.0,
        specUnit = "mg",
        doseUnit = "片",
        splittable = true,
        packSize = 10,
        packUnit = "片"
    )

    private fun build(
        now: Instant,
        schedules: List<Schedule>,
        medicines: List<Medicine>,
        logs: List<DoseLog> = emptyList(),
        nextDueDates: Map<Long, LocalDate> = emptyMap(),
        stockDays: Map<Long, Double> = emptyMap()
    ): TodayUiState = buildTodayUiState(
        date = date,
        now = now,
        zone = zone,
        medicines = medicines,
        schedules = schedules,
        taperStepsBySchedule = emptyMap(),
        logs = logs,
        settings = settings,
        nextDueDates = nextDueDates,
        stockDays = stockDays,
        texts = FakeTexts()
    )

    @Test
    fun `同一合并窗口内的时刻归为一组`() {
        val state = build(
            now = at(10, 0),
            schedules = listOf(
                clockSchedule(1, 1, 8, 0),
                clockSchedule(2, 2, 8, 8)
            ),
            medicines = listOf(medicine(1, "维生素 D3"), medicine(2, "钙片"))
        )

        assertEquals(1, state.groups.size)
        assertEquals("08:00", state.groups[0].timeText)
        assertEquals(2, state.groups[0].slots.size)
        assertEquals(2, state.totalCount)
        assertEquals(listOf("维生素 D3", "钙片"), state.groups[0].slots.map { it.medicineName })
    }

    @Test
    fun `有记录按记录状态展示 无记录按窗口判定`() {
        val schedules = listOf(
            clockSchedule(1, 1, 8, 0),
            clockSchedule(2, 2, 8, 10)
        )
        val medicines = listOf(medicine(1, "维生素 D3"), medicine(2, "钙片"))
        val log = DoseLog(
            id = 5,
            scheduleId = 1,
            medicineId = 1,
            plannedAt = at(8, 0),
            actedAt = at(8, 5),
            status = DoseStatus.LATE,
            delayMinutes = 5
        )

        // 09:00 尚未超过 08:10 计划的延迟窗口（+60 分钟），未记录的项应为待服用
        val due = build(
            now = at(9, 0),
            schedules = schedules,
            medicines = medicines,
            logs = listOf(log)
        )
        val recorded = due.groups[0].slots.first { it.scheduleId == 1L }
        val pending = due.groups[0].slots.first { it.scheduleId == 2L }

        assertEquals(DoseStatus.LATE, recorded.status)
        assertEquals("Taken late", recorded.statusText)
        assertEquals(5L, recorded.logId)
        assertFalse(recorded.canUndo)
        assertEquals(DoseStatus.DUE, pending.status)
        assertEquals("Due", pending.statusText)

        // 10:00 已过窗口，未记录的项变为未确认
        val missed = build(
            now = at(10, 0),
            schedules = schedules,
            medicines = medicines,
            logs = listOf(log)
        )
        assertEquals(
            "Unconfirmed",
            missed.groups[0].slots.first { it.scheduleId == 2L }.statusText
        )
    }

    @Test
    fun `今天不吃的药进入今天不用服用列表`() {
        val state = build(
            now = at(10, 0),
            // 隔天药：昨天开始，今天不是服药日
            schedules = listOf(
                clockSchedule(1, 1, 8, 0),
                clockSchedule(3, 3, 8, 0, everyNDays = 2, startDate = date.minusDays(1))
            ),
            medicines = listOf(medicine(1, "维生素 D3"), medicine(3, "阿仑膦酸钠")),
            nextDueDates = mapOf(3L to date.plusDays(1))
        )

        assertEquals(1, state.notDueToday.size)
        assertEquals(3L, state.notDueToday[0].medicineId)
        assertEquals("阿仑膦酸钠", state.notDueToday[0].medicineName)
        assertEquals(
            "Not needed today · Next ${date.plusDays(1)}",
            state.notDueToday[0].nextDateText
        )
    }

    @Test
    fun `已服与总数只统计今天有记录的项`() {
        val schedules = listOf(
            clockSchedule(1, 1, 8, 0),
            clockSchedule(2, 2, 8, 10)
        )
        val medicines = listOf(medicine(1, "维生素 D3"), medicine(2, "钙片"))

        val takenLog = DoseLog(
            id = 1,
            scheduleId = 1,
            medicineId = 1,
            plannedAt = at(8, 0),
            actedAt = at(8, 0),
            status = DoseStatus.TAKEN
        )
        val missedLog = DoseLog(
            id = 2,
            scheduleId = 2,
            medicineId = 2,
            plannedAt = at(8, 10),
            status = DoseStatus.MISSED
        )

        val state = build(
            now = at(10, 0),
            schedules = schedules,
            medicines = medicines,
            logs = listOf(takenLog, missedLog)
        )

        // 未确认不算"已服"，但计入总数与未确认汇总
        assertEquals(1, state.takenCount)
        assertEquals(2, state.totalCount)
        assertEquals(1, state.missedSummary)

        val skipped = build(
            now = at(10, 0),
            schedules = schedules,
            medicines = medicines,
            logs = listOf(
                takenLog,
                missedLog.copy(id = 3, status = DoseStatus.SKIPPED, actedAt = at(8, 10))
            )
        )
        assertEquals(2, skipped.takenCount)
        assertEquals(0, skipped.missedSummary)
    }

    @Test
    fun `库存天数与低库存文案`() {
        val state = build(
            now = at(10, 0),
            schedules = listOf(clockSchedule(1, 1, 8, 0), clockSchedule(2, 2, 8, 10)),
            medicines = listOf(medicine(1, "维生素 D3"), medicine(2, "钙片")),
            stockDays = mapOf(1L to 12.0, 2L to 0.0)
        )

        val first = state.groups[0].slots.first { it.medicineId == 1L }
        val empty = state.groups[0].slots.first { it.medicineId == 2L }

        assertEquals("In stock for about 12 days", first.stockText)
        assertFalse(first.lowStock)
        assertEquals("Low stock, please restock", empty.stockText)
        assertEquals(true, empty.lowStock)
    }
}