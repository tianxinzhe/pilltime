package com.alex.pilltime.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.data.entity.DoseLog
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.domain.buildDaySlots
import com.alex.pilltime.ui.history.HistoryStatus.UNCONFIRMED
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryUiState(
    val loading: Boolean = true,
    val rangeDays: Int = 30,
    val counts: HistoryCounts = HistoryCounts(),
    val days: List<HistoryDayUi> = emptyList()
) {
    val isEmpty: Boolean get() = !loading && counts.total == 0
}

data class HistoryDayUi(
    val date: LocalDate,
    val dateText: String,
    val rows: List<HistoryRowUi>
)

data class HistoryRowUi(
    val scheduleId: Long,
    val plannedAt: Instant,
    val timeText: String,
    val medicineName: String,
    val doseText: String,
    val status: HistoryStatus,
    val statusText: String,
    val delayText: String?,
    val certain: Boolean
)

/**
 * 历史/统计：按范围逐日重建应服 slot，再与记录合并后汇总。
 * 90 天量级的计算全部在 IO 协程完成，主线程只接收结果。
 */
class HistoryViewModel : ViewModel() {

    private val medicineRepository = ServiceLocator.medicineRepository
    private val scheduleRepository = ServiceLocator.scheduleRepository
    private val doseRepository = ServiceLocator.doseRepository
    private val settingsRepository = ServiceLocator.settingsRepository

    private val zone: ZoneId = ZoneId.systemDefault()
    private val texts: HistoryTextProvider = AndroidHistoryTextProvider(ServiceLocator.appContext)
    private val rangeDaysFlow = MutableStateFlow(DEFAULT_RANGE_DAYS)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = combine(
        rangeDaysFlow,
        settingsRepository.settings
    ) { days, settings -> days to settings }
        .flatMapLatest { (days, settings) ->
            val today = LocalDate.now(zone)
            // 近 N 天含今天，因此回看 N - 1 天
            val from = today.minusDays(days.toLong() - 1)
            combine(
                medicineRepository.observeMedicines(settings.activeProfileId),
                scheduleRepository.observeSchedules(settings.activeProfileId),
                doseRepository.observeRange(from, today, zone)
            ) { medicines, schedules, logs ->
                // 记录表带 profileId，显式过滤，避免多档案记录混入统计
                Triple(
                    medicines,
                    schedules,
                    logs.filter { it.profileId == settings.activeProfileId }
                )
            }
                .map { (medicines, schedules, logs) ->
                    withContext(Dispatchers.IO) {
                        buildUiState(days, from, today, medicines, schedules, logs, settings)
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState()
        )

    fun setRange(days: Int) {
        if (days !in RANGE_OPTIONS) return
        rangeDaysFlow.value = days
    }

    /** 补记：按“吃过了”写入一条记录，certain=false 表示补记。 */
    fun backfillTaken(row: HistoryRowUi) {
        if (row.status != UNCONFIRMED) return
        viewModelScope.launch {
            val schedule = scheduleRepository.getById(row.scheduleId) ?: return@launch
            doseRepository.markTaken(schedule, row.plannedAt, certain = false)
        }
    }

    /** 补记为记不清：状态单列，不扣库存。 */
    fun backfillUncertain(row: HistoryRowUi) {
        if (row.status != UNCONFIRMED) return
        viewModelScope.launch {
            val schedule = scheduleRepository.getById(row.scheduleId) ?: return@launch
            doseRepository.markUncertain(schedule, row.plannedAt)
        }
    }

    private suspend fun buildUiState(
        rangeDays: Int,
        from: LocalDate,
        today: LocalDate,
        medicines: List<Medicine>,
        schedules: List<Schedule>,
        logs: List<DoseLog>,
        settings: AppSettings
    ): HistoryUiState {
        val enabledSchedules = schedules.filter { it.enabled }
        val taperSteps = scheduleRepository.getAllTaperSteps()
        val medicinesById = medicines.associateBy { it.id }
        val now = Instant.now()

        val items = mutableListOf<HistoryDayItem>()
        var date = from
        while (!date.isAfter(today)) {
            val slots = buildDaySlots(date, enabledSchedules, taperSteps, settings, zone)
            items += mergeDayRecords(date, slots, logs, now)
            date = date.plusDays(1)
        }

        // 最新的日期排在前面，同一天内按计划时刻升序
        val days = items.groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (day, dayItems) ->
                HistoryDayUi(
                    date = day,
                    dateText = texts.date(day),
                    rows = dayItems.sortedBy { it.plannedAt }.map { it.toRow(medicinesById) }
                )
            }

        return HistoryUiState(
            loading = false,
            rangeDays = rangeDays,
            counts = summarize(items),
            days = days
        )
    }

    private fun HistoryDayItem.toRow(medicinesById: Map<Long, Medicine>): HistoryRowUi {
        val medicine = medicinesById[medicineId]
        return HistoryRowUi(
            scheduleId = scheduleId,
            plannedAt = plannedAt,
            timeText = texts.time(plannedAt),
            medicineName = medicine?.name.orEmpty(),
            doseText = texts.dose(doseQtyX10, medicine?.doseUnit.orEmpty()),
            status = status,
            statusText = texts.status(status),
            delayText = delayMinutes?.let { texts.late(it) },
            certain = certain
        )
    }

    private companion object {
        val RANGE_OPTIONS = setOf(7, 30, 90)
        const val DEFAULT_RANGE_DAYS = 30
    }
}