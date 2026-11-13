package com.medtimer.app.ui.today

import com.medtimer.app.data.entity.AppSettings
import com.medtimer.app.data.entity.DoseLog
import com.medtimer.app.data.entity.Medicine
import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.data.entity.TaperStep
import com.medtimer.app.domain.aggregateSlots
import com.medtimer.app.domain.buildDaySlots
import com.medtimer.app.domain.canUndo
import com.medtimer.app.domain.doseQtyX10On
import com.medtimer.app.domain.isLowStock
import com.medtimer.app.domain.judgeDose
import com.medtimer.app.domain.model.DoseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 今日服药单的界面状态。本文件只做纯数据映射，不依赖 android.*，便于 JVM 单元测试。
 */
data class TodayUiState(
    val loading: Boolean = true,
    val dateText: String = "",
    val groups: List<TodayGroupUi> = emptyList(),
    val prn: List<PrnUi> = emptyList(),
    val notDueToday: List<NotDueUi> = emptyList(),
    val takenCount: Int = 0,
    val totalCount: Int = 0,
    val missedSummary: Int = 0,
    val exactAlarmDegraded: Boolean = false,
    val notificationsDisabled: Boolean = false,
    val paused: Boolean = false,
    val hasAnyPlan: Boolean = true
)

data class TodayGroupUi(
    val groupKey: String,
    val timeText: String,
    val slots: List<TodaySlotUi>
)

data class TodaySlotUi(
    val scheduleId: Long,
    val medicineId: Long,
    val medicineName: String,
    val doseText: String,
    val status: DoseStatus,
    val statusText: String,
    val canUndo: Boolean,
    val logId: Long?,
    val thumbPath: String?,
    val stockText: String?,
    val lowStock: Boolean,
    val imagePath: String?,
    /** 计划时刻，写记录时需要精确定位 slot。 */
    val plannedAt: Instant
)

data class NotDueUi(
    val medicineId: Long,
    val medicineName: String,
    val nextDateText: String
)

/** 按需用药条目：每日上限只用于提示，不参与拦截。 */
data class PrnUi(
    val scheduleId: Long,
    val medicineName: String,
    val doseText: String,
    val takenCount: Int,
    val maxPerDay: Int?
)

/**
 * 首页文案来源：由 UI 层用 strings.xml 实现，映射层只负责挑选与排序。
 */
interface TodayTextProvider {
    fun date(date: LocalDate): String
    fun time(time: LocalDateTime): String
    fun dose(qtyX10: Int, doseUnit: String): String
    fun status(status: DoseStatus): String
    fun notDueNext(nextDate: LocalDate): String
    fun stockDays(days: Int): String
    fun stockEmpty(): String
}

/** 已有有效记录、计入"已处理"的状态。 */
private val SETTLED_STATUSES = setOf(
    DoseStatus.TAKEN,
    DoseStatus.LATE,
    DoseStatus.SKIPPED,
    DoseStatus.UNCERTAIN
)

/**
 * 把当天的计划、记录、库存与设置映射为界面状态。
 * [nextDueDates] 与 [stockDays] 由 ViewModel 预先从仓库取好，保持本函数可纯 JVM 测试。
 */
