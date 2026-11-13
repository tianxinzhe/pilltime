package com.medtimer.app.alarm

import com.medtimer.app.data.entity.DoseLog
import com.medtimer.app.domain.DoseSlot
import com.medtimer.app.domain.SlotGroup
import com.medtimer.app.domain.pendingGroups
import com.medtimer.app.domain.windowEnd
import java.time.Instant

/**
 * 纯函数排程决策：不依赖 Android，便于单元测试。
 */
object AlarmPlanner {

    /**
     * 从一批 slot（通常为今天起 7 天）中挑出下一个还需要响的组。
     * - 窗口已结束（windowEnd <= now）的 slot 不再响；
     * - 已有有效记录的 slot 由 [pendingGroups] 过滤；
     * - 相邻 slot 按 [mergeWindowMinutes] 合并；
     * - 无剩余返回 null。
     */
    fun pickNextGroup(
        daySlots: List<DoseSlot>,
        logs: List<DoseLog>,
        now: Instant,
        mergeWindowMinutes: Int = 10
    ): SlotGroup? {
        val alive = daySlots.filter { windowEnd(it.plannedAt, it.lateMinutes).isAfter(now) }
        if (alive.isEmpty()) return null
        return pendingGroups(alive, logs, now, mergeWindowMinutes).firstOrNull()
    }
}