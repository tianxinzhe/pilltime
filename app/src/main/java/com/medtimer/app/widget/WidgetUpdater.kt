package com.medtimer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.medtimer.app.MainActivity
import com.medtimer.app.R
import com.medtimer.app.ServiceLocator
import com.medtimer.app.domain.buildDaySlots
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 小组件数据刷新：读取当前档案的计划与今日记录，重新生成 RemoteViews。
 * 任何异常都吞掉，保留系统上一次展示的内容。
 */
object WidgetUpdater {

    suspend fun refreshAll(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, TodayWidgetProvider::class.java)
                )
                for (id in ids) {
                    updateInternal(context, manager, id)
                }
            } catch (_: Exception) {
                // 数据库或组件读取失败时保留上一次内容
            }
        }
    }

    suspend fun update(context: Context, appWidgetId: Int) {
        withContext(Dispatchers.IO) {
            try {
                updateInternal(context, AppWidgetManager.getInstance(context), appWidgetId)
            } catch (_: Exception) {
                // 刷新失败时保留上一次内容
            }
        }
    }

    private suspend fun updateInternal(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int
    ) {
        manager.updateAppWidget(appWidgetId, buildViews(context))
    }

    private suspend fun buildViews(context: Context): RemoteViews {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val date = now.atZone(zone).toLocalDate()

        val settings = ServiceLocator.settingsRepository.get()
        val profileId = settings.activeProfileId
        val medicines = ServiceLocator.medicineRepository.observeMedicines(profileId).first()
        val medicineIds = medicines.mapTo(mutableSetOf()) { it.id }
        val schedules = ServiceLocator.scheduleRepository.observeSchedules(profileId).first()
            .filter { it.medicineId in medicineIds }
        val taperSteps = schedules.associate { schedule ->
            schedule.id to ServiceLocator.scheduleRepository.getTaperSteps(schedule.id)
        }
        val logs = ServiceLocator.doseRepository.observeDay(date, zone).first()
            .filter { it.profileId == profileId }

        val slots = buildDaySlots(date, schedules, taperSteps, settings, zone)
        val paused = settings.allPaused &&
            (settings.pausedUntil == null || settings.pausedUntil.isAfter(now))
        val state = mapWidgetState(date, slots, logs, now, paused)
        val texts = buildWidgetTexts(state, AndroidWidgetTextProvider(context))

        return RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.widget_primary, texts.primary)
            setTextViewText(R.id.widget_secondary, texts.secondary)
            setViewVisibility(
                R.id.widget_secondary,
                if (texts.secondary.isBlank()) View.GONE else View.VISIBLE
            )
            setOnClickPendingIntent(R.id.widget_root, contentIntent(context))
        }
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** 时刻格式与首页保持一致。 */
    private class AndroidWidgetTextProvider(private val context: Context) : WidgetTextProvider {

        private val locale = context.resources.configuration.locales[0]
        private val timeFormatter = DateTimeFormatter.ofPattern(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
            locale
        )

        override fun remaining(count: Int): String =
            context.getString(R.string.widget_remaining, count)

        override fun next(time: LocalDateTime): String =
            context.getString(R.string.widget_next_time, timeFormatter.format(time))

        override fun done(): String = context.getString(R.string.widget_done_today)

        override fun noSchedule(): String = context.getString(R.string.widget_no_schedule)

        override fun paused(): String = context.getString(R.string.widget_paused)
    }
}