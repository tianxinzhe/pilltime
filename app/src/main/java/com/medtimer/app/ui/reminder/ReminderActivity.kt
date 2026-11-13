package com.medtimer.app.ui.reminder

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.medtimer.app.R
import com.medtimer.app.ServiceLocator
import com.medtimer.app.alarm.AlarmIntents
import com.medtimer.app.alarm.AlarmScheduler
import com.medtimer.app.alarm.NotificationHelper
import com.medtimer.app.alarm.ReminderActions
import com.medtimer.app.alarm.ReminderGroup
import com.medtimer.app.domain.formatDoseQty
import com.medtimer.app.ui.text.formatDoseText
import com.medtimer.app.ui.theme.MedTimerTheme
import com.medtimer.app.util.ImageStore
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 提醒页：全屏沉浸、大字号，展示时间、药盒照片、药名与剂量，并提供服药操作。
 * 进入时自动 TTS 播报一次；所有操作写库幂等，库存不足只提示不拦截。
 */
class ReminderActivity : AppCompatActivity() {

    private val uiState = mutableStateOf(ReminderUiState())
    private var speaker: ReminderSpeaker? = null
    private var group: ReminderGroup? = null
    private var notificationId: Int = -1
    private var loadJob: Job? = null

    /** 防重复提交：点击后立即置位，协程结束才复位。 */
    private var actionInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        speaker = ReminderSpeaker(this)

        setContent {
            MedTimerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReminderScreen(
                        state = uiState.value,
                        onTaken = ::onTaken,
                        onSnooze = ::onSnooze,
                        onSkip = ::onSkip,
                        onUncertain = ::onUncertain,
                        onClose = ::finish
                    )
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        loadJob?.cancel()
        speaker?.release()
        speaker = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** 解析 extras 并刷新界面；数据缺失时展示兜底页，允许关闭。 */
    private fun handleIntent(intent: Intent?) {
        val parsed = AlarmIntents.readGroup(intent)
        if (parsed == null || parsed.slots.isEmpty()) {
            group = null
            loadJob?.cancel()
            uiState.value = ReminderUiState(loading = false, invalid = true)
            return
        }

        group = parsed
        notificationId = intent?.getIntExtra(AlarmIntents.EXTRA_NOTIFICATION_ID, -1)
            ?.takeIf { it >= 0 }
            ?: NotificationHelper.notificationIdFor(parsed.groupKey)

        actionInFlight = false
        uiState.value = ReminderUiState(loading = true)
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val loaded = try {
                loadUi(parsed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (loaded == null) {
                uiState.value = ReminderUiState(loading = false, invalid = true)
                return@launch
            }
            uiState.value = loaded
            speakReminder(loaded)
        }
    }

    private suspend fun loadUi(reminderGroup: ReminderGroup): ReminderUiState {
        val medicines = reminderGroup.slots.map { slot ->
            ServiceLocator.medicineRepository.get(slot.medicineId)
        }

        val lines = ReminderContent.lines(
            slots = reminderGroup.slots.mapIndexed { index, slot ->
                ReminderSlotText(
                    medicineName = medicines[index]?.name,
                    doseQtyX10 = slot.doseQtyX10,
                    doseUnit = medicines[index]?.doseUnit.orEmpty()
                )
            },
            unknownName = getString(R.string.reminder_unknown_medicine),
            formatDose = { qtyX10, doseUnit ->
                if (doseUnit.isBlank()) {
                    formatDoseQty(qtyX10)
                } else {
                    formatDoseText(this, qtyX10, doseUnit)
                }
            }
        )

        // 库存为 0 只提示，不阻止确认
        val lowStock = medicines.any { medicine ->
            medicine != null && ServiceLocator.stockRepository.currentStock(medicine.id) <= 0
        }

        val timeFormat = DateFormat.getTimeFormat(this)
        return ReminderUiState(
            loading = false,
            timeText = ReminderContent.timeText(reminderGroup.triggerAt) { instant ->
                timeFormat.format(Date.from(instant))
            },
            items = lines.mapIndexed { index, line ->
                ReminderItemUi(line = line, imagePath = medicines[index]?.imagePath)
            },
            lowStock = lowStock
        )
    }

    private fun speakReminder(state: ReminderUiState) {
        if (state.items.isEmpty()) return
        val text = ReminderContent.spokenText(
            lines = state.items.map { it.line },
            itemFormat = { name, dose -> getString(R.string.tts_item_format, name, dose) },
            separator = getString(R.string.tts_item_separator),
            template = { items -> getString(R.string.tts_reminder_text, items) }
        )
        speaker?.speak(text, resources.configuration.locales[0])
    }

    private fun onTaken() = runRecordAction { current -> ReminderActions.markTaken(current) }

    private fun onSkip() = runRecordAction { current -> ReminderActions.markSkipped(current) }

    private fun onUncertain() =
        runRecordAction { current -> ReminderActions.markUncertain(current) }

    /** 已服用/跳过/记不清：写库 → 取消通知 → 重排下一组提醒 → 关闭页面。 */
    private fun runRecordAction(action: suspend (ReminderGroup) -> Unit) {
        val current = group ?: return
        if (actionInFlight) return
        actionInFlight = true
        uiState.value = uiState.value.copy(submitting = true)

        lifecycleScope.launch {
            try {
                action(current)
                cancelNotification()
                AlarmScheduler(applicationContext).rescheduleAll()
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 写库失败时恢复按钮，允许重试
                actionInFlight = false
                uiState.value = uiState.value.copy(submitting = false)
            }
        }
    }

    /** 稍后提醒：不写记录；独立排程，调用 rescheduleAll 反而会取消它。 */
    private fun onSnooze() {
        val current = group ?: return
        if (actionInFlight) return
        actionInFlight = true
        uiState.value = uiState.value.copy(submitting = true)
        AlarmScheduler(applicationContext).scheduleSnooze(current, SNOOZE_MINUTES)
        cancelNotification()
        finish()
    }

    private fun cancelNotification() {
        val id = notificationId
        if (id >= 0) {
            NotificationManagerCompat.from(applicationContext).cancel(id)
        }
    }

    private companion object {
        const val SNOOZE_MINUTES = 10L
    }
}

private data class ReminderItemUi(val line: ReminderLine, val imagePath: String?)

private data class ReminderUiState(
    val loading: Boolean = true,
    val invalid: Boolean = false,
    val submitting: Boolean = false,
    val timeText: String = "",
    val items: List<ReminderItemUi> = emptyList(),
    val lowStock: Boolean = false
)

@Composable
private fun ReminderScreen(
    state: ReminderUiState,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onUncertain: () -> Unit,
    onClose: () -> Unit
) {
    when {
        state.loading -> LoadingContent()
        state.invalid -> InvalidContent(onClose = onClose)
        else -> ReminderContentColumn(
            state = state,
            onTaken = onTaken,
            onSnooze = onSnooze,
            onSkip = onSkip,
            onUncertain = onUncertain
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InvalidContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.reminder_invalid_data),
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text(text = stringResource(R.string.reminder_close), fontSize = 22.sp)
        }
    }
}

@Composable
private fun ReminderContentColumn(
    state: ReminderUiState,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onUncertain: () -> Unit
) {
    val compact = state.items.size > 1
    val context = LocalContext.current
    // 每个药品一个无障碍标签：药名 → 剂量，避免药盒图描述打断顺序
    val accessibilityTexts = remember(state.items) {
        ReminderContent.listTexts(state.items.map { it.line }) { name, dose ->
            context.getString(R.string.reminder_item_line, name, dose)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = state.timeText,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        for ((index, item) in state.items.withIndex()) {
            ReminderItemRow(
                item = item,
                compact = compact,
                accessibilityText = accessibilityTexts.getOrElse(index) { item.line.medicineName }
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.lowStock) {
            Spacer(Modifier.height(8.dp))
            LowStockNotice()
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onTaken,
            enabled = !state.submitting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = LocalContentColor.current
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = stringResource(R.string.reminder_screen_all_taken),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(16.dp))
        SecondaryAction(
            text = stringResource(R.string.notification_action_snooze),
            enabled = !state.submitting,
            onClick = onSnooze
        )
        Spacer(Modifier.height(12.dp))
        SecondaryAction(
            text = stringResource(R.string.notification_action_skip),
            enabled = !state.submitting,
            onClick = onSkip
        )
        Spacer(Modifier.height(12.dp))
        SecondaryAction(
            text = stringResource(R.string.today_action_uncertain),
            enabled = !state.submitting,
            onClick = onUncertain
        )
    }
}

@Composable
private fun ReminderItemRow(item: ReminderItemUi, compact: Boolean, accessibilityText: String) {
    val photoDescription = stringResource(R.string.cd_medicine_photo, item.line.medicineName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = accessibilityText }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MedicinePhoto(
            imagePath = item.imagePath,
            medicineName = item.line.medicineName,
            description = photoDescription,
            size = if (compact) 72.dp else 120.dp
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.line.medicineName,
                fontSize = if (compact) 28.sp else 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.line.doseText,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 有药盒照片显示圆角图，无图或加载失败显示首字圆形占位。 */
@Composable
private fun MedicinePhoto(
    imagePath: String?,
    medicineName: String,
    description: String,
    size: Dp
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
        value = if (imagePath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { ImageStore.loadImage(imagePath) }.getOrNull()
            }
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clearAndSetSemantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = medicineName.take(1),
                fontSize = 36.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** 库存不足只提示，不禁用确认按钮。 */
@Composable
private fun LowStockNotice() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.today_stock_empty),
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 次要操作：触碰区不小于 48dp，文字允许换行。 */
@Composable
private fun SecondaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}