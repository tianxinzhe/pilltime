package com.alex.pilltime.ui.medicine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.domain.isDueOn
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 计划 tab 里一个药品分组的计划行。 */
data class ScheduleRowUi(
    val scheduleId: Long,
    val medicineId: Long,
    val ruleText: String,
    val taperText: String?,
    val enabled: Boolean
)

/** 按药品分组的计划列表。 */
data class ScheduleGroupUi(
    val medicineId: Long,
    val medicineName: String,
    val doseUnit: String,
    val rows: List<ScheduleRowUi>
)

/** 增加计划时可选的药品。 */
data class MedicineOptionUi(
    val medicineId: Long,
    val name: String
)

data class ScheduleListUiState(
    val loading: Boolean = true,
    val medicines: List<MedicineOptionUi> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateText: String = "",
    /** 选中当天应服的定时次数（按需用药没有固定时刻，不计入）。 */
    val dayDoseCount: Int = 0,
    val visibleMonth: YearMonth = YearMonth.now(),
    /** 可见月份里有定时用药的日期（epochDay），日历上打点。 */
    val dueDays: Set<Long> = emptySet(),
    val groups: List<ScheduleGroupUi> = emptyList()
)

/**
 * 计划 tab：日历 + 增加计划 + 按药品分组的全部计划。
 * 与药品列表页同源的 Flow，编辑/启停/删除计划后自动刷新。
 */
class ScheduleListViewModel : ViewModel() {

    private val medicineRepository = ServiceLocator.medicineRepository
    private val scheduleRepository = ServiceLocator.scheduleRepository
    private val settingsRepository = ServiceLocator.settingsRepository
    private val alarmScheduler = ServiceLocator.alarmScheduler
    private val formatter = MedicineTextFormatter(ServiceLocator.appContext)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _visibleMonth = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ScheduleListUiState> = settingsRepository.settings
        .flatMapLatest { settings ->
            combine(
                medicineRepository.observeMedicines(settings.activeProfileId),
                scheduleRepository.observeSchedules(settings.activeProfileId),
                _selectedDate,
                _visibleMonth
            ) { medicines, schedules, selectedDate, visibleMonth ->
                buildState(medicines, schedules, selectedDate, visibleMonth)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleListUiState()
        )

    fun setEnabled(scheduleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            scheduleRepository.setEnabled(scheduleId, enabled)
            alarmScheduler.rescheduleAll()
        }
    }

    fun delete(scheduleId: Long) {
        viewModelScope.launch {
            scheduleRepository.delete(scheduleId)
            alarmScheduler.rescheduleAll()
        }
    }

    /** 点选日期时同步切到该日期所在月份。 */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
    }

    fun setVisibleMonth(month: YearMonth) {
        _visibleMonth.value = month
    }

    private suspend fun buildState(
        medicines: List<Medicine>,
        schedules: List<Schedule>,
        selectedDate: LocalDate,
        visibleMonth: YearMonth
    ): ScheduleListUiState {
        val medicinesById = medicines.associateBy { it.id }
        val stepsBySchedule = scheduleRepository.getTaperStepsFor(schedules.map { it.id })
        val groups = schedules
            .groupBy { it.medicineId }
            .mapNotNull { (medicineId, medicineSchedules) ->
                val medicine = medicinesById[medicineId] ?: return@mapNotNull null
                ScheduleGroupUi(
                    medicineId = medicineId,
                    medicineName = medicine.name,
                    doseUnit = medicine.doseUnit,
                    rows = medicineSchedules
                        .sortedBy { it.id }
                        .map { schedule ->
                            ScheduleRowUi(
                                scheduleId = schedule.id,
                                medicineId = medicineId,
                                ruleText = formatter.ruleText(schedule, medicine.doseUnit),
                                taperText = formatter.taperText(
                                    schedule,
                                    stepsBySchedule[schedule.id].orEmpty(),
                                    medicine.doseUnit
                                ),
                                enabled = schedule.enabled
                            )
                        }
                )
            }
            .sortedBy { it.medicineName }
        return ScheduleListUiState(
            loading = false,
            medicines = medicines
                .map { MedicineOptionUi(medicineId = it.id, name = it.name) }
                .sortedBy { it.name },
            selectedDate = selectedDate,
            selectedDateText = formatter.dateText(selectedDate),
            dayDoseCount = dayDoseCount(schedules, selectedDate),
            visibleMonth = visibleMonth,
            dueDays = dueDays(schedules, visibleMonth),
            groups = groups
        )
    }

    private fun dayDoseCount(schedules: List<Schedule>, date: LocalDate): Int =
        schedules.count { !it.isPrn && it.isDueOn(date) }

    private fun dueDays(schedules: List<Schedule>, month: YearMonth): Set<Long> {
        val timed = schedules.filter { !it.isPrn }
        if (timed.isEmpty()) return emptySet()
        val days = mutableSetOf<Long>()
        for (offset in 0 until month.lengthOfMonth()) {
            val date = month.atDay(1).plusDays(offset.toLong())
            if (timed.any { it.isDueOn(date) }) days += date.toEpochDay()
        }
        return days
    }
}
