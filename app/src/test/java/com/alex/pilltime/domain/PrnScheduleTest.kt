package com.alex.pilltime.domain

import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.domain.model.AnchorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 按需用药（PRN）不参与定时排程。 */
class PrnScheduleTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 11, 12)

    private fun schedule(id: Long, isPrn: Boolean) = Schedule(
        id = id,
        medicineId = id,
        anchorType = AnchorType.CLOCK,
        clockHour = 8,
        clockMinute = 0,
        doseQtyX10 = 10,
        startDate = date,
        isPrn = isPrn
    )

    @Test
    fun `PRN 计划不产生定时 slot`() {
        val prn = schedule(1, isPrn = true)
        assertNull(prn.toSlot(date, AppSettings(), zone = zone))
        assertTrue(buildDaySlots(date, listOf(prn), zone = zone).isEmpty())
    }

    @Test
    fun `普通计划与 PRN 并存时只返回普通计划`() {
        val prn = schedule(1, isPrn = true)
        val regular = schedule(2, isPrn = false)

        val slots = buildDaySlots(date, listOf(prn, regular), zone = zone)

        assertEquals(1, slots.size)
        assertEquals(2L, slots.single().scheduleId)
        assertFalse(slots.single().isPrn)
    }

    @Test
    fun `PRN 不计入未来消耗`() {
        val prn = schedule(1, isPrn = true)
        assertEquals(0, futureConsumptionX10(listOf(prn), date, 30))
        assertTrue(daysLeft(100, listOf(prn), date).isInfinite())
    }
}