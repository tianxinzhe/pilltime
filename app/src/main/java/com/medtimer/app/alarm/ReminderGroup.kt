package com.medtimer.app.alarm

import com.medtimer.app.domain.SlotGroup
import java.time.Instant

/**
 * 一次提醒中需要展示/记录的最小信息，可跨进程通过 Intent extras 传递。
 */
data class GroupSlot(
    val scheduleId: Long,
    val medicineId: Long,
    val plannedAt: Instant,
    val doseQtyX10: Int,
    val earlyMinutes: Int,
    val lateMinutes: Int
)

/** 一次提醒（合并后的一组 slot）。 */
data class ReminderGroup(
    val groupKey: String,
    val slots: List<GroupSlot>
) {
    /** 组内最早计划时刻，用于提醒页展示。 */
    val triggerAt: Instant get() = slots.minOf { it.plannedAt }
}

/** 领域层聚合结果转为可跨进程传输的提醒组。 */
fun SlotGroup.toReminderGroup(): ReminderGroup = ReminderGroup(
    groupKey = groupKey,
    slots = slots.map { slot ->
        GroupSlot(
            scheduleId = slot.scheduleId,
            medicineId = slot.medicineId,
            plannedAt = slot.plannedAt,
            doseQtyX10 = slot.doseQtyX10,
            earlyMinutes = slot.earlyMinutes,
            lateMinutes = slot.lateMinutes
        )
    }
)