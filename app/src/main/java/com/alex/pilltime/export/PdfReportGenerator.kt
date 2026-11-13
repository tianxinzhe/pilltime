package com.alex.pilltime.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.alex.pilltime.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.format.DateTimeFormatter

/** 生成结果：文件与页数，页数用于预览卡片展示。 */
data class PdfReport(val file: File, val pageCount: Int)

/**
 * A4（595 × 842 pt）PDF 生成器，纯自绘，不依赖第三方库。
 *
 * 版面顺序固定：页眉 → 当前用药 → 服药概况 → 按日概览 → 异常提示 → 页脚声明。
 * 分页时先测量段落高度，放不下就换页；表格行整体测量，不会被截断。
 */
class PdfReportGenerator(private val context: Context) {

    fun generate(data: ReportData, outputFile: File): File =
        generateWithInfo(data, outputFile).file

    fun generateWithInfo(data: ReportData, outputFile: File): PdfReport {
        val document = PdfDocument()
        try {
            val writer = PageWriter(document)
            writer.beginPage()
            drawHeader(writer, data)
            drawMedicines(writer, data)
            drawSummary(writer, data)
            drawDays(writer, data)
            drawAnomalies(writer, data)
            writer.finish()

            outputFile.parentFile?.mkdirs()
            try {
                FileOutputStream(outputFile).use { document.writeTo(it) }
            } catch (e: IOException) {
                outputFile.delete()
                throw e
            }
            return PdfReport(outputFile, writer.pageNumber)
        } finally {
            document.close()
        }
    }

    private fun drawHeader(writer: PageWriter, data: ReportData) {
        writer.drawParagraph(context.getString(R.string.export_pdf_title), titlePaint, 6f)
        writer.drawParagraph(
            context.getString(
                R.string.export_pdf_range,
                data.rangeFrom.format(ISO_DATE),
                data.rangeTo.format(ISO_DATE)
            ),
            bodyPaint,
            2f
        )
        writer.drawParagraph(
            context.getString(
                R.string.export_pdf_generated,
                data.generatedAt.format(ISO_DATE)
            ),
            bodyPaint,
            14f
        )
    }

    private fun drawMedicines(writer: PageWriter, data: ReportData) {
        writer.drawParagraph(
            context.getString(R.string.export_pdf_section_medicines),
            sectionPaint,
            6f
        )
        if (data.medicines.isEmpty()) {
            writer.drawParagraph(
                context.getString(R.string.export_pdf_medicines_none),
                bodyPaint,
                6f
            )
            writer.addGap(6f)
            return
        }

        for (medicine in data.medicines) {
            // 药名与规则首行保持在同一页，避免标题孤立在页尾
            val nameHeight = writer.heightOf(medicine.name, boldBodyPaint)
            val ruleFirstLine = writer.heightOf(medicine.ruleText, bodyPaint)
            writer.ensureSpace(nameHeight + minOf(ruleFirstLine, LINE_HEIGHT) + 4f)

            writer.drawParagraph(medicine.name, boldBodyPaint, 2f)
            writer.drawParagraph(medicine.ruleText, bodyPaint, 2f)
            medicine.remainingText?.let { writer.drawParagraph(it, bodyPaint, 2f) }
            writer.addGap(6f)
        }
        writer.addGap(4f)
    }

    private fun drawSummary(writer: PageWriter, data: ReportData) {
        val counts = data.counts
        writer.drawParagraph(
            context.getString(R.string.export_pdf_section_summary),
            sectionPaint,
            6f
        )
        writer.drawParagraph(
            context.getString(R.string.export_pdf_count_on_time, counts.onTime),
            bodyPaint,
            2f
        )
        writer.drawParagraph(
            context.getString(R.string.export_pdf_count_late, counts.late),
            bodyPaint,
            2f
        )
        writer.drawParagraph(
            context.getString(R.string.export_pdf_count_unconfirmed, counts.unconfirmed),
            bodyPaint,
            2f
        )
        writer.drawParagraph(
            context.getString(R.string.export_pdf_count_uncertain, counts.uncertain),
            bodyPaint,
            2f
        )
        writer.drawParagraph(
            context.getString(R.string.export_pdf_count_skipped, counts.skipped),
            bodyPaint,
            2f
        )
        writer.drawParagraph(
            context.getString(R.string.export_pdf_count_total, counts.total),
            bodyPaint,
            2f
        )
        writer.addGap(10f)
    }

