package com.medtimer.app.domain

import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.domain.model.AnchorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 时刻聚合。对应测试用例 TC-STAT-12、13。 */
class SlotMergeTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 11, 12)

    private fun scheduleAt(id: Long, hour: Int, minute: Int) = Schedule(
        id = id,
        medicineId = id,
        anchorType = AnchorType.CLOCK,
        clockHour = hour,
        clockMinute = minute,
        doseQtyX10 = 10,
        startDate = date
    )

    private fun instant(hour: Int, minute: Int): java.time.Instant =
        LocalDateTime.of(2026, 11, 12, hour, minute).atZone(zone).toInstant()

    @Test
    fun `TC-STAT-12 相差8分钟合并为一次提醒`() {
        val slots = buildDaySlots(
            date,
            listOf(scheduleAt(1, 8, 0), scheduleAt(2, 8, 8)),
            zone = zone
        )
        val groups = aggregateSlots(slots, mergeWindowMinutes = 10)

        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals(2, group.slots.size)
        assertEquals(instant(8, 0), group.triggerAt)
        assertEquals(instant(9, 8), group.latestWindowEnd)
        assertTrue(group.groupKey.startsWith("1@"))
        assertEquals(listOf(1L, 2L), group.slots.map { it.scheduleId })
    }

    @Test
    fun `TC-STAT-13 相差20分钟不合并`() {
        val slots = buildDaySlots(
            date,
            listOf(scheduleAt(1, 8, 0), scheduleAt(2, 8, 20)),
            zone = zone
        )
        val groups = aggregateSlots(slots, mergeWindowMinutes = 10)

        assertEquals(2, groups.size)
        assertEquals(1, groups[0].slots.size)
        assertEquals(1, groups[1].slots.size)
        assertEquals(instant(8, 0), groups[0].triggerAt)
        assertEquals(instant(8, 20), groups[1].triggerAt)
    }
}