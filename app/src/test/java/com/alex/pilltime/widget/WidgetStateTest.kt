package com.alex.pilltime.widget

import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.domain.DoseSlot
import com.alex.pilltime.domain.model.DoseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 小组件状态映射（纯 JVM，不依赖 android.*）。 */
class WidgetStateTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val date: LocalDate = LocalDate.of(2026, 11, 12)

    private class FakeTexts : WidgetTextProvider {
        private val formatter = DateTimeFormatter.ofPattern("HH:mm")

        override fun remaining(count: Int) = "$count left"
        override fun next(time: LocalDateTime) = "Next ${time.format(formatter)}"
        override fun done() = "All done"
        override fun noSchedule() = "No plan"
        override fun paused() = "Paused"
    }

    private fun at(hour: Int, minute: Int, dayOffset: Long = 0): Instant =
        date.plusDays(dayOffset).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun slot(id: Long, hour: Int, minute: Int, dayOffset: Long = 0): DoseSlot {
        val local = LocalDateTime.of(date.plusDays(dayOffset), LocalTime.of(hour, minute))
        return DoseSlot(
            scheduleId = id,
            medicineId = id,
            plannedAt = local.atZone(zone).toInstant(),
            plannedLocal = local,
            doseQtyX10 = 10,
            earlyMinutes = 30,
            lateMinutes = 60
        )
    }

    private fun log(slot: DoseSlot, status: DoseStatus): DoseLog = DoseLog(
        scheduleId = slot.scheduleId,
        medicineId = slot.medicineId,
        plannedAt = slot.plannedAt,
        actedAt = if (status == DoseStatus.TAKEN) slot.plannedAt else null,
        status = status
    )

    private fun state(
        slots: List<DoseSlot>,
        logs: List<DoseLog> = emptyList(),
        now: Instant = at(10, 0),
        paused: Boolean = false
    ): WidgetState = mapWidgetState(date, slots, logs, now, paused)

    @Test
    fun `全部完成显示今天已完成`() {
        val eight = slot(1, 8, 0)
        val nine = slot(2, 9, 0)
        val result = state(
            slots = listOf(eight, nine),
            logs = listOf(log(eight, DoseStatus.TAKEN), log(nine, DoseStatus.TAKEN))
        )

        assertEquals(WidgetStatus.DONE, result.status)
        assertEquals(0, result.remainingCount)
        assertNull(result.nextTime)
        assertEquals("All done", buildWidgetTexts(result, FakeTexts()).primary)
    }

    @Test
    fun `部分完成统计剩余次数与下一次时间`() {
        val eight = slot(1, 8, 0)
        val noon = slot(2, 12, 0)
        val evening = slot(3, 18, 0)
        val result = state(
            slots = listOf(eight, noon, evening),
            logs = listOf(log(eight, DoseStatus.TAKEN))
        )

        assertEquals(WidgetStatus.ACTIVE, result.status)
        assertEquals(2, result.remainingCount)
        assertEquals(LocalDateTime.of(date, LocalTime.of(12, 0)), result.nextTime)

        val texts = buildWidgetTexts(result, FakeTexts())
        assertEquals("2 left", texts.primary)
        assertEquals("Next 12:00", texts.secondary)
    }

    @Test
    fun `已有MISSED的记录不计入剩余`() {
        val eight = slot(1, 8, 0)
        val noon = slot(2, 12, 0)
        val result = state(
            slots = listOf(eight, noon),
            logs = listOf(log(eight, DoseStatus.MISSED))
        )

        assertEquals(1, result.remainingCount)
        assertEquals(LocalDateTime.of(date, LocalTime.of(12, 0)), result.nextTime)

        // 两条都已写 MISSED 时不再有剩余，即使尚未到 12:00
        val allMissed = state(
            slots = listOf(eight, noon),
            logs = listOf(log(eight, DoseStatus.MISSED), log(noon, DoseStatus.MISSED))
        )
        assertEquals(WidgetStatus.DONE, allMissed.status)
        assertEquals(0, allMissed.remainingCount)
    }

    @Test
    fun `暂停状态优先展示提醒已暂停`() {
        val eight = slot(1, 8, 0)
        val noon = slot(2, 12, 0)
        val result = state(
            slots = listOf(eight, noon),
            logs = listOf(log(eight, DoseStatus.TAKEN)),
            paused = true
        )

        assertEquals(WidgetStatus.PAUSED, result.status)
        assertEquals(1, result.remainingCount)
        val texts = buildWidgetTexts(result, FakeTexts())
        assertEquals("Paused", texts.primary)
        assertEquals("1 left", texts.secondary)
    }

    @Test
    fun `今天没有计划显示今天没有安排`() {
        val result = state(slots = emptyList())

        assertEquals(WidgetStatus.NO_SCHEDULE, result.status)
        assertEquals(WidgetTexts("No plan", ""), buildWidgetTexts(result, FakeTexts()))
    }

    @Test
    fun `跨日不统计明天`() {
        val noon = slot(1, 12, 0)
        val tomorrow = slot(2, 8, 0, dayOffset = 1)
        val result = state(slots = listOf(noon, tomorrow))

        assertEquals(1, result.remainingCount)
        assertEquals(LocalDateTime.of(date, LocalTime.of(12, 0)), result.nextTime)

        val onlyTomorrow = state(slots = listOf(tomorrow))
        assertEquals(WidgetStatus.NO_SCHEDULE, onlyTomorrow.status)
        assertEquals(0, onlyTomorrow.remainingCount)
    }
}