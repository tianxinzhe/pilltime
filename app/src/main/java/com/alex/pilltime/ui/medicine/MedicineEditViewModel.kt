package com.alex.pilltime.ui.medicine

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.domain.model.MedicineForm
import com.alex.pilltime.scan.MedicineNameExtractor
import com.alex.pilltime.scan.MlKitRecognizer
import com.alex.pilltime.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 药品编辑页里的一条计划。 */
data class MedicineScheduleUi(
    val id: Long,
    val ruleText: String,
    val taperText: String?,
    val enabled: Boolean
)

data class MedicineEditUiState(
    val loading: Boolean = true,
    val medicineId: Long = 0L,
    val isNew: Boolean = true,
    val name: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val specValue: String = "",
    val specUnit: String = "mg",
    val doseUnit: String = "片",
    val splittable: Boolean = true,
    val packSize: String = "",
    val autoDeductStock: Boolean = true,
    val note: String = "",
    val imagePath: String? = null,
    val thumbPath: String? = null,
    val schedules: List<MedicineScheduleUi> = emptyList(),
    val nameError: Boolean = false,
    val specError: Boolean = false,
    val photoError: Boolean = false,
    /** 药盒识别中：驱动输入框的加载状态。 */
    val nameScanRunning: Boolean = false,
    /** 待用户确认的药名候选，非空时弹出选择对话框。 */
    val nameCandidates: List<String> = emptyList(),
    val nameScanFailed: Boolean = false
)

/**
 * 药品编辑：字段先放在内存态里，保存时一次性写库；计划列表由仓库 Flow 驱动，
 * 因此从计划编辑页返回后会自动刷新。
 */
class MedicineEditViewModel : ViewModel() {

    private val medicineRepository = ServiceLocator.medicineRepository
    private val scheduleRepository = ServiceLocator.scheduleRepository
    private val settingsRepository = ServiceLocator.settingsRepository
    private val alarmScheduler = ServiceLocator.alarmScheduler
    private val formatter = MedicineTextFormatter(ServiceLocator.appContext)
    private val context = ServiceLocator.appContext
    private val recognizer = MlKitRecognizer()

    private val _uiState = MutableStateFlow(MedicineEditUiState())
    val uiState: StateFlow<MedicineEditUiState> = _uiState.asStateFlow()

    private val _close = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val close: SharedFlow<Unit> = _close.asSharedFlow()

    private var loadedId: Long? = null
    /** 数据库里原有的图片路径，保存成功且更换后才删除，避免取消编辑时丢图。 */
    private var originalImagePath: String? = null
    private var originalThumbPath: String? = null
    private var stagedImagePath: String? = null
    private var stagedThumbPath: String? = null

