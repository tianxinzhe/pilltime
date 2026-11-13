package com.medtimer.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.medtimer.app.MainActivity
import com.medtimer.app.ServiceLocator
import com.medtimer.app.domain.SlotGroup
import com.medtimer.app.domain.buildDaySlots
import com.medtimer.app.widget.WidgetUpdater
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * 提醒排程：只排"下一组"提醒 + 对应判定闹钟，触发后再排下一组，避免堆积。
 * PendingIntent 独立于进程存活，无需常驻前台服务。
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 全量重排：取消旧闹钟后扫描今天起 [PLAN_DAYS] 天，只排最早的一组。
     * [excludeGroupKey] 用于跳过正在响的组，避免同一组被立即重排。
     */
    suspend fun rescheduleAll(excludeGroupKey: String? = null) {
        cancelAll()
        prefs.edit().putBoolean(KEY_EXACT_DEGRADED, !canScheduleExactAlarms()).apply()

        val now = Instant.now()
        val settings = ServiceLocator.settingsRepository.get()
        if (settings.allPaused && (settings.pausedUntil == null || settings.pausedUntil.isAfter(now))) {
            WidgetUpdater.refreshAll(context)
            return
        }

        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        // 只排当前档案的定时计划，避免多档案互相覆盖提醒
        val schedules = ServiceLocator.scheduleRepository
            .getTimedEnabledForProfile(settings.activeProfileId)
        if (schedules.isEmpty()) {
            WidgetUpdater.refreshAll(context)
            return
        }

        val taperSteps = ServiceLocator.scheduleRepository.getTaperStepsFor(schedules.map { it.id })
        var slots = buildList {
            for (offset in 0 until PLAN_DAYS) {
                addAll(
                    buildDaySlots(
                        date = today.plusDays(offset.toLong()),
                        schedules = schedules,
                        taperStepsBySchedule = taperSteps,
                        settings = settings,
                        zone = zone
                    )
                )
            }
        }

        val logs = ServiceLocator.doseRepository
            .observeRange(today, today.plusDays((PLAN_DAYS - 1).toLong()), zone)
            .first()

        var group = AlarmPlanner.pickNextGroup(slots, logs, now, settings.mergeWindowMinutes)
        val current = group
        if (excludeGroupKey != null && current?.groupKey == excludeGroupKey) {
            // 当前组正在响：cancelAll 后仍需保留其判定闹钟，到点仍无记录要写 MISSED
            val judgeAtMillis = current.latestWindowEnd.toEpochMilli()
            if (judgeAtMillis > now.toEpochMilli()) {
                val code = requestCodeFor(current.groupKey)
                cancelCode(code)
                setJudgeAlarm(code, current.toReminderGroup(), judgeAtMillis)
                rememberCode(code)
            }

            val excluded = current.slots.map { it.scheduleId to it.plannedAt }.toSet()
            slots = slots.filterNot { (it.scheduleId to it.plannedAt) in excluded }
            group = AlarmPlanner.pickNextGroup(slots, logs, now, settings.mergeWindowMinutes)
        }

        group?.let { scheduleGroup(it, now) }
        WidgetUpdater.refreshAll(context)
    }

    /** 取消所有已排闹钟（提醒 + 判定），并清空记录。 */
    fun cancelAll() {
        val codes = prefs.getString(KEY_REQUEST_CODES, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()
        for (code in codes) {
            cancelCode(code)
        }
        prefs.edit().remove(KEY_REQUEST_CODES).apply()
    }

    /** 测试提醒：延迟 [delayMillis] 后响一次，用于设置页自检。 */
    suspend fun scheduleTestReminder(delayMillis: Long = 60_000) {
        val now = Instant.now()
        val plannedAt = now.plusMillis(delayMillis)
        val group = ReminderGroup(
            groupKey = "test@${now.toEpochMilli()}",
            slots = listOf(
                GroupSlot(
                    scheduleId = 0L,
                    medicineId = 0L,
                    plannedAt = plannedAt,
                    doseQtyX10 = 10,
                    earlyMinutes = 0,
                    lateMinutes = 0
                )
            )
        )
        scheduleAlarm(
            code = requestCodeFor(group.groupKey),
            group = group,
            triggerAtMillis = plannedAt.toEpochMilli(),
            judgeAtMillis = null
        )
    }

    /** 稍后提醒：不写记录，用独立 requestCode 排一次性闹钟。 */
    fun scheduleSnooze(group: ReminderGroup, delayMinutes: Long = 10) {
        val triggerAtMillis = Instant.now()
            .plusSeconds(delayMinutes * 60L)
            .toEpochMilli()
        scheduleAlarm(
            code = snoozeRequestCodeFor(group.groupKey),
            group = group,
            triggerAtMillis = triggerAtMillis,
            judgeAtMillis = null
        )
    }

    /** API 31+ 查系统精确闹钟权限；26~30 默认可用。 */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 精确闹钟是否已降级（供首页红条提示）。 */
    fun isExactAlarmDegraded(): Boolean = prefs.getBoolean(KEY_EXACT_DEGRADED, false)

    private fun scheduleGroup(group: SlotGroup, now: Instant) {
        val reminderGroup = group.toReminderGroup()
        // 触发时刻已过但窗口未过：1 秒后立即触发
        val triggerAtMillis = maxOf(
            group.triggerAt.toEpochMilli(),
            now.toEpochMilli() + IMMEDIATE_DELAY_MILLIS
        )
        scheduleAlarm(
            code = requestCodeFor(group.groupKey),
            group = reminderGroup,
            triggerAtMillis = triggerAtMillis,
            judgeAtMillis = maxOf(group.latestWindowEnd.toEpochMilli(), triggerAtMillis)
        )
    }

    /**
     * 取消同 requestCode 的旧提醒/判定闹钟后重新排程。
     * 精确闹钟不可用时降级为 setAndAllowWhileIdle 并记录降级标记。
     */
    private fun scheduleAlarm(
        code: Int,
        group: ReminderGroup,
        triggerAtMillis: Long,
        judgeAtMillis: Long?
    ) {
        cancelCode(code)

        val reminderIntent = AlarmIntents.putGroup(
            Intent(context, ReminderAlarmReceiver::class.java),
            group
        )
        val reminderPending = PendingIntent.getBroadcast(context, code, reminderIntent, PI_FLAGS)

        if (canScheduleExactAlarms()) {
            val showIntent = PendingIntent.getActivity(
                context,
                code,
                Intent(context, MainActivity::class.java),
                PI_FLAGS
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                reminderPending
            )
            if (judgeAtMillis != null) {
                setJudgeAlarm(code, group, judgeAtMillis)
            }
        } else {
            prefs.edit().putBoolean(KEY_EXACT_DEGRADED, true).apply()
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                reminderPending
            )
            if (judgeAtMillis != null) {
                setJudgeAlarm(code, group, judgeAtMillis)
            }
        }

        rememberCode(code)
    }

    private fun setJudgeAlarm(code: Int, group: ReminderGroup, judgeAtMillis: Long) {
        val judgePending = judgePendingIntent(code, group)
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                judgeAtMillis,
                judgePending
            )
        } else {
            prefs.edit().putBoolean(KEY_EXACT_DEGRADED, true).apply()
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                judgeAtMillis,
                judgePending
            )
        }
    }

    private fun judgePendingIntent(code: Int, group: ReminderGroup): PendingIntent {
        val intent = AlarmIntents.putGroup(
            Intent(context, JudgeAlarmReceiver::class.java),
            group
        )
        return PendingIntent.getBroadcast(context, judgeRequestCodeFor(code), intent, PI_FLAGS)
    }

    /** 取消同一 requestCode 对应的提醒与判定 PendingIntent。 */
    private fun cancelCode(code: Int) {
        val reminder = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ReminderAlarmReceiver::class.java),
            PI_FLAGS or PendingIntent.FLAG_NO_CREATE
        )
        reminder?.cancel()

        val judge = PendingIntent.getBroadcast(
            context,
            judgeRequestCodeFor(code),
            Intent(context, JudgeAlarmReceiver::class.java),
            PI_FLAGS or PendingIntent.FLAG_NO_CREATE
        )
        judge?.cancel()
    }

    private fun rememberCode(code: Int) {
        val existing = prefs.getString(KEY_REQUEST_CODES, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()
        if (code in existing) return
        val updated = (existing + code).joinToString(",")
        prefs.edit().putString(KEY_REQUEST_CODES, updated).apply()
    }

    companion object {
        private const val PREFS_NAME = "alarm_state"
        private const val KEY_REQUEST_CODES = "scheduled_request_codes"
        private const val KEY_EXACT_DEGRADED = "exact_alarm_degraded"

        /** 扫描未来天数（含今天）。 */
        private const val PLAN_DAYS = 7

        /** 触发时刻已过但窗口未过时的立即触发延迟。 */
        private const val IMMEDIATE_DELAY_MILLIS = 1_000L

        private const val PI_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        /** 提醒 requestCode：groupKey 稳定哈希。 */
        fun requestCodeFor(groupKey: String): Int = groupKey.hashCode() and 0x3FFFFFFF

        /** 判定 requestCode：提醒 requestCode 的最高位取反。 */
        fun judgeRequestCodeFor(reminderCode: Int): Int = reminderCode xor 0x40000000

        /** 稍后提醒 requestCode：与常规提醒区分。 */
        fun snoozeRequestCodeFor(groupKey: String): Int =
            requestCodeFor(groupKey) xor 0x20000000
    }
}