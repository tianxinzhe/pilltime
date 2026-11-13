package com.medtimer.app.domain

import com.medtimer.app.data.entity.AppSettings
import com.medtimer.app.data.entity.DoseLog
import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.data.entity.TaperStep
import com.medtimer.app.domain.model.DoseStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 某天某时的一次服药任务，由计划实时推导，不落库。 */
data class DoseSlot(
    val scheduleId: Long,
    val medicineId: Long,
    val plannedAt: Instant,
    val plannedLocal: LocalDateTime,
    val doseQtyX10: Int,
    val earlyMinutes: Int,
    val lateMinutes: Int,
    val isPrn: Boolean = false
)

/** 合并后的一次提醒。 */
data class SlotGroup(
    val groupKey: String,
    val triggerAt: Instant,
    val latestWindowEnd: Instant,
    val slots: List<DoseSlot>
)

/** 把计划在 [date] 当天实例化为 slot。PRN 计划与当天不服用时返回 null。 */
fun Schedule.toSlot(
    date: LocalDate,
    settings: AppSettings,
    taperSteps: List<TaperStep> = emptyList(),
    zone: ZoneId = ZoneId.systemDefault()
): DoseSlot? {
    if (isPrn) return null
    if (!isDueOn(date)) return null
    val plannedLocal = resolveTime(date, settings)
    return DoseSlot(
        scheduleId = id,
        medicineId = medicineId,
        plannedAt = plannedLocal.atZone(zone).toInstant(),
        plannedLocal = plannedLocal,
        doseQtyX10 = doseQtyX10On(date, taperSteps),
        earlyMinutes = earlyMinutes,
        lateMinutes = lateMinutes,
        isPrn = isPrn
    )
}

/** 生成 [date] 当天全部定时 slot，按计划时刻升序。 */
fun buildDaySlots(
    date: LocalDate,
    schedules: List<Schedule>,
    taperStepsBySchedule: Map<Long, List<TaperStep>> = emptyMap(),
    settings: AppSettings = AppSettings(),
    zone: ZoneId = ZoneId.systemDefault()
): List<DoseSlot> = schedules
    .mapNotNull { schedule ->
        schedule.toSlot(date, settings, taperStepsBySchedule[schedule.id].orEmpty(), zone)
    }
    .sortedBy { it.plannedAt }

/**
 * 合并相邻 slot：只合并同一本地日期、且与组内首个 slot 相差不超过 [mergeWindowMinutes] 的项。
 */
fun aggregateSlots(slots: List<DoseSlot>, mergeWindowMinutes: Int = 10): List<SlotGroup> {
    val mergeLimit = Duration.ofMinutes(mergeWindowMinutes.toLong())
    val groups = mutableListOf<MutableList<DoseSlot>>()

    for (slot in slots.sortedBy { it.plannedAt }) {
        val current = groups.lastOrNull()
        val first = current?.first()
        val mergeable = first != null &&
            first.plannedLocal.toLocalDate() == slot.plannedLocal.toLocalDate() &&
            Duration.between(first.plannedAt, slot.plannedAt) <= mergeLimit
        if (mergeable) {
            current!!.add(slot)
        } else {
            groups.add(mutableListOf(slot))
        }
    }

    return groups.map { group ->
        val head = group.first()
        SlotGroup(
            groupKey = "${head.scheduleId}@${head.plannedAt.toEpochMilli()}",
            triggerAt = group.minOf { it.plannedAt },
            latestWindowEnd = group.maxOf { windowEnd(it.plannedAt, it.lateMinutes) },
            slots = group
        )
    }
}

/**
 * 过滤掉已有有效记录（未作废且状态为 TAKEN/LATE/SKIPPED/UNCERTAIN）的 slot 后聚合。
 */
fun pendingGroups(
    slots: List<DoseSlot>,
    logs: List<DoseLog>,
    now: Instant,
    mergeWindowMinutes: Int = 10
): List<SlotGroup> {
    val settledStatuses = setOf(
        DoseStatus.TAKEN,
        DoseStatus.LATE,
        DoseStatus.SKIPPED,
        DoseStatus.UNCERTAIN
    )
    val settled = logs.asSequence()
        .filter { it.voidedAt == null && it.status in settledStatuses }
        .map { it.scheduleId to it.plannedAt }
        .toSet()
    val pending = slots.filter { (it.scheduleId to it.plannedAt) !in settled }
    return aggregateSlots(pending, mergeWindowMinutes)
}