package com.alex.pilltime.domain

import com.alex.pilltime.domain.model.DoseStatus
import java.time.Duration
import java.time.Instant

/** 单次服药判定结果。delayMinutes 为 actedAt − plannedAt 的整分钟数，提前为负。 */
data class DoseDecision(val status: DoseStatus, val delayMinutes: Int?)

/** 提醒有效时段起点：计划时刻 − 提前量。 */
fun windowStart(plannedAt: Instant, earlyMinutes: Int): Instant =
    plannedAt.minusSeconds(earlyMinutes.toLong() * 60L)

/** 提醒有效时段终点：计划时刻 + 延迟量。 */
fun windowEnd(plannedAt: Instant, lateMinutes: Int): Instant =
    plannedAt.plusSeconds(lateMinutes.toLong() * 60L)

/**
 * 判定一次服药状态。
 * 有 actedAt：不晚于窗口终点 → TAKEN（含提前与两端边界），晚于终点 → LATE。
 * 无 actedAt：now 未到窗口终点 → DUE，否则 MISSED。
 */
fun judgeDose(
    plannedAt: Instant,
    actedAt: Instant?,
    now: Instant,
    earlyMinutes: Int,
    lateMinutes: Int
): DoseDecision {
    if (actedAt == null) {
        val end = windowEnd(plannedAt, lateMinutes)
        return DoseDecision(
            status = if (now.isBefore(end)) DoseStatus.DUE else DoseStatus.MISSED,
            delayMinutes = null
        )
    }
    val delayMinutes = Duration.between(plannedAt, actedAt).toMinutes().toInt()
    val status = if (actedAt.isAfter(windowEnd(plannedAt, lateMinutes))) {
        DoseStatus.LATE
    } else {
        DoseStatus.TAKEN
    }
    return DoseDecision(status, delayMinutes)
}

/** 撤销窗口：默认 5 分钟内（含边界）。 */
fun canUndo(actedAt: Instant, now: Instant, undoWindowMinutes: Int = 5): Boolean =
    !now.isAfter(actedAt.plusSeconds(undoWindowMinutes.toLong() * 60L))

/** 拒绝标记晚于当前时间 30 分钟以上的时刻（含 30 分钟边界）。 */
fun isActedAtAcceptable(actedAt: Instant, now: Instant, futureToleranceMinutes: Int = 30): Boolean =
    !actedAt.isAfter(now.plusSeconds(futureToleranceMinutes.toLong() * 60L))