    private fun drawDays(writer: PageWriter, data: ReportData) {
        writer.drawParagraph(
            context.getString(R.string.export_pdf_section_days),
            sectionPaint,
            6f
        )
        for (day in data.days) {
            val heading = day.date.format(ISO_DATE)
            val firstRowHeight = day.rows.firstOrNull()
                ?.let { writer.heightOf(rowText(it), bodyPaint) }
                ?: 0f
            writer.ensureSpace(
                writer.heightOf(heading, boldBodyPaint) + firstRowHeight + 4f
            )
            writer.drawParagraph(heading, boldBodyPaint, 2f)
            // 每条记录作为一个整体段落测量与绘制，超长时自动换行但不会被分页截断
            for (row in day.rows) {
                writer.drawParagraph(rowText(row), bodyPaint, 1f)
            }
            writer.addGap(6f)
        }
        writer.addGap(4f)
    }

    private fun drawAnomalies(writer: PageWriter, data: ReportData) {
        writer.drawParagraph(
            context.getString(R.string.export_pdf_section_anomalies),
            sectionPaint,
            6f
        )
        if (data.anomalies.isEmpty()) {
            writer.drawParagraph(
                context.getString(R.string.export_pdf_anomalies_none),
                bodyPaint,
                4f
            )
            return
        }
        for (anomaly in data.anomalies) {
            writer.drawParagraph(anomaly, bodyPaint, 2f)
        }
        writer.addGap(4f)
    }

    private fun rowText(row: ReportRow): String = context.getString(
        R.string.export_pdf_row,
        row.timeText,
        row.medicineName,
        row.doseText,
        row.statusText
    )

    private fun buildLayout(text: String, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

    /** 分页写入器：持有当前页，负责页脚与内容溢出换页。 */
    private inner class PageWriter(private val document: PdfDocument) {

        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f

        var pageNumber: Int = 0
            private set

        fun beginPage() {
            finishPage()
            pageNumber += 1
            val newPage = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            page = newPage
            canvas = newPage.canvas
            y = MARGIN
        }

        fun finish() {
            finishPage()
        }

        /** 当前页剩余空间不足时换页。 */
        fun ensureSpace(height: Float) {
            if (y + height > CONTENT_BOTTOM && y > MARGIN) beginPage()
        }

        fun addGap(gap: Float) {
            y += gap
        }

        fun heightOf(text: String, paint: TextPaint): Float =
            buildLayout(text, paint).height.toFloat()

        fun drawParagraph(text: String, paint: TextPaint, gapAfter: Float = 0f) {
            if (text.isEmpty()) return
            val layout = buildLayout(text, paint)
            val height = layout.height.toFloat()
            ensureSpace(height)
            val target = canvas ?: return
            target.save()
            target.translate(MARGIN, y)
            layout.draw(target)
            target.restore()
            y += height + gapAfter
        }

        private fun finishPage() {
            val target = canvas ?: return
            // 页脚声明每页重复
            val footer = buildLayout(
                context.getString(R.string.export_pdf_footer),
                footerPaint
            )
            target.save()
            target.translate(MARGIN, FOOTER_TOP)
            footer.draw(target)
            target.restore()

            page?.let { document.finishPage(it) }
            canvas = null
            page = null
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - 80
        const val CONTENT_BOTTOM = PAGE_HEIGHT - 80f
        const val FOOTER_TOP = PAGE_HEIGHT - 64f
        const val LINE_HEIGHT = 12f
        const val TEXT_COLOR = 0xFF111111.toInt()
        const val FOOTER_COLOR = 0xFF555555.toInt()

        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        val normalTypeface: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val boldTypeface: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = normalTypeface
        textSize = 10.5f
        color = TEXT_COLOR
    }

    private val boldBodyPaint = TextPaint(bodyPaint).apply {
        typeface = boldTypeface
        textSize = 10.5f
    }

    private val sectionPaint = TextPaint(bodyPaint).apply {
        typeface = boldTypeface
        textSize = 11f
    }

    private val titlePaint = TextPaint(bodyPaint).apply {
        typeface = boldTypeface
        textSize = 16f
    }

    private val footerPaint = TextPaint(bodyPaint).apply {
        textSize = 10f
        color = FOOTER_COLOR
    }
}