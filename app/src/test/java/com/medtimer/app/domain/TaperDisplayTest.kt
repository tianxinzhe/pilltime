package com.medtimer.app.domain

import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.data.entity.TaperStep
import com.medtimer.app.domain.model.AnchorType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 递减方案回显：阶段数与当前阶段剂量。 */
class TaperDisplayTest {

    private val date = LocalDate.of(2026, 11, 13)

    private fun schedule(taperEnabled: Boolean = true) = Schedule(
        id = 1,
        medicineId = 1,
        anchorType = AnchorType.CLOCK,
        clockHour = 8,
        clockMinute = 0,
        doseQtyX10 = 10,
        startDate = LocalDate.of(2026, 10, 1),
        taperEnabled = taperEnabled
    )

    private val steps = listOf(
        TaperStep(scheduleId = 1, startDate = LocalDate.of(2026, 11, 1), doseQtyX10 = 20),
        TaperStep(scheduleId = 1, startDate = LocalDate.of(2026, 11, 8), doseQtyX10 = 10),
        TaperStep(scheduleId = 1, startDate = LocalDate.of(2026, 11, 20), doseQtyX10 = 5)
    )

    @Test
    fun `三个阶段时取当前阶段剂量并展示阶段数`() {
        val display = schedule().taperDisplay(date, steps)
        assertEquals(10, display?.currentDoseX10)
        assertEquals(3, display?.stageCount)
    }

    @Test
    fun `全部阶段都在未来时仍取最早阶段并展示阶段数`() {
        val display = schedule().taperDisplay(LocalDate.of(2026, 10, 20), steps)
        assertEquals(20, display?.currentDoseX10)
        assertEquals(3, display?.stageCount)
    }

    @Test
    fun `没有步骤时回退基础剂量且不展示`() {
        val schedule = schedule()
        assertNull(schedule.taperDisplay(date, emptyList()))
        assertEquals(10, schedule.doseQtyX10On(date, emptyList()))
    }

    @Test
    fun `未启用递减时不展示`() {
        assertNull(schedule(taperEnabled = false).taperDisplay(date, steps))
        assertEquals(10, schedule(taperEnabled = false).doseQtyX10On(date, steps))
    }
}