fun buildTodayUiState(
    date: LocalDate,
    now: Instant,
    zone: ZoneId,
    medicines: List<Medicine>,
    schedules: List<Schedule>,
    taperStepsBySchedule: Map<Long, List<TaperStep>>,
    logs: List<DoseLog>,
    settings: AppSettings,
    nextDueDates: Map<Long, LocalDate>,
    stockDays: Map<Long, Double>,
    texts: TodayTextProvider,
    loading: Boolean = false,
    exactAlarmDegraded: Boolean = false,
    notificationsDisabled: Boolean = false
): TodayUiState {
    val slots = buildDaySlots(date, schedules, taperStepsBySchedule, settings, zone)
    val medicinesById = medicines.associateBy { it.id }

    // 同一 slot 只认一条未作废记录，重复写入时取最新一条
    val logBySlot = logs.asSequence()
        .filter { it.voidedAt == null }
        .groupBy { it.scheduleId to it.plannedAt }
        .mapValues { (_, entries) -> entries.maxByOrNull { it.id }!! }

    val groups = aggregateSlots(slots, settings.mergeWindowMinutes).map { group ->
        TodayGroupUi(
            groupKey = group.groupKey,
            timeText = texts.time(group.slots.first().plannedLocal),
            slots = group.slots.map { slot ->
                val log = logBySlot[slot.scheduleId to slot.plannedAt]
                val status = log?.status
                    ?: judgeDose(slot.plannedAt, null, now, slot.earlyMinutes, slot.lateMinutes).status
                val medicine = medicinesById[slot.medicineId]
                val days = stockDays[slot.medicineId]
                TodaySlotUi(
                    scheduleId = slot.scheduleId,
                    medicineId = slot.medicineId,
                    medicineName = medicine?.name.orEmpty(),
                    doseText = texts.dose(slot.doseQtyX10, medicine?.doseUnit.orEmpty()),
                    status = status,
                    statusText = texts.status(status),
                    canUndo = log != null &&
                        (status == DoseStatus.TAKEN || status == DoseStatus.LATE) &&
                        log.actedAt?.let { canUndo(it, now) } == true,
                    logId = log?.id,
                    thumbPath = medicine?.thumbPath,
                    stockText = stockText(days, texts),
                    lowStock = days != null &&
                        (days <= 0.0 || isLowStock(days, settings.lowStockDaysThreshold)),
                    imagePath = medicine?.imagePath,
                    plannedAt = slot.plannedAt
                )
            }
        )
    }

    val allSlotUis = groups.flatMap { it.slots }
    val slotsByMedicine = allSlotUis.groupBy { it.medicineId }

    // 按需用药不进入定时 slot，单独成块；每日上限仅展示，不阻止确认
    val prn = schedules
        .filter { it.enabled && it.isPrn }
        .mapNotNull { schedule ->
            val medicine = medicinesById[schedule.medicineId] ?: return@mapNotNull null
            val takenCount = logs.count { log ->
                log.voidedAt == null &&
                    log.scheduleId == schedule.id &&
                    (log.status == DoseStatus.TAKEN || log.status == DoseStatus.LATE)
            }
            PrnUi(
                scheduleId = schedule.id,
                medicineName = medicine.name,
                doseText = texts.dose(
                    schedule.doseQtyX10On(date, taperStepsBySchedule[schedule.id].orEmpty()),
                    medicine.doseUnit
                ),
                takenCount = takenCount,
                maxPerDay = schedule.prnMaxPerDay
            )
        }

    // 今天没有任务的药也要出现一行"今天不用服用"，避免隔天药被误认为漏服
    val notDueToday = medicines.mapNotNull { medicine ->
        if (medicine.id in slotsByMedicine) return@mapNotNull null
        val hasEnabledPlan = schedules.any {
            it.medicineId == medicine.id && it.enabled && !it.isPrn
        }
        if (!hasEnabledPlan) return@mapNotNull null
        val nextDate = nextDueDates[medicine.id] ?: return@mapNotNull null
        NotDueUi(
            medicineId = medicine.id,
            medicineName = medicine.name,
            nextDateText = texts.notDueNext(nextDate)
        )
    }

    return TodayUiState(
        loading = loading,
        dateText = texts.date(date),
        groups = groups,
        prn = prn,
        notDueToday = notDueToday,
        takenCount = allSlotUis.count { it.status in SETTLED_STATUSES },
        totalCount = allSlotUis.size,
        missedSummary = allSlotUis.count { it.status == DoseStatus.MISSED },
        exactAlarmDegraded = exactAlarmDegraded,
        notificationsDisabled = notificationsDisabled,
        paused = settings.allPaused &&
            (settings.pausedUntil == null || settings.pausedUntil.isAfter(now)),
        hasAnyPlan = schedules.any { it.enabled }
    )
}

/** 库存为 0 提示补药；无穷大表示按当前规则永远吃不完，不展示天数。 */
private fun stockText(days: Double?, texts: TodayTextProvider): String? = when {
    days == null -> null
    days <= 0.0 -> texts.stockEmpty()
    days.isFinite() -> texts.stockDays(days.toInt())
    else -> null
}