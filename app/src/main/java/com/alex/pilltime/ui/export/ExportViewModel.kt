package com.alex.pilltime.ui.export

import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.pilltime.R
import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.export.ReportAssembly
import com.alex.pilltime.export.ReportRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 预览卡片所需的展示信息。 */
data class ExportPreviewUi(
    val file: File,
    val pageCount: Int,
    val sizeText: String,
    val rangeText: String,
    val recordCount: Int
)

data class ExportUiState(
    val rangeDays: Int = DEFAULT_RANGE_DAYS,
    val generating: Boolean = false,
    val preview: ExportPreviewUi? = null,
    val message: String? = null,
    val messageIsError: Boolean = false
)

private const val DEFAULT_RANGE_DAYS = 30
private val RANGE_OPTIONS = setOf(7, 30, 90)

/**
 * 导出页状态：范围选择 → 生成预览（缓存文件 + 页数 + 大小）→ 分享。
 * 组装与 PDF 写入都在 IO 线程，异常统一转为可见提示，不崩溃。
 */
class ExportViewModel : ViewModel() {

    private val context = ServiceLocator.appContext
    private val repository = ReportRepository(context)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun setRange(days: Int) {
        if (days !in RANGE_OPTIONS) return
        val current = _uiState.value
        if (current.generating || current.rangeDays == days) return
        _uiState.update {
            it.copy(
                rangeDays = days,
                preview = null,
                message = null,
                messageIsError = false
            )
        }
    }

    fun generate() {
        val current = _uiState.value
        if (current.generating) return
        _uiState.update {
            it.copy(
                generating = true,
                preview = null,
                message = null,
                messageIsError = false
            )
        }
        viewModelScope.launch {
            try {
                when (val result = repository.assemble(current.rangeDays)) {
                    ReportAssembly.Empty -> _uiState.update {
                        it.copy(
                            generating = false,
                            message = context.getString(R.string.export_empty_message),
                            messageIsError = false
                        )
                    }

                    is ReportAssembly.Ready -> {
                        val pdf = repository.export(result.data)
                        val sizeText = Formatter.formatFileSize(context, pdf.file.length())
                        _uiState.update {
                            it.copy(
                                generating = false,
                                preview = ExportPreviewUi(
                                    file = pdf.file,
                                    pageCount = pdf.pageCount,
                                    sizeText = sizeText,
                                    rangeText = "${result.data.rangeFrom} ~ ${result.data.rangeTo}",
                                    recordCount = result.data.counts.total
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        generating = false,
                        message = context.getString(R.string.export_error_generate),
                        messageIsError = true
                    )
                }
            }
        }
    }
}