package com.medtimer.app.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtimer.app.ServiceLocator
import com.medtimer.app.alarm.NotificationHelper
import com.medtimer.app.data.entity.AppSettings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 权限自检状态：正常 / 未授权 / 已关闭 / 建议调整。 */
enum class PermissionState { OK, DENIED, OFF, RECOMMENDED }

/** 四类提醒相关权限的当前状态。 */
data class PermissionUiState(
    val exactAlarm: PermissionState = PermissionState.OK,
    val notifications: PermissionState = PermissionState.OK,
    val fullScreen: PermissionState = PermissionState.OK,
    val battery: PermissionState = PermissionState.OK
)

/** 操作反馈：测试提醒已排队、分钟数非法。 */
enum class SettingsNotice { NONE, TEST_SENT, INVALID_MINUTES }

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val permissions: PermissionUiState = PermissionUiState(),
    val pauseActive: Boolean = false,
    val notice: SettingsNotice = SettingsNotice.NONE
)

/** 用餐时间对应的四个设置字段。 */
enum class MealSlot { BREAKFAST, LUNCH, DINNER, BEDTIME }

/** 可编辑的分钟数设置字段。 */
enum class MinutesField { EARLY, LATE, MERGE }

/**
 * 设置页状态：设置来自 Room Flow，权限状态在每次回到前台时重新检测。
 * 影响排程的修改统一调用 [com.medtimer.app.alarm.AlarmScheduler.rescheduleAll]。
 */
class SettingsViewModel : ViewModel() {

    private val settingsRepository = ServiceLocator.settingsRepository
    private val alarmScheduler = ServiceLocator.alarmScheduler
    private val context = ServiceLocator.appContext

    private val noticeFlow = MutableStateFlow(SettingsNotice.NONE)
    private val permissionFlow = MutableStateFlow(readPermissions())
    // 仅用于触发暂停状态的重新判定
    private val tickFlow = MutableStateFlow(0L)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        permissionFlow,
        noticeFlow,
        tickFlow
    ) { settings, permissions, notice, _ ->
        SettingsUiState(
            settings = settings,
            permissions = permissions,
            pauseActive = isPauseActive(
                allPaused = settings.allPaused,
                pausedUntil = settings.pausedUntil,
                now = Instant.now()
            ),
            notice = notice
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    /** 页面回到前台时重新检测权限与暂停状态。 */
    fun refreshPermissions() {
        permissionFlow.value = readPermissions()
        tickFlow.value = System.nanoTime()
    }

    fun clearNotice() {
        noticeFlow.value = SettingsNotice.NONE
    }

    fun setMeal(slot: MealSlot, time: LocalTime) {
        updateSettings { settings ->
            when (slot) {
                MealSlot.BREAKFAST -> settings.copy(breakfast = time)
                MealSlot.LUNCH -> settings.copy(lunch = time)
                MealSlot.DINNER -> settings.copy(dinner = time)
                MealSlot.BEDTIME -> settings.copy(bedtime = time)
            }
        }
    }

    fun setQuietStart(time: LocalTime) {
        updateSettings { it.copy(quietStart = time) }
    }

    fun setQuietEnd(time: LocalTime) {
        updateSettings { it.copy(quietEnd = time) }
    }

    /** 分钟数超出 0..720 视为非法，用提示告知用户。 */
    fun setMinutes(field: MinutesField, minutes: Int) {
        if (minutes !in 0..MAX_MINUTES) {
            noticeFlow.value = SettingsNotice.INVALID_MINUTES
            return
        }
        updateSettings { settings ->
            when (field) {
                MinutesField.EARLY -> settings.copy(defaultEarlyMinutes = minutes)
                MinutesField.LATE -> settings.copy(defaultLateMinutes = minutes)
                MinutesField.MERGE -> settings.copy(mergeWindowMinutes = minutes)
            }
        }
    }

    /** 暂停 / 恢复全部提醒，随后重排闹钟。 */
    fun setPaused(option: PauseOption) {
        viewModelScope.launch {
            val until = pauseUntil(Instant.now(), ZoneId.systemDefault(), option)
            settingsRepository.setPaused(until)
            alarmScheduler.rescheduleAll()
            tickFlow.value = System.nanoTime()
        }
    }

    /** 安排 1 分钟后的测试提醒。 */
    fun sendTestReminder() {
        viewModelScope.launch {
            alarmScheduler.scheduleTestReminder()
            noticeFlow.value = SettingsNotice.TEST_SENT
        }
    }

    /** 切换应用语言：写设置、重建通知渠道并应用 LocaleList。 */
    fun setLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
            NotificationHelper(context).ensureChannel()
            val locales = localeTagOrNull(language)
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** 修改会影响排程的设置后统一重排。 */
    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            alarmScheduler.rescheduleAll()
        }
    }

    private fun readPermissions(): PermissionUiState = PermissionUiState(
        exactAlarm = if (alarmScheduler.canScheduleExactAlarms()) {
            PermissionState.OK
        } else {
            PermissionState.DENIED
        },
        notifications = if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            PermissionState.OK
        } else {
            PermissionState.OFF
        },
        fullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.canUseFullScreenIntent()) PermissionState.OK else PermissionState.DENIED
        } else {
            PermissionState.OK
        },
        battery = if (isIgnoringBatteryOptimizations()) {
            PermissionState.OK
        } else {
            PermissionState.RECOMMENDED
        }
    )

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private companion object {
        /** 提醒提前量、延迟量与合并窗口允许的上限（分钟）。 */
        const val MAX_MINUTES = 720
    }
}