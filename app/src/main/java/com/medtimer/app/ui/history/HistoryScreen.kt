package com.medtimer.app.ui.history

import android.content.Context
import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medtimer.app.R
import com.medtimer.app.ui.text.formatDoseText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 历史页文案：日期与时刻按当前 locale 格式化，其余全部来自 strings.xml。 */
class AndroidHistoryTextProvider(private val context: Context) : HistoryTextProvider {

    private val locale = context.resources.configuration.locales[0]
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        locale
    )

    override fun date(date: LocalDate): String = dateFormatter.format(date)

    override fun time(plannedAt: Instant): String =
        timeFormatter.format(plannedAt.atZone(ZoneId.systemDefault()).toLocalTime())

    override fun dose(qtyX10: Int, doseUnit: String): String =
        formatDoseText(context, qtyX10, doseUnit)

    override fun status(status: HistoryStatus): String = context.getString(statusResId(status))

    override fun late(minutes: Int): String =
        context.getString(R.string.history_late_minutes, minutes)

    private fun statusResId(status: HistoryStatus): Int = when (status) {
        HistoryStatus.ON_TIME -> R.string.history_status_on_time
        HistoryStatus.LATE -> R.string.today_status_late
        HistoryStatus.UNCONFIRMED -> R.string.today_status_missed
        HistoryStatus.UNCERTAIN -> R.string.today_status_uncertain
        HistoryStatus.SKIPPED -> R.string.today_status_skipped
    }
}

@Composable
fun HistoryRoute(
    onExport: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    HistoryScreen(
        state = state,
        onRangeChange = viewModel::setRange,
        onBackfillTaken = viewModel::backfillTaken,
        onBackfillUncertain = viewModel::backfillUncertain,
        onExport = onExport
    )
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onRangeChange: (Int) -> Unit,
    onBackfillTaken: (HistoryRowUi) -> Unit,
    onBackfillUncertain: (HistoryRowUi) -> Unit,
    onExport: () -> Unit
) {
    var pendingBackfill by remember { mutableStateOf<HistoryRowUi?>(null) }

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
        item(key = "title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.export_entry_button))
                }
            }
        }
        item(key = "range") {
            RangeChips(selected = state.rangeDays, onSelect = onRangeChange)
        }

        if (state.isEmpty) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }
        } else {
            item(key = "summary") { SummaryCard(counts = state.counts) }
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                DayCard(day = day, onRowClick = { pendingBackfill = it })
            }
        }
    }

    pendingBackfill?.let { row ->
        BackfillDialog(
            row = row,
            onDismiss = { pendingBackfill = null },
            onTaken = {
                onBackfillTaken(row)
                pendingBackfill = null
            },
            onUncertain = {
                onBackfillUncertain(row)
                pendingBackfill = null
            }
        )
    }
}

@Composable
private fun RangeChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (days in listOf(7, 30, 90)) {
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = {
                    Text(
                        text = stringResource(rangeLabelRes(days)),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            )
        }
    }
}

private fun rangeLabelRes(days: Int): Int = when (days) {
    7 -> R.string.history_range_7
    30 -> R.string.history_range_30
    else -> R.string.history_range_90
}

@Composable
private fun SummaryCard(counts: HistoryCounts) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(R.string.history_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            SummaryRow(stringResource(R.string.history_summary_on_time), counts.onTime)
            SummaryRow(stringResource(R.string.history_summary_late), counts.late)
            SummaryRow(stringResource(R.string.history_summary_unconfirmed), counts.unconfirmed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SummaryColumn(
                    label = stringResource(R.string.history_summary_uncertain),
                    value = counts.uncertain,
                    modifier = Modifier.weight(1f)
                )
                SummaryColumn(
                    label = stringResource(R.string.history_summary_skipped),
                    value = counts.skipped,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SummaryRow(stringResource(R.string.history_summary_total), counts.total)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun SummaryColumn(label: String, value: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun DayCard(day: HistoryDayUi, onRowClick: (HistoryRowUi) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Text(
                text = day.dateText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            for (row in day.rows) {
                HistoryRow(row = row, onClick = { onRowClick(row) })
            }
        }
    }
}

@Composable
private fun HistoryRow(row: HistoryRowUi, onClick: () -> Unit) {
    val canBackfill = row.status == HistoryStatus.UNCONFIRMED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = canBackfill, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.timeText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = row.medicineName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = row.doseText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canBackfill) {
                Text(
                    text = stringResource(R.string.history_backfill_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            StatusChip(row = row)
            row.delayText?.let { delay ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = delay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusChip(row: HistoryRowUi) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (row.status) {
        HistoryStatus.UNCONFIRMED -> scheme.errorContainer to scheme.onErrorContainer
        HistoryStatus.LATE -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text = row.statusText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** 补记对话框：吃过了写 certain=false，记不清写 UNCERTAIN，之后不再判为漏服。 */
@Composable
private fun BackfillDialog(
    row: HistoryRowUi,
    onDismiss: () -> Unit,
    onTaken: () -> Unit,
    onUncertain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.history_backfill_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.history_backfill_message,
                    row.medicineName,
                    row.timeText
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onTaken,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.history_backfill_taken))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onUncertain,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.today_action_uncertain))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        }
    )
}