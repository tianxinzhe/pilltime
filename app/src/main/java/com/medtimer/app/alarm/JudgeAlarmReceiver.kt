package com.medtimer.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medtimer.app.ServiceLocator
import com.medtimer.app.domain.windowEnd
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * 判定闹钟接收器：窗口终点仍无有效记录则写 MISSED，不依赖 App 进程存活。
 */
class JudgeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val group = AlarmIntents.readGroup(intent)
        val appContext = context.applicationContext

        alarmReceiverScope.launch {
            try {
                if (group != null) {
                    val now = Instant.now()
                    val scheduleRepository = ServiceLocator.scheduleRepository
                    val doseRepository = ServiceLocator.doseRepository

                    for (slot in group.slots) {
                        if (slot.scheduleId <= 0L) continue
                        if (now.isBefore(windowEnd(slot.plannedAt, slot.lateMinutes))) continue
                        if (doseRepository.getActive(slot.scheduleId, slot.plannedAt) != null) continue
                        val schedule = scheduleRepository.getById(slot.scheduleId) ?: continue
                        doseRepository.markMissed(schedule, slot.plannedAt)
                    }
                }
            } finally {
                try {
                    AlarmScheduler(appContext).rescheduleAll()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}