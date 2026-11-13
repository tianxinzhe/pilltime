package com.medtimer.app.alarm

import com.medtimer.app.ServiceLocator
import com.medtimer.app.data.repo.MissedRecomputeResult
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 前台补算：每次 App 回到前台补写漏服，并汇总提示。
 * 静默时段内只更新状态供首页展示，不弹通知。
 */
object ForegroundRecompute {

    private val summaryFlow = MutableStateFlow<MissedRecomputeResult?>(null)
    val lastSummary: StateFlow<MissedRecomputeResult?> = summaryFlow

    private val degradedFlow = MutableStateFlow(false)
    val exactAlarmDegraded: StateFlow<Boolean> = degradedFlow

    suspend fun run(now: Instant = Instant.now()): MissedRecomputeResult {
        val context = ServiceLocator.appContext
        val scheduler = AlarmScheduler(context)
        degradedFlow.value = scheduler.isExactAlarmDegraded()

        val empty = MissedRecomputeResult(0, emptyList())
        val settings = ServiceLocator.settingsRepository.get()
        if (settings.allPaused && (settings.pausedUntil == null || settings.pausedUntil.isAfter(now))) {
            return empty
        }

        val result = ServiceLocator.doseRepository.recomputeMissed(now)
        summaryFlow.value = result

        val zone = ZoneId.systemDefault()
        val localTime = now.atZone(zone).toLocalTime()
        val inQuietHours = isInQuietHours(localTime, settings.quietStart, settings.quietEnd)
        if (result.missedCount > 0 && !inQuietHours) {
            NotificationHelper(context).showMissedSummary(result.missedCount)
        }
        return result
    }

    /** 首页消费后清空汇总，避免重复提示。 */
    fun consumeSummary() {
        summaryFlow.value = null
    }

    /** 静默时段判定，支持跨零点（如 22:00–07:00）。 */
    private fun isInQuietHours(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            !now.isBefore(start) || now.isBefore(end)
        }
}