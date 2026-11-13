package com.alex.pilltime.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alex.pilltime.MainActivity
import com.alex.pilltime.R
import com.alex.pilltime.ServiceLocator
import com.alex.pilltime.domain.formatDoseQty
import com.alex.pilltime.ui.reminder.ReminderActivity

/**
 * 通知构建与渠道管理。渠道 id 固定，重复创建只更新名称/描述。
 */
class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** 每用相同 id 创建，语言变化后名称随之更新；系统不允许降级 importance。 */
    fun ensureChannel() {
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.notification_channel_reminder_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_reminder_desc)
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
        }

        val summaryChannel = NotificationChannel(
            CHANNEL_SUMMARY,
            context.getString(R.string.notification_channel_summary_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_summary_desc)
        }

        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemManager.createNotificationChannel(reminderChannel)
        systemManager.createNotificationChannel(summaryChannel)
    }

    /** 提醒通知：药名 + 剂量，最多列 3 条，超出显示"还有 N 项"。 */
    suspend fun showReminder(group: ReminderGroup) {
        ensureChannel()

        val medicineRepository = ServiceLocator.medicineRepository
        val visibleSlots = group.slots.take(MAX_LINES)
        val lines = visibleSlots.map { slot ->
            val medicine = medicineRepository.get(slot.medicineId)
            context.getString(
                R.string.reminder_item_format,
                medicine?.name ?: context.getString(R.string.reminder_unknown_medicine),
                formatDoseQty(slot.doseQtyX10),
                medicine?.doseUnit.orEmpty()
            )
        }
        val body = buildString {
            append(lines.joinToString("\n"))
            val remaining = group.slots.size - visibleSlots.size
            if (remaining > 0) {
                if (isNotEmpty()) append("\n")
                append(context.getString(R.string.notification_more_items, remaining))
            }
        }

        val notificationId = notificationIdFor(group.groupKey)
        val contentIntent = reminderActivityPendingIntent(group, notificationId)

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notification_title_reminder))
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .addAction(
                0,
                context.getString(R.string.notification_action_taken),
                actionPendingIntent(AlarmIntents.ACTION_TAKEN, group, notificationId, 0x10000000)
            )
            .addAction(
                0,
                context.getString(R.string.notification_action_snooze),
                actionPendingIntent(AlarmIntents.ACTION_SNOOZE, group, notificationId, 0x08000000)
            )
            .addAction(
                0,
                context.getString(R.string.notification_action_skip),
                actionPendingIntent(AlarmIntents.ACTION_SKIP, group, notificationId, 0x04000000)
            )

        notifySafely(notificationId, builder.build())
    }

    /** 前台补算汇总通知（独立渠道，默认优先级）。 */
    fun showMissedSummary(count: Int) {
        if (count <= 0) return
        ensureChannel()

        val contentIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PI_FLAGS
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notification_missed_summary_title))
            .setContentText(context.getString(R.string.notification_missed_summary_text, count))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        notifySafely(SUMMARY_NOTIFICATION_ID, notification)
    }

    /** 取消某组提醒通知。 */
    fun cancel(groupKey: String) {
        manager.cancel(notificationIdFor(groupKey))
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // 无通知权限时静默失败，闹钟链路本身仍然有效
        }
    }

    private fun reminderActivityPendingIntent(group: ReminderGroup, requestCode: Int): PendingIntent {
        val intent = AlarmIntents.putGroup(
            Intent(context, ReminderActivity::class.java),
            group
        )
        return PendingIntent.getActivity(context, requestCode, intent, PI_FLAGS)
    }

    private fun actionPendingIntent(
        action: String,
        group: ReminderGroup,
        notificationId: Int,
        requestCodeOffset: Int
    ): PendingIntent {
        val intent = AlarmIntents.putGroup(
            Intent(context, DoseActionReceiver::class.java).setAction(action),
            group
        ).putExtra(AlarmIntents.EXTRA_NOTIFICATION_ID, notificationId)
        return PendingIntent.getBroadcast(
            context,
            notificationId xor requestCodeOffset,
            intent,
            PI_FLAGS
        )
    }

    companion object {
        const val CHANNEL_REMINDER = "med_reminder"
        const val CHANNEL_SUMMARY = "med_summary"

        /** 通知正文最多列出的条目数。 */
        private const val MAX_LINES = 3

        private const val SUMMARY_NOTIFICATION_ID = 0x5A5A5A

        private val PI_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        /** 组通知 id 由稳定 groupKey 推导。 */
        fun notificationIdFor(groupKey: String): Int = groupKey.hashCode() and 0x3FFFFFFF
    }
}