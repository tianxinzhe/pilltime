package com.alex.pilltime.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.pilltime.R
import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.data.entity.AppSettings
import com.alex.pilltime.data.entity.Medicine
import com.alex.pilltime.data.entity.StockChange
import com.alex.pilltime.domain.isLowStock
import com.alex.pilltime.domain.model.StockReason
import com.alex.pilltime.ui.text.formatDoseText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 操作反馈：库存无变化或数量非法时由界面显示对应文案。 */
enum class StockNotice { NONE, NO_CHANGE, INVALID_QTY }

data class StockUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val medicineName: String = "",
    val doseUnit: String = "",
    val stockText: String = "",
    val daysText: String = "",
    val outOfStock: Boolean = false,
    val lowStock: Boolean = false,
    val changes: List<StockChangeUi> = emptyList(),
    val notice: StockNotice = StockNotice.NONE
)

data class StockChangeUi(
    val id: Long,
    val timeText: String,
    val reasonText: String,
    val deltaText: String,
    val note: String?
)

private val STOCK_QTY_PATTERN = Regex("^(\\d{1,7})(?:\\.(\\d))?$")

/**
 * 库存数量文本转 ×10 整数：与 parseDoseX10 同样的整数解析（最多一位小数），
 * 额外允许 0，因为盘点后的实际库存可能为 0。非法输入返回 null。
 */
fun parseQuantityX10(input: String): Int? {
    val text = input.trim().replace(',', '.')
    val match = STOCK_QTY_PATTERN.matchEntire(text) ?: return null
    val whole = match.groupValues[1].toLongOrNull() ?: return null
    val tenth = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
    val total = whole * 10 + tenth
    if (total < 0 || total > Int.MAX_VALUE) return null
    return total.toInt()
}

/**
 * 库存页：当前库存、预计可用天数与最近流水。
 * 库存值始终来自流水求和，页面不单独保存库存字段。
 */
class StockViewModel : ViewModel() {

    private val medicineRepository = ServiceLocator.medicineRepository
    private val stockRepository = ServiceLocator.stockRepository
    private val settingsRepository = ServiceLocator.settingsRepository
    private val context = ServiceLocator.appContext

    private val locale = context.resources.configuration.locales[0]
    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)

    private val medicineIdFlow = MutableStateFlow(NOT_LOADED)
    private val noticeFlow = MutableStateFlow(StockNotice.NONE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val contentFlow: Flow<StockUiState> = medicineIdFlow.flatMapLatest { medicineId ->
        when {
            medicineId == NOT_LOADED -> flowOf(StockUiState())
            medicineId <= 0L -> flowOf(StockUiState(loading = false, missing = true))
            else -> combine(
                medicineRepository.observeMedicine(medicineId),
                stockRepository.observeStock(medicineId),
                stockRepository.observeRecent(medicineId, RECENT_LIMIT),
                settingsRepository.settings
            ) { medicine, stockQty, changes, settings ->
                Snapshot(medicine, stockQty, changes, settings)
            }.map { snapshot ->
                withContext(Dispatchers.IO) { buildState(medicineId, snapshot) }
            }
        }
    }

    val uiState: StateFlow<StockUiState> = combine(contentFlow, noticeFlow) { content, notice ->
        content.copy(notice = notice)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StockUiState()
    )

    fun load(medicineId: Long) {
        if (medicineIdFlow.value != medicineId) {
            medicineIdFlow.value = medicineId
        }
    }

    /** 入库：按最小单位 ×10 写入 PURCHASE 流水。 */
    fun purchase(input: String) {
        val qtyX10 = parseQuantityX10(input)
        if (qtyX10 == null || qtyX10 <= 0) {
            noticeFlow.value = StockNotice.INVALID_QTY
            return
        }
        viewModelScope.launch {
            stockRepository.addPurchase(medicineIdFlow.value, qtyX10)
            noticeFlow.value = StockNotice.NONE
        }
    }

    /** 盘点：写入 ADJUST = 实际 − 当前；差值为 0 时不写流水并提示库存无变化。 */
    fun adjust(input: String) {
        val actualX10 = parseQuantityX10(input)
        if (actualX10 == null) {
            noticeFlow.value = StockNotice.INVALID_QTY
            return
        }
        viewModelScope.launch {
            val inserted = stockRepository.adjustTo(medicineIdFlow.value, actualX10)
            noticeFlow.value = if (inserted == 0L) StockNotice.NO_CHANGE else StockNotice.NONE
        }
    }

    fun clearNotice() {
        noticeFlow.value = StockNotice.NONE
    }

    private suspend fun buildState(medicineId: Long, snapshot: Snapshot): StockUiState {
        val medicine = snapshot.medicine
            ?: return StockUiState(loading = false, missing = true)

        val days = stockRepository.remainingDays(medicineId)
        val outOfStock = snapshot.stockQty <= 0
        val low = outOfStock ||
            isLowStock(days, snapshot.settings.lowStockDaysThreshold)

        return StockUiState(
            loading = false,
            medicineName = medicine.name,
            doseUnit = medicine.doseUnit,
            stockText = context.getString(
                R.string.stock_remaining,
                formatDoseText(context, snapshot.stockQty, medicine.doseUnit)
            ),
            daysText = if (days.isFinite()) {
                context.getString(R.string.medicine_stock_days, days.toInt())
            } else {
                context.getString(R.string.stock_days_long_term)
            },
            outOfStock = outOfStock,
            lowStock = low,
            changes = snapshot.changes.map { it.toUi(medicine.doseUnit) }
        )
    }

    private fun StockChange.toUi(doseUnit: String): StockChangeUi {
        val amount = formatDoseText(context, abs(deltaQtyX10), doseUnit)
        return StockChangeUi(
            id = id,
            timeText = dateTimeFormatter.format(
                at.atZone(ZoneId.systemDefault()).toLocalDateTime()
            ),
            reasonText = context.getString(reasonResId(reason)),
            deltaText = if (deltaQtyX10 >= 0) {
                context.getString(R.string.stock_delta_plus, amount)
            } else {
                context.getString(R.string.stock_delta_minus, amount)
            },
            note = note
        )
    }

    private fun reasonResId(reason: StockReason): Int = when (reason) {
        StockReason.PURCHASE -> R.string.stock_reason_purchase
        StockReason.TAKEN -> R.string.stock_reason_taken
        StockReason.ADJUST -> R.string.stock_reason_adjust
        StockReason.LOST -> R.string.stock_reason_lost
        StockReason.UNDO -> R.string.stock_reason_undo
    }

    private data class Snapshot(
        val medicine: Medicine?,
        val stockQty: Int,
        val changes: List<StockChange>,
        val settings: AppSettings
    )

    private companion object {
        const val NOT_LOADED = -1L
        const val RECENT_LIMIT = 20
    }
}