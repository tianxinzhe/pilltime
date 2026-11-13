package com.alex.pilltime.export

import android.content.Context
import com.alex.pilltime.R
import com.alex.pilltime.ServiceLocator
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 报告仓库：读取当前档案的药品、计划、记录与库存，组装 ReportData 并输出 PDF。
 * 所有数据库读取与文件写入都在 IO 线程完成。
 */
class ReportRepository(private val context: Context) {

    private val texts = AndroidReportTextProvider(context)
    private val generator = PdfReportGenerator(context)

    /** 组装所选范围的报告；范围为空时返回 Empty，不产生文件。 */
    suspend fun assemble(
        rangeDays: Int,
        today: LocalDate = LocalDate.now()
    ): ReportAssembly = withContext(Dispatchers.IO) {
        val days = if (rangeDays in RANGE_OPTIONS) rangeDays else DEFAULT_RANGE_DAYS
        val zone = ZoneId.systemDefault()
        val settings = ServiceLocator.settingsRepository.get()
        val medicines = ServiceLocator.medicineRepository
            .observeMedicines(settings.activeProfileId)
            .first()
        val schedules = ServiceLocator.scheduleRepository
            .observeSchedules(settings.activeProfileId)
            .first()
        val from = today.minusDays(days.toLong() - 1)
        val logs = ServiceLocator.doseRepository.observeRange(from, today, zone).first()
            .filter { it.profileId == settings.activeProfileId }
        val taperSteps = ServiceLocator.scheduleRepository.getAllTaperSteps()
        val stockX10ByMedicine = medicines.associate { medicine ->
            medicine.id to ServiceLocator.stockRepository.currentStock(medicine.id)
        }

        assembleReport(
            rangeDays = days,
            today = today,
            zone = zone,
            settings = settings,
            medicines = medicines,
            schedules = schedules,
            taperSteps = taperSteps,
            logs = logs,
            stockX10ByMedicine = stockX10ByMedicine,
            texts = texts,
            now = Instant.now()
        )
    }

    /** 输出到 cache/reports，写入前清空旧文件，避免缓存膨胀。 */
    suspend fun export(data: ReportData): PdfReport = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, REPORTS_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Unable to create report directory")
        }
        dir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
        val prefix = context.getString(R.string.export_file_prefix)
        val output = File(dir, reportFileName(prefix, data.generatedAt))
        generator.generateWithInfo(data, output)
    }

    private companion object {
        val RANGE_OPTIONS = setOf(7, 30, 90)
        const val DEFAULT_RANGE_DAYS = 30
        const val REPORTS_DIR = "reports"
    }
}