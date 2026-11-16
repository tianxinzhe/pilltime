package com.alex.pilltime.ui.medicine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.data.entity.Schedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

data class ScheduleListUiState(
    val loading: Boolean = true,
    val groups: List<ScheduleGroupUi> = emptyList()
)

/**
 * 计划 tab：当前档案全部计划按药品分组。
 * 与药品列表页同源的 Flow，编辑/启停/删除计划后自动刷新。
 */
class ScheduleListViewModel : ViewModel() {

    private val medicineRepository = ServiceLocator.medicineRepository
    private val scheduleRepository = ServiceLocator.scheduleRepository
    private val settingsRepository = ServiceLocator.settingsRepository
    private val alarmScheduler = ServiceLocator.alarmScheduler
    private val formatter = MedicineTextFormatter(ServiceLocator.appContext)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ScheduleListUiState> = settingsRepository.settings
        .flatMapLatest { settings ->
            combine(
                medicineRepository.observeMedicines(settings.activeProfileId),
                scheduleRepository.observeSchedules(settings.activeProfileId)
            ) { medicines, schedules -> medicines to schedules }
                .map { (medicines, schedules) -> buildState(medicines, schedules) }
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

    private suspend fun buildState(
        medicines: List<Medicine>,
        schedules: List<Schedule>
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
        return ScheduleListUiState(loading = false, groups = groups)
    }
}
