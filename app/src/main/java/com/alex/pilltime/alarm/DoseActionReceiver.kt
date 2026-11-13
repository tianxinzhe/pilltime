package com.alex.pilltime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch

/**
 * 通知按钮与提醒页共用的操作入口：已服用 / 跳过 / 稍后 / 记不清。
 */
class DoseActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val group = AlarmIntents.readGroup(intent) ?: return
        val notificationId = intent.getIntExtra(AlarmIntents.EXTRA_NOTIFICATION_ID, -1)
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        alarmReceiverScope.launch {
            try {
                when (action) {
                    AlarmIntents.ACTION_TAKEN -> ReminderActions.markTaken(group)
                    AlarmIntents.ACTION_SKIP -> ReminderActions.markSkipped(group)
                    AlarmIntents.ACTION_UNCERTAIN -> ReminderActions.markUncertain(group)
                    AlarmIntents.ACTION_SNOOZE -> {
                        AlarmScheduler(appContext).scheduleSnooze(group, SNOOZE_MINUTES)
                        // 稍后提醒已独立排程，重排会把它取消，这里不调用 rescheduleAll
                        if (notificationId >= 0) {
                            NotificationManagerCompat.from(appContext).cancel(notificationId)
                        }
                        return@launch
                    }
                    else -> return@launch
                }

                if (notificationId >= 0) {
                    NotificationManagerCompat.from(appContext).cancel(notificationId)
                }
                AlarmScheduler(appContext).rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val SNOOZE_MINUTES = 10L
    }
}