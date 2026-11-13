package com.alex.pilltime.alarm

import android.content.Intent
import java.time.Instant

/**
 * 提醒相关的 Intent action 与 extras 统一定义。
 * 进程可能在触发时已被系统回收，因此闹钟/通知 Intent 必须携带完整组信息。
 */
object AlarmIntents {

    /** 通知按钮：已服用。 */
    const val ACTION_TAKEN = "com.alex.pilltime.alarm.action.TAKEN"

    /** 通知按钮：跳过。 */
    const val ACTION_SKIP = "com.alex.pilltime.alarm.action.SKIP"

    /** 通知按钮：稍后 10 分钟。 */
    const val ACTION_SNOOZE = "com.alex.pilltime.alarm.action.SNOOZE"

    /** 提醒页：记不清。 */
    const val ACTION_UNCERTAIN = "com.alex.pilltime.alarm.action.UNCERTAIN"

    const val EXTRA_GROUP_KEY = "com.alex.pilltime.alarm.extra.GROUP_KEY"
    const val EXTRA_SCHEDULE_IDS = "com.alex.pilltime.alarm.extra.SCHEDULE_IDS"
    const val EXTRA_MEDICINE_IDS = "com.alex.pilltime.alarm.extra.MEDICINE_IDS"
    const val EXTRA_PLANNED_ATS = "com.alex.pilltime.alarm.extra.PLANNED_ATS"
    const val EXTRA_DOSE_QTY_X10 = "com.alex.pilltime.alarm.extra.DOSE_QTY_X10"
    const val EXTRA_EARLY_MINUTES = "com.alex.pilltime.alarm.extra.EARLY_MINUTES"
    const val EXTRA_LATE_MINUTES = "com.alex.pilltime.alarm.extra.LATE_MINUTES"

    /** 通知 id，用于按钮操作后取消对应通知。 */
    const val EXTRA_NOTIFICATION_ID = "com.alex.pilltime.alarm.extra.NOTIFICATION_ID"

    /** 稍后提醒的延迟分钟数。 */
    const val EXTRA_SNOOZE_MINUTES = "com.alex.pilltime.alarm.extra.SNOOZE_MINUTES"

    /** 把 [ReminderGroup] 写入 Intent extras。 */
    fun putGroup(intent: Intent, group: ReminderGroup): Intent {
        intent.putExtra(EXTRA_GROUP_KEY, group.groupKey)
        intent.putExtra(EXTRA_SCHEDULE_IDS, group.slots.map { it.scheduleId }.toLongArray())
        intent.putExtra(EXTRA_MEDICINE_IDS, group.slots.map { it.medicineId }.toLongArray())
        intent.putExtra(EXTRA_PLANNED_ATS, group.slots.map { it.plannedAt.toEpochMilli() }.toLongArray())
        intent.putExtra(EXTRA_DOSE_QTY_X10, group.slots.map { it.doseQtyX10 }.toIntArray())
        intent.putExtra(EXTRA_EARLY_MINUTES, group.slots.map { it.earlyMinutes }.toIntArray())
        intent.putExtra(EXTRA_LATE_MINUTES, group.slots.map { it.lateMinutes }.toIntArray())
        return intent
    }

    /** 从 Intent extras 还原 [ReminderGroup]；数据不完整时返回 null。 */
    fun readGroup(intent: Intent?): ReminderGroup? {
        val source = intent ?: return null
        val groupKey = source.getStringExtra(EXTRA_GROUP_KEY) ?: return null
        val scheduleIds = source.getLongArrayExtra(EXTRA_SCHEDULE_IDS) ?: return null
        val medicineIds = source.getLongArrayExtra(EXTRA_MEDICINE_IDS) ?: return null
        val plannedAts = source.getLongArrayExtra(EXTRA_PLANNED_ATS) ?: return null
        val doseQtyX10 = source.getIntArrayExtra(EXTRA_DOSE_QTY_X10) ?: return null
        val earlyMinutes = source.getIntArrayExtra(EXTRA_EARLY_MINUTES) ?: return null
        val lateMinutes = source.getIntArrayExtra(EXTRA_LATE_MINUTES) ?: return null

        val size = scheduleIds.size
        if (medicineIds.size != size || plannedAts.size != size || doseQtyX10.size != size ||
            earlyMinutes.size != size || lateMinutes.size != size
        ) {
            return null
        }

        val slots = (0 until size).map { index ->
            GroupSlot(
                scheduleId = scheduleIds[index],
                medicineId = medicineIds[index],
                plannedAt = Instant.ofEpochMilli(plannedAts[index]),
                doseQtyX10 = doseQtyX10[index],
                earlyMinutes = earlyMinutes[index],
                lateMinutes = lateMinutes[index]
            )
        }
        return ReminderGroup(groupKey = groupKey, slots = slots)
    }
}