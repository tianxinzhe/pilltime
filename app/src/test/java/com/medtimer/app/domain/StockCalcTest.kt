package com.medtimer.app.domain

import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.domain.model.AnchorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** 库存与消耗估算。对应测试用例 TC-STOCK-05、07、09、10。 */
class StockCalcTest {

    private val today = LocalDate.of(2026, 11, 12)

    private fun schedule(
        doseQtyX10: Int,
        everyNDays: Int = 1,
        start: LocalDate = today,
        id: Long = 0
    ) = Schedule(
        id = id,
        medicineId = 1,
        anchorType = AnchorType.CLOCK,
        clockHour = 8,
        clockMinute = 0,
        doseQtyX10 = doseQtyX10,
        startDate = start,
        everyNDays = everyNDays
    )

    @Test
    fun `TC-STOCK-05 半片扣5单位并显示0点5片`() {
        val half = schedule(doseQtyX10 = 5)
        assertEquals(5, futureConsumptionX10(listOf(half), today, days = 1))
        assertEquals(150, futureConsumptionX10(listOf(half), today, days = 30))
        assertEquals(20.0, daysLeft(100, listOf(half), today), 1e-9)
        assertEquals("0.5", formatDoseQty(5, Locale.US))
    }

    @Test
    fun `TC-STOCK-07 隔天药15片约可用30天`() {
        val everyOtherDay = schedule(doseQtyX10 = 10, everyNDays = 2)
        assertEquals(150, futureConsumptionX10(listOf(everyOtherDay), today, days = 30))
        assertEquals(30.0, daysLeft(150, listOf(everyOtherDay), today), 1e-9)
    }

    @Test
    fun `TC-STOCK-09 库存与剩余天数求和口径一致`() {
        val daily = schedule(doseQtyX10 = 10)
        val total = futureConsumptionX10(listOf(daily), today, days = 30)
        assertEquals(300, total)

        listOf(0, 100, 200, 300, 450).forEach { stock ->
            val expected = if (stock == 0) 0.0 else stock / (total / 30.0)
            assertEquals("stock=$stock", expected, daysLeft(stock, listOf(daily), today), 1e-9)
        }
        assertTrue(daysLeft(100, emptyList(), today).isInfinite())
    }

    @Test
    fun `TC-STOCK-10 早1片加晚半片30天共45片`() {
        val morning = schedule(doseQtyX10 = 10, id = 1)
        val evening = schedule(doseQtyX10 = 5, id = 2)
        val schedules = listOf(morning, evening)

        assertEquals(450, futureConsumptionX10(schedules, today, days = 30))
        assertEquals(30.0, daysLeft(450, schedules, today), 1e-9)
        assertEquals(15.0, daysLeft(225, schedules, today), 1e-9)
    }

    @Test
    fun `低库存与有效期提示的边界`() {
        assertTrue(isLowStock(5.0, thresholdDays = 7))
        assertFalse(isLowStock(Double.POSITIVE_INFINITY, thresholdDays = 7))
        assertFalse(isExpiringBeforeRunOut(null, today, 10.0))
        assertFalse(isExpiringBeforeRunOut(today.plusDays(90), today, 30.0))
        assertTrue(isExpiringBeforeRunOut(today.plusDays(10), today, 30.0))
    }
}