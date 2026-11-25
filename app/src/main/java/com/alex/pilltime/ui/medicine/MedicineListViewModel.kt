package com.alex.pilltime.ui.medicine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.pilltime.R
import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.data.entity.Schedule
import com.alex.pilltime.domain.isLowStock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 药品列表的一行。 */
data class MedicineRowUi(
    val id: Long,
    val name: String,
    val thumbPath: String?,
    /** 基本参数：剂型 + 规格，如"片剂 · 500 mg"。 */
    val paramsText: String,
    val ruleText: String,
    val taperText: String?,
    val extraRuleCount: Int,
    val stockText: String?,
    val lowStock: Boolean
)

data class MedicineListUiState(
    val loading: Boolean = true,
    val rows: List<MedicineRowUi> = emptyList()
)

/**
 * 药品列表：药品、计划与库存一起映射成行。
 * 每行规则取该药第一条启用的计划，其余条数以"另有 N 条规则"折叠。
 */
class MedicineListViewModel : ViewModel() {

    private val medicineRepository = ServiceLocator.medicineRepository
    private val scheduleRepository = ServiceLocator.scheduleRepository
    private val stockRepository = ServiceLocator.stockRepository
    private val settingsRepository = ServiceLocator.settingsRepository
    private val alarmScheduler = ServiceLocator.alarmScheduler
    private val formatter = MedicineTextFormatter(ServiceLocator.appContext)
    private val context = ServiceLocator.appContext

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MedicineListUiState> = settingsRepository.settings
        .flatMapLatest { settings ->
            combine(
                medicineRepository.observeMedicines(settings.activeProfileId),
                scheduleRepository.observeSchedules(settings.activeProfileId)
            ) { medicines, schedules -> medicines to schedules }
                .map { (medicines, schedules) -> buildRows(medicines, schedules, settings) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MedicineListUiState()
        )

    /** 删除药品：数据库级联删除计划/记录，图片文件同步清理，随后重排闹钟。 */
    fun delete(medicineId: Long) {
        viewModelScope.launch {
            medicineRepository.delete(medicineId)
            alarmScheduler.rescheduleAll()
        }
    }

    private suspend fun buildRows(
        medicines: List<Medicine>,
        schedules: List<Schedule>,
        settings: AppSettings
    ): MedicineListUiState {
        val schedulesByMedicine = schedules.groupBy { it.medicineId }
        val rows = medicines.map { medicine ->
            val enabledSchedules = schedulesByMedicine[medicine.id].orEmpty()
                .filter { it.enabled }
                .sortedBy { it.id }
            val first = enabledSchedules.firstOrNull()
            val stockQty = stockRepository.currentStock(medicine.id)
            val days = stockRepository.remainingDays(medicine.id)
            val lowStock = stockQty <= 0 || isLowStock(days, settings.lowStockDaysThreshold)

            MedicineRowUi(
                id = medicine.id,
                name = medicine.name,
                thumbPath = medicine.thumbPath ?: medicine.imagePath,
                paramsText = context.getString(formLabelRes(medicine.form)) +
                    " · " + specToText(medicine.specValue) + " " + medicine.specUnit,
                ruleText = first?.let { formatter.ruleText(it, medicine.doseUnit) }
                    ?: context.getString(R.string.medicine_no_rule),
                taperText = first?.let { schedule ->
                    formatter.taperText(
                        schedule,
                        scheduleRepository.getTaperSteps(schedule.id),
                        medicine.doseUnit
                    )
                },
                extraRuleCount = (enabledSchedules.size - 1).coerceAtLeast(0),
                stockText = when {
                    // 没库存时提示补药；有库存时展示剩余量，能估算时附剩余天数
                    stockQty <= 0 -> context.getString(R.string.medicine_stock_low)
                    days.isFinite() -> context.getString(
                        R.string.medicine_stock_qty,
                        formatter.doseText(stockQty, medicine.doseUnit)
                    ) + " · " + context.getString(
                        R.string.medicine_stock_days, days.toInt()
                    )
                    else -> context.getString(
                        R.string.medicine_stock_qty,
                        formatter.doseText(stockQty, medicine.doseUnit)
                    )
                },
                lowStock = lowStock
            )
        }
        return MedicineListUiState(loading = false, rows = rows)
    }
}