package com.alex.pilltime.alarm

import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.widget.WidgetUpdater

/**
 * 提醒操作共用的写库逻辑：广播接收器与提醒页直接复用，避免广播绕行。
 */
object ReminderActions {

    /** 已服用：逐个 slot 写 TAKEN，幂等。 */
    suspend fun markTaken(group: ReminderGroup) {
        for (slot in group.slots) {
            // 测试提醒的 scheduleId 为 0，取不到计划时直接跳过
            if (slot.scheduleId <= 0L) continue
            val schedule = ServiceLocator.scheduleRepository.getById(slot.scheduleId) ?: continue
            ServiceLocator.doseRepository.markTaken(schedule, slot.plannedAt)
        }
        WidgetUpdater.refreshAll(ServiceLocator.appContext)
    }

    /** 跳过：写 SKIPPED，不动库存。 */
    suspend fun markSkipped(group: ReminderGroup) {
        for (slot in group.slots) {
            if (slot.scheduleId <= 0L) continue
            val schedule = ServiceLocator.scheduleRepository.getById(slot.scheduleId) ?: continue
            ServiceLocator.doseRepository.markSkipped(schedule, slot.plannedAt)
        }
        WidgetUpdater.refreshAll(ServiceLocator.appContext)
    }

    /** 记不清：写 UNCERTAIN。 */
    suspend fun markUncertain(group: ReminderGroup) {
        for (slot in group.slots) {
            if (slot.scheduleId <= 0L) continue
            val schedule = ServiceLocator.scheduleRepository.getById(slot.scheduleId) ?: continue
            ServiceLocator.doseRepository.markUncertain(schedule, slot.plannedAt)
        }
        WidgetUpdater.refreshAll(ServiceLocator.appContext)
    }
}