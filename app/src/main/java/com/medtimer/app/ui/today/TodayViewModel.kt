package com.medtimer.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtimer.app.ServiceLocator
import com.medtimer.app.data.entity.Schedule
import com.medtimer.app.domain.model.DoseStatus
import com.medtimer.app.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 今日服药单：把当天的设置、药品、计划与记录合并成界面状态。
 * 刷新只重取"今天"与"当前时刻"，其余数据来自仓库 Flow，操作后无需手动刷新。
 */
class TodayViewModel : ViewModel() {

    private val settingsRepository = ServiceLocator.settingsRepository
    private val medicineRepository = ServiceLocator.medicineRepository
    private val scheduleRepository = ServiceLocator.scheduleRepository
    private val doseRepository = ServiceLocator.doseRepository
    private val stockRepository = ServiceLocator.stockRepository
    private val alarmScheduler = ServiceLocator.alarmScheduler

    private val zone: ZoneId = ZoneId.systemDefault()
    private val refreshFlow = MutableStateFlow(RefreshState(LocalDate.now(zone), 0L))
    private val texts: TodayTextProvider = AndroidTodayTextProvider(ServiceLocator.appContext)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = combine(
        refreshFlow,
        settingsRepository.settings
    ) { refresh, settings -> refresh to settings }
        .flatMapLatest { (refresh, settings) ->
            combine(
                medicineRepository.observeMedicines(settings.activeProfileId),
                scheduleRepository.observeSchedules(settings.activeProfileId),
                doseRepository.observeDay(refresh.date, zone)
            ) { medicines, schedules, logs -> Triple(medicines, schedules, logs) }
                .map { (medicines, schedules, logs) ->
                    val now = Instant.now()
                    val taperSteps = scheduleRepository.getAllTaperSteps()
                    // 今天没有任务的药需要算出下次应服日期，供"今天不用服用"一行展示
                    val nextDueDates = medicines.mapNotNull { medicine ->
                        scheduleRepository
                            .nextDueDateFor(medicine.id, refresh.date.plusDays(1))
                            ?.let { medicine.id to it }
                    }.toMap()
                    val stockDays = medicines.associate { medicine ->
                        medicine.id to stockRepository.remainingDays(medicine.id, refresh.date)
                    }
                    buildTodayUiState(
                        date = refresh.date,
                        now = now,
                        zone = zone,
                        medicines = medicines,
                        schedules = schedules,
                        taperStepsBySchedule = taperSteps,
                        logs = logs,
                        settings = settings,
                        nextDueDates = nextDueDates,
                        stockDays = stockDays,
                        texts = texts,
                        exactAlarmDegraded = alarmScheduler.isExactAlarmDegraded(),
                        notificationsDisabled = !alarmScheduler.areNotificationsEnabled()
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState()
        )

    /** 回到前台后重取今天与当前时刻；跨天后会重建当日记录订阅。 */
    fun refresh() {
        refreshFlow.value = RefreshState(LocalDate.now(zone), System.nanoTime())
    }

    /** 标记某组内（[groupKey] 为空表示全部）待服用项为已服；库存不足不拦截。 */
    fun markTakenAll(groupKey: String? = null) {
        val targets = uiState.value.groups
            .filter { groupKey == null || it.groupKey == groupKey }
            .flatMap { it.slots }
            .filter { it.status == DoseStatus.DUE }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            for (slot in targets) {
                val schedule = scheduleRepository.getById(slot.scheduleId) ?: continue
                doseRepository.markTaken(schedule, slot.plannedAt)
            }
        }
    }

    fun markTaken(slot: TodaySlotUi) {
        withSchedule(slot) { schedule ->
            doseRepository.markTaken(schedule, slot.plannedAt)
        }
    }

    /** 按需用药：立即记录一次服用；每日上限只提示，绝不拦截。 */
    fun markPrnTaken(item: PrnUi) {
        viewModelScope.launch {
            val schedule = scheduleRepository.getById(item.scheduleId) ?: return@launch
            doseRepository.markPrnTaken(schedule)
            // PRN 不进定时提醒，但库存与今日统计会变化，需同步桌面小组件
            WidgetUpdater.refreshAll(ServiceLocator.appContext)
        }
    }

    fun skip(slot: TodaySlotUi) {
        withSchedule(slot) { schedule ->
            doseRepository.markSkipped(schedule, slot.plannedAt)
        }
    }

    fun uncertain(slot: TodaySlotUi) {
        withSchedule(slot) { schedule ->
            doseRepository.markUncertain(schedule, slot.plannedAt)
        }
    }

    fun undo(logId: Long) {
        viewModelScope.launch {
            doseRepository.undo(logId)
        }
    }

    /** 暂停状态下手动恢复提醒。 */
    fun resumeReminders() {
        viewModelScope.launch {
            settingsRepository.setPaused(null)
        }
    }

    private fun withSchedule(slot: TodaySlotUi, action: suspend (Schedule) -> Unit) {
        viewModelScope.launch {
            val schedule = scheduleRepository.getById(slot.scheduleId) ?: return@launch
            action(schedule)
        }
    }

    /** token 仅用于让 StateFlow 感知刷新（同一天内多次刷新也要重算）。 */
    private data class RefreshState(val date: LocalDate, val token: Long)
}