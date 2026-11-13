package com.medtimer.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * 开机、改时间、改时区、应用升级、语言变化后全量重排。
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        alarmReceiverScope.launch {
            try {
                NotificationHelper(appContext).ensureChannel()
                AlarmScheduler(appContext).rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}