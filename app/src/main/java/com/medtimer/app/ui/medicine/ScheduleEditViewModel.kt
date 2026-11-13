package com.medtimer.app.ui.medicine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtimer.app.R
import com.medtimer.app.ServiceLocator
import com.medtimer.app.data.entity.TaperStep
import com.medtimer.app.domain.formatDoseQty
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleEditUiState(
    val loading: Boolean = true,
    val medicineName: String = "",
    val doseUnit: String = "",
    val splittable: Boolean = true,
    val isNew: Boolean = true,
    val form: ScheduleForm = ScheduleForm(),
    val taperEnabled: Boolean = false,
    val taperSteps: List<TaperStepDraft> = emptyList(),
    val errors: Set<ScheduleFormError> = emptySet(),
    val previewText: String = ""
)

/**
 * 计划编辑：锚点、节奏、剂量与提醒有效时段。
 * 表单每次变化都重算"规则预览"，保存前统一校验；不静默取整剂量。
 */
class ScheduleEditViewModel : ViewModel() {

    private val medicineRepository = ServiceLocator.medicineRepository
    private val scheduleRepository = ServiceLocator.scheduleRepository
    private val settingsRepository = ServiceLocator.settingsRepository
    private val alarmScheduler = ServiceLocator.alarmScheduler
    private val formatter = MedicineTextFormatter(ServiceLocator.appContext)
    private val context = ServiceLocator.appContext

    private val _uiState = MutableStateFlow(ScheduleEditUiState())
    val uiState: StateFlow<ScheduleEditUiState> = _uiState.asStateFlow()

    private val _close = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val close: SharedFlow<Unit> = _close.asSharedFlow()

    private var loadedMedicineId: Long = -1L
    private var loadedScheduleId: Long = -1L

    fun load(medicineId: Long, scheduleId: Long) {
        if (loadedMedicineId == medicineId && loadedScheduleId == scheduleId) return
        loadedMedicineId = medicineId
        loadedScheduleId = scheduleId

        viewModelScope.launch {
            val medicine = medicineRepository.get(medicineId)
            val settings = settingsRepository.get()
            val existing = if (scheduleId != 0L) scheduleRepository.getById(scheduleId) else null
            val existingSteps = if (scheduleId != 0L) {
                scheduleRepository.getTaperSteps(scheduleId)
            } else {
                emptyList()
            }
            val form = existing?.let { scheduleToDraft(it) } ?: ScheduleForm(
                startDate = LocalDate.now(),
                earlyText = settings.defaultEarlyMinutes.toString(),
                lateText = settings.defaultLateMinutes.toString()
            )
            _uiState.value = ScheduleEditUiState(
                loading = false,
                medicineName = medicine?.name.orEmpty(),
                doseUnit = medicine?.doseUnit.orEmpty(),
                splittable = medicine?.splittable ?: true,
                isNew = existing == null,
                form = form,
                taperEnabled = existing?.taperEnabled ?: false,
                taperSteps = existingSteps.map { step ->
                    TaperStepDraft(
                        startDate = step.startDate,
                        doseText = formatDoseQty(step.doseQtyX10, Locale.US)
                    )
                }
            )
            refreshPreview()
        }
    }

    fun updateForm(transform: (ScheduleForm) -> ScheduleForm) {
        _uiState.update { it.copy(form = transform(it.form), errors = emptySet()) }
        refreshPreview()
    }

    fun save() {
        val state = _uiState.value
        // 按需用药没有固定节奏，递减方案不生效
        val taperActive = state.taperEnabled && !state.form.isPrn
        val taperResult = if (taperActive) {
            buildTaperSteps(state.taperSteps, state.splittable)
        } else {
            TaperBuildResult()
        }
        val result = draftToSchedule(
            form = state.form,
            medicineId = loadedMedicineId,
            splittable = state.splittable,
            scheduleId = if (state.isNew) 0L else loadedScheduleId,
            taperEnabled = taperActive
        )
        val schedule = result.schedule
        if (schedule == null || taperResult.errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = result.errors + taperResult.errors) }
            return
        }
        viewModelScope.launch {
            val scheduleId = scheduleRepository.save(schedule)
            scheduleRepository.saveTaperSteps(
                scheduleId = scheduleId,
                steps = taperResult.steps.map { step ->
                    TaperStep(
                        scheduleId = scheduleId,
                        startDate = step.startDate,
                        doseQtyX10 = step.doseQtyX10
                    )
                }
            )
            alarmScheduler.rescheduleAll()
            _close.emit(Unit)
        }
    }

    /** 开启时用当前表单生成第一步，关闭时清空步骤。 */
    fun setTaperEnabled(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                taperEnabled = enabled,
                taperSteps = if (enabled) {
                    state.taperSteps.ifEmpty {
                        listOf(
                            TaperStepDraft(
                                startDate = state.form.startDate,
                                doseText = state.form.doseText
                            )
                        )
                    }
                } else {
                    emptyList()
                },
                errors = emptySet()
            )
        }
    }

    fun addTaperStep() {
        _uiState.update { state ->
            val last = state.taperSteps.lastOrNull()
            val newStep = TaperStepDraft(
                startDate = (last?.startDate ?: state.form.startDate).plusDays(1),
                doseText = last?.doseText ?: state.form.doseText
            )
            state.copy(taperSteps = state.taperSteps + newStep, errors = emptySet())
        }
    }

    fun updateTaperStep(index: Int, startDate: LocalDate? = null, doseText: String? = null) {
        _uiState.update { state ->
            if (index !in state.taperSteps.indices) return@update state
            val steps = state.taperSteps.toMutableList()
            val current = steps[index]
            steps[index] = current.copy(
                startDate = startDate ?: current.startDate,
                doseText = doseText ?: current.doseText
            )
            state.copy(taperSteps = steps, errors = emptySet())
        }
    }

    fun removeTaperStep(index: Int) {
        _uiState.update { state ->
            if (index !in state.taperSteps.indices) return@update state
            state.copy(
                taperSteps = state.taperSteps.filterIndexed { i, _ -> i != index },
                errors = emptySet()
            )
        }
    }

    fun delete() {
        if (loadedScheduleId == 0L) return
        viewModelScope.launch {
            scheduleRepository.delete(loadedScheduleId)
            alarmScheduler.rescheduleAll()
            _close.emit(Unit)
        }
    }

    /** 表单变化后重算预览，非法输入按默认值兜底，保证始终有一句话。 */
    private fun refreshPreview() {
        val state = _uiState.value
        if (state.loading) return
        val preview = draftToPreviewSchedule(state.form, loadedMedicineId)
        val rule = formatter.ruleText(preview, state.doseUnit)
        _uiState.update {
            it.copy(previewText = context.getString(R.string.schedule_preview, rule))
        }
    }
}