    fun load(medicineId: Long) {
        if (loadedId == medicineId) return
        loadedId = medicineId
        if (medicineId == 0L) {
            originalImagePath = null
            originalThumbPath = null
            _uiState.value = MedicineEditUiState(loading = false, medicineId = 0L, isNew = true)
            return
        }
        viewModelScope.launch {
            val medicine = medicineRepository.get(medicineId)
            if (medicine == null) {
                _uiState.value = MedicineEditUiState(loading = false, medicineId = medicineId, isNew = false)
                return@launch
            }
            originalImagePath = medicine.imagePath
            originalThumbPath = medicine.thumbPath
            _uiState.value = medicine.toUiState()
            scheduleRepository.observeMedicineWithSchedules(medicineId).collect { schedules ->
                val stepsBySchedule = scheduleRepository.getTaperStepsFor(schedules.map { it.id })
                _uiState.update { state ->
                    state.copy(
                        schedules = schedules.map { schedule ->
                            MedicineScheduleUi(
                                id = schedule.id,
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
            }
        }
    }

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, nameError = false) }

    /** 切换剂型时联动：剂量单位切到该剂型的默认值，掰开设置只在片剂下保留用户选择。 */
    fun onFormChange(value: MedicineForm) = _uiState.update { state ->
        val unitOptions = formDoseUnitOptions(value)
        state.copy(
            form = value,
            doseUnit = if (state.doseUnit in unitOptions) {
                state.doseUnit
            } else {
                formDefaultDoseUnit(value)
            },
            splittable = if (isSplittableByUser(value)) {
                state.splittable
            } else {
                formFixedSplittable(value)
            },
            specError = false
        )
    }

    fun onSpecValueChange(value: String) =
        _uiState.update { it.copy(specValue = value, specError = false) }

    fun onSpecUnitChange(value: String) =
        _uiState.update { it.copy(specUnit = value, specError = false) }

    fun onDoseUnitChange(value: String) =
        _uiState.update { it.copy(doseUnit = value, specError = false) }

    fun onSplittableChange(value: Boolean) = _uiState.update { it.copy(splittable = value) }

    fun onPackSizeChange(value: String) = _uiState.update { it.copy(packSize = value) }

    fun onAutoDeductChange(value: Boolean) = _uiState.update { it.copy(autoDeductStock = value) }

    fun onNoteChange(value: String) = _uiState.update { it.copy(note = value) }

    /** 端侧识别是否可用；不可用时界面隐藏识别入口。 */
    fun isScanAvailable(): Boolean = recognizer.isAvailable(context)

    /** 识别药盒文字并提取候选药名，结果只进入待确认列表，不直接写入药名。 */
    fun scanMedicineName(uri: Uri) {
        if (_uiState.value.nameScanRunning) return
        _uiState.update {
            it.copy(nameScanRunning = true, nameScanFailed = false, nameCandidates = emptyList())
        }
        viewModelScope.launch {
            val lines = recognizer.recognizeText(context, uri)
            val candidates = MedicineNameExtractor.extract(lines)
            _uiState.update {
                it.copy(
                    nameScanRunning = false,
                    nameCandidates = candidates,
                    nameScanFailed = candidates.isEmpty()
                )
            }
        }
    }

    /** 用户确认后才把候选药名写入表单，仍需点击保存才会落库。 */
    fun applyNameCandidate(value: String) = _uiState.update {
        it.copy(name = value, nameError = false, nameCandidates = emptyList(), nameScanFailed = false)
    }

    fun dismissNameCandidates() = _uiState.update { it.copy(nameCandidates = emptyList()) }

    /** 选图后立即压缩落盘并更新预览，真正写库在保存时进行。 */
    fun attachPhoto(uri: Uri) {
        viewModelScope.launch {
            runCatching { ImageStore.save(context, uri) }
                .onSuccess { (imagePath, thumbPath) ->
                    // 同一药品在保存前多次选图时，清掉上一次尚未落库的临时文件
                    val abandonedImage = stagedImagePath
                    val abandonedThumb = stagedThumbPath
                    stagedImagePath = imagePath
                    stagedThumbPath = thumbPath
                    withContext(Dispatchers.IO) {
                        ImageStore.delete(abandonedImage, abandonedThumb)
                    }
                    _uiState.update {
                        it.copy(imagePath = imagePath, thumbPath = thumbPath, photoError = false)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(photoError = true) }
                }
        }
    }

    /** 只清空表单里的照片，文件在保存成功后才删除，避免取消编辑时误删原图。 */
    fun removePhoto() {
        val abandonedImage = stagedImagePath
        val abandonedThumb = stagedThumbPath
        stagedImagePath = null
        stagedThumbPath = null
        _uiState.update { it.copy(imagePath = null, thumbPath = null, photoError = false) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ImageStore.delete(abandonedImage, abandonedThumb)
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val name = state.name.trim()
        val specValue = state.specValue.trim().replace(',', '.').toDoubleOrNull()
        val specUnit = state.specUnit.trim()
        val doseUnit = state.doseUnit.trim()

        val nameError = name.isEmpty()
        val specError = specValue == null || specValue <= 0.0 ||
            specUnit.isEmpty() || doseUnit.isEmpty()
        if (nameError || specError) {
            _uiState.update { it.copy(nameError = nameError, specError = specError) }
            return
        }

        viewModelScope.launch {
            val existing = if (state.medicineId != 0L) medicineRepository.get(state.medicineId) else null
            // 新药品必须归属当前档案，避免落到默认档案造成串档
            val activeProfileId = settingsRepository.get().activeProfileId
            // 非片剂剂型没有掰开开关，按剂型固定，避免旧数据里残留 true
            val splittable = if (isSplittableByUser(state.form)) {
                state.splittable
            } else {
                formFixedSplittable(state.form)
            }
            val base = existing ?: Medicine(
                name = name,
                profileId = activeProfileId,
                form = state.form,
                specValue = specValue ?: 0.0,
                specUnit = specUnit,
                doseUnit = doseUnit,
                splittable = splittable,
                packSize = 0
            )
            medicineRepository.save(
                base.copy(
                    name = name,
                    form = state.form,
                    specValue = specValue ?: base.specValue,
                    specUnit = specUnit,
                    doseUnit = doseUnit,
                    splittable = splittable,
                    packSize = state.packSize.trim().toIntOrNull() ?: 0,
                    imagePath = state.imagePath,
                    thumbPath = state.thumbPath,
                    autoDeductStock = state.autoDeductStock,
                    note = state.note.trim().ifEmpty { null }
                )
            )
            alarmScheduler.rescheduleAll()
            // 图片更换或删除在保存成功后才清理旧文件
            if (originalImagePath != state.imagePath || originalThumbPath != state.thumbPath) {
                withContext(Dispatchers.IO) {
                    ImageStore.delete(originalImagePath, originalThumbPath)
                }
            }
            _close.emit(Unit)
        }
    }

    fun setScheduleEnabled(scheduleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            scheduleRepository.setEnabled(scheduleId, enabled)
            alarmScheduler.rescheduleAll()
        }
    }

    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            scheduleRepository.delete(scheduleId)
            alarmScheduler.rescheduleAll()
        }
    }

    /** 删除药品：外键级联删除计划与记录，图片文件一并清理后重排闹钟。 */
    fun deleteMedicine() {
        val id = _uiState.value.medicineId
        if (id == 0L) return
        viewModelScope.launch {
            medicineRepository.delete(id)
            alarmScheduler.rescheduleAll()
            _close.emit(Unit)
        }
    }

    private fun Medicine.toUiState(): MedicineEditUiState = MedicineEditUiState(
        loading = false,
        medicineId = id,
        isNew = false,
        name = name,
        form = form,
        specValue = specToText(specValue),
        specUnit = specUnit,
        doseUnit = doseUnit,
        splittable = splittable,
        packSize = if (packSize > 0) packSize.toString() else "",
        autoDeductStock = autoDeductStock,
        note = note.orEmpty(),
        imagePath = imagePath,
        thumbPath = thumbPath
    )
}