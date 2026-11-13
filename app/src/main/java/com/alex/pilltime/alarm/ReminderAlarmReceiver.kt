package com.alex.pilltime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 接收器共用的后台作用域（进程级）。 */
internal val alarmReceiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 提醒闹钟接收器：发通知，然后排下一组提醒。
 * 不在此写服药记录，记录只由用户操作触发。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val group = AlarmIntents.readGroup(intent)
        val appContext = context.applicationContext

        alarmReceiverScope.launch {
            try {
                if (group != null) {
                    try {
                        NotificationHelper(appContext).showReminder(group)
                    } catch (_: Exception) {
                        // 无通知权限等异常不应中断排程
                    }
                }
            } finally {
                try {
                    // 排除当前正在响的组，避免同一组被立即重排
                    AlarmScheduler(appContext).rescheduleAll(excludeGroupKey = group?.groupKey)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}