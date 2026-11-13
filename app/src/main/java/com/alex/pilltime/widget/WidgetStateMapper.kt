package com.alex.pilltime.widget

import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.domain.DoseSlot
import com.alex.pilltime.domain.judgeDose
import com.alex.pilltime.domain.model.DoseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** 小组件展示状态。 */
enum class WidgetStatus { ACTIVE, DONE, NO_SCHEDULE, PAUSED }

/** 小组件需要展示的数据：剩余次数与下一次待服时间。 */
data class WidgetState(
    val status: WidgetStatus = WidgetStatus.NO_SCHEDULE,
    val remainingCount: Int = 0,
    val nextTime: LocalDateTime? = null
)

/** 小组件文案来源：由 Android 层用 strings.xml 实现，便于纯 JVM 测试。 */
interface WidgetTextProvider {
    fun remaining(count: Int): String
    fun next(time: LocalDateTime): String
    fun done(): String
    fun noSchedule(): String
    fun paused(): String
}

/** 主文案与副文案，分别对应布局中的大字号与下一行。 */
data class WidgetTexts(val primary: String, val secondary: String)

/**
 * 把今日 slot 与记录映射为小组件状态。
 * 剩余次数只统计无有效记录、且当前仍处于有效窗口（DUE）的 slot；
 * 已有 MISSED 等记录的 slot 一律不计入。
 */
fun mapWidgetState(
    date: LocalDate,
    slots: List<DoseSlot>,
    logs: List<DoseLog>,
    now: Instant,
    paused: Boolean
): WidgetState {
    // 跨日 slot 不参与今天的小组件统计
    val todaySlots = slots.filter { it.plannedLocal.toLocalDate() == date }

    val settled = logs.asSequence()
        .filter { it.voidedAt == null }
        .map { it.scheduleId to it.plannedAt }
        .toSet()

    val remaining = todaySlots
        .filter { slot ->
            (slot.scheduleId to slot.plannedAt) !in settled &&
                judgeDose(slot.plannedAt, null, now, slot.earlyMinutes, slot.lateMinutes).status ==
                DoseStatus.DUE
        }
        .sortedBy { it.plannedAt }

    val state = WidgetState(remainingCount = remaining.size, nextTime = remaining.firstOrNull()?.plannedLocal)
    return when {
        paused -> state.copy(status = WidgetStatus.PAUSED)
        todaySlots.isEmpty() -> state.copy(status = WidgetStatus.NO_SCHEDULE)
        remaining.isNotEmpty() -> state.copy(status = WidgetStatus.ACTIVE)
        else -> state.copy(status = WidgetStatus.DONE)
    }
}

/** 按状态挑选文案；副文案为空时由 UI 层隐藏。 */
fun buildWidgetTexts(state: WidgetState, texts: WidgetTextProvider): WidgetTexts =
    when (state.status) {
        WidgetStatus.PAUSED -> WidgetTexts(
            primary = texts.paused(),
            secondary = if (state.remainingCount > 0) texts.remaining(state.remainingCount) else ""
        )

        WidgetStatus.NO_SCHEDULE -> WidgetTexts(primary = texts.noSchedule(), secondary = "")
        WidgetStatus.DONE -> WidgetTexts(primary = texts.done(), secondary = "")
        WidgetStatus.ACTIVE -> WidgetTexts(
            primary = texts.remaining(state.remainingCount),
            secondary = state.nextTime?.let(texts::next).orEmpty()
        )
    }