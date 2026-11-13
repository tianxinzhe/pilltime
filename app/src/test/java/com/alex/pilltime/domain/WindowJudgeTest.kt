package com.alex.pilltime.domain

import com.alex.pilltime.domain.model.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 提醒窗口与状态判定。对应测试用例 TC-STAT-01~08、16。 */
class WindowJudgeTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val planned: Instant = LocalDateTime.of(2026, 11, 12, 8, 0).atZone(zone).toInstant()

    private fun at(hour: Int, minute: Int): Instant =
        LocalDateTime.of(2026, 11, 12, hour, minute).atZone(zone).toInstant()

    private fun judge(actedAt: Instant?, now: Instant) =
        judgeDose(planned, actedAt, now, earlyMinutes = 30, lateMinutes = 60)

    @Test
    fun `TC-STAT-01 窗口内确认记为按时`() {
        val result = judge(at(7, 45), at(7, 45))
        assertEquals(DoseStatus.TAKEN, result.status)
        // delayMinutes = actedAt − plannedAt，提前为负
        assertEquals(-15, result.delayMinutes)
    }

    @Test
    fun `TC-STAT-02 早于窗口起点仍为按时并标记提前`() {
        val result = judge(at(7, 20), at(7, 20))
        assertEquals(DoseStatus.TAKEN, result.status)
        assertEquals(-40, result.delayMinutes)
    }

    @Test
    fun `TC-STAT-03 窗口内最后一分钟`() {
        val result = judge(at(8, 59), at(8, 59))
        assertEquals(DoseStatus.TAKEN, result.status)
        assertEquals(59, result.delayMinutes)
    }

    @Test
    fun `TC-STAT-04 09点整仍在窗口内`() {
        val result = judge(at(9, 0), at(9, 0))
        assertEquals(DoseStatus.TAKEN, result.status)
        assertEquals(60, result.delayMinutes)
    }

    @Test
    fun `TC-STAT-05 09点01分算迟服`() {
        val result = judge(at(9, 1), at(9, 1))
        assertEquals(DoseStatus.LATE, result.status)
        assertEquals(61, result.delayMinutes)
    }

    @Test
    fun `TC-STAT-06 无记录到达窗口终点判定漏服`() {
        val beforeEnd = judge(null, at(8, 59))
        assertEquals(DoseStatus.DUE, beforeEnd.status)
        assertNull(beforeEnd.delayMinutes)

        assertEquals(DoseStatus.MISSED, judge(null, at(9, 0)).status)
        assertEquals(DoseStatus.MISSED, judge(null, at(9, 1)).status)
    }

    @Test
    fun `TC-STAT-07与08 跳过和记不清由用户显式选择`() {
        // 窗口判定只产出 TAKEN / LATE / DUE / MISSED，SKIPPED / UNCERTAIN 由用户操作写入
        val decision = judge(null, at(8, 0))
        assertEquals(DoseStatus.DUE, decision.status)
        assertNotEquals(DoseStatus.SKIPPED, decision.status)
        assertNotEquals(DoseStatus.UNCERTAIN, decision.status)
    }

    @Test
    fun `撤销窗口为5分钟且包含边界`() {
        assertTrue(canUndo(at(8, 0), at(8, 5)))
        assertFalse(canUndo(at(8, 0), at(8, 5).plusSeconds(1)))
        assertFalse(canUndo(at(8, 0), at(8, 6)))
    }

    @Test
    fun `TC-STAT-16 拒绝晚于未来30分钟的时刻`() {
        val now = at(8, 0)
        assertTrue(isActedAtAcceptable(at(8, 30), now))
        assertFalse(isActedAtAcceptable(at(8, 31), now))
        assertTrue(isActedAtAcceptable(at(7, 0), now))
    }
}