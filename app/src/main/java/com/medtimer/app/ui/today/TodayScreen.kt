package com.medtimer.app.ui.today

import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medtimer.app.R
import com.medtimer.app.domain.model.DoseStatus
import com.medtimer.app.ui.text.formatDoseText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 首页文案：日期与时刻按当前 locale 格式化，其余全部来自 strings.xml。 */
class AndroidTodayTextProvider(private val context: Context) : TodayTextProvider {

    private val locale = context.resources.configuration.locales[0]
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        locale
    )

    override fun date(date: LocalDate): String = dateFormatter.format(date)

    override fun time(time: LocalDateTime): String = timeFormatter.format(time)

    override fun dose(qtyX10: Int, doseUnit: String): String =
        formatDoseText(context, qtyX10, doseUnit)

    override fun status(status: DoseStatus): String = context.getString(statusResId(status))

    override fun notDueNext(nextDate: LocalDate): String =
        context.getString(R.string.today_not_due, dateFormatter.format(nextDate))

    override fun stockDays(days: Int): String =
        context.getString(R.string.today_stock_days, days)

    override fun stockEmpty(): String = context.getString(R.string.today_stock_empty)
}

private fun statusResId(status: DoseStatus): Int = when (status) {
    DoseStatus.DUE -> R.string.today_status_due
    DoseStatus.MISSED -> R.string.today_status_missed
    DoseStatus.TAKEN -> R.string.today_status_taken
    DoseStatus.LATE -> R.string.today_status_late
    DoseStatus.SKIPPED -> R.string.today_status_skipped
    DoseStatus.UNCERTAIN -> R.string.today_status_uncertain
    DoseStatus.PENDING -> R.string.today_status_pending
}

/** 首页入口：与 MainActivity 共用同一个 ViewModel 实例（Activity 作用域）。 */
@Composable
fun TodayRoute(
    onNavigateToMedicines: () -> Unit,
    viewModel: TodayViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    TodayScreen(
        state = state,
        onTakenAll = { viewModel.markTakenAll() },
        onMarkTaken = viewModel::markTaken,
        onSkip = viewModel::skip,
        onUncertain = viewModel::uncertain,
        onUndo = viewModel::undo,
        onPrnTaken = viewModel::markPrnTaken,
        onResume = viewModel::resumeReminders,
        onNavigateToMedicines = onNavigateToMedicines
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onTakenAll: () -> Unit,
    onMarkTaken: (TodaySlotUi) -> Unit,
    onSkip: (TodaySlotUi) -> Unit,
    onUncertain: (TodaySlotUi) -> Unit,
    onUndo: (Long) -> Unit,
    onPrnTaken: (PrnUi) -> Unit,
    onResume: () -> Unit,
    onNavigateToMedicines: () -> Unit
) {
    var sheetSlot by remember { mutableStateOf<TodaySlotUi?>(null) }
    var notDueExpanded by remember { mutableStateOf(true) }

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") { TodayHeader(state) }

        if (state.exactAlarmDegraded) {
            item(key = "banner_exact_alarm") {
                AlertBanner(text = stringResource(R.string.today_alert_exact_alarm))
            }
        }
        if (state.notificationsDisabled) {
            item(key = "banner_notifications") {
                AlertBanner(text = stringResource(R.string.today_alert_notifications))
            }
        }
        if (state.paused) {
            item(key = "banner_paused") { PausedBanner(onResume = onResume) }
        }
        if (state.missedSummary > 0) {
            item(key = "banner_missed") {
                AlertBanner(
                    text = stringResource(R.string.today_missed_summary, state.missedSummary),
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "taken_all") {
            Button(
                onClick = onTakenAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    text = stringResource(R.string.today_taken_all),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (!state.hasAnyPlan) {
            item(key = "empty") { EmptyPlan(onNavigateToMedicines = onNavigateToMedicines) }
        } else {
            items(state.groups, key = { it.groupKey }) { group ->
                GroupCard(group = group, onSlotClick = { sheetSlot = it })
            }
        }

        if (state.prn.isNotEmpty()) {
            item(key = "prn") {
                PrnSection(items = state.prn, onTake = onPrnTaken)
            }
        }

        if (state.notDueToday.isNotEmpty()) {
            item(key = "not_due") {
                NotDueSection(
                    items = state.notDueToday,
                    expanded = notDueExpanded,
                    onToggle = { notDueExpanded = !notDueExpanded }
                )
            }
        }
    }

    sheetSlot?.let { slot ->
        SlotActionsSheet(
            slot = slot,
            onDismiss = { sheetSlot = null },
            onMarkTaken = { onMarkTaken(it); sheetSlot = null },
            onSkip = { onSkip(it); sheetSlot = null },
            onUncertain = { onUncertain(it); sheetSlot = null },
            onUndo = { onUndo(it); sheetSlot = null }
        )
    }
}

@Composable
private fun TodayHeader(state: TodayUiState) {
    Column {
        Text(
            text = state.dateText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.today_progress, state.takenCount, state.totalCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlertBanner(
    text: String,
    container: Color = MaterialTheme.colorScheme.errorContainer,
    content: Color = MaterialTheme.colorScheme.onErrorContainer,
    action: (@Composable () -> Unit)? = null
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            action?.invoke()
        }
    }
}

@Composable
private fun PausedBanner(onResume: () -> Unit) {
    AlertBanner(
        text = stringResource(R.string.today_alert_paused),
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        TextButton(onClick = onResume) {
            Text(stringResource(R.string.today_action_resume))
        }
    }
}

@Composable
private fun GroupCard(group: TodayGroupUi, onSlotClick: (TodaySlotUi) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
        ) {
            Text(
                text = group.timeText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            for (slot in group.slots) {
                SlotRow(slot = slot, onClick = { onSlotClick(slot) })
            }
        }
    }
}

@Composable
private fun SlotRow(slot: TodaySlotUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MedicineThumb(slot = slot)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = slot.medicineName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = slot.doseText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            slot.stockText?.let { stock ->
                Text(
                    text = stock,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (slot.lowStock) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(slot = slot)
    }
}

/** 有缩略图显示 48dp 圆角图，否则用首字圆形占位。 */
@Composable
private fun MedicineThumb(slot: TodaySlotUi) {
    val path = slot.thumbPath ?: slot.imagePath
    val bitmap = remember(path) { path?.let { BitmapFactory.decodeFile(it) } }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_medicine_photo, slot.medicineName),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = slot.medicineName.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun StatusChip(slot: TodaySlotUi) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (slot.status) {
        DoseStatus.DUE -> scheme.primary to scheme.onPrimary
        DoseStatus.MISSED -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text = slot.statusText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PrnSection(items: List<PrnUi>, onTake: (PrnUi) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(R.string.today_prn_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            for (item in items) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.medicineName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = item.doseText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 每日上限只做提示，按钮始终可点，不拦截确认
                        val max = item.maxPerDay
                        if (max != null && item.takenCount >= max) {
                            Text(
                                text = stringResource(
                                    R.string.today_prn_taken_limit,
                                    item.takenCount,
                                    max
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onTake(item) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.today_prn_take_now))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotDueSection(items: List<NotDueUi>, expanded: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.today_not_due_section),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = stringResource(R.string.cd_not_due_toggle)
                )
            }
            if (expanded) {
                for (item in items) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = item.medicineName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = item.nextDateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlan(onNavigateToMedicines: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.today_empty_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNavigateToMedicines,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(text = stringResource(R.string.today_empty_action))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotActionsSheet(
    slot: TodaySlotUi,
    onDismiss: () -> Unit,
    onMarkTaken: (TodaySlotUi) -> Unit,
    onSkip: (TodaySlotUi) -> Unit,
    onUncertain: (TodaySlotUi) -> Unit,
    onUndo: (Long) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.today_sheet_title,
                    slot.medicineName,
                    slot.doseText
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            SheetAction(stringResource(R.string.notification_action_taken)) { onMarkTaken(slot) }
            SheetAction(stringResource(R.string.notification_action_skip)) { onSkip(slot) }
            SheetAction(stringResource(R.string.today_action_uncertain)) { onUncertain(slot) }
            val logId = slot.logId
            if (slot.canUndo && logId != null) {
                SheetAction(stringResource(R.string.today_action_undo)) { onUndo(logId) }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}