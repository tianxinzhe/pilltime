package com.alex.pilltime.ui.medicine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alex.pilltime.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** 计划 tab 入口。 */
@Composable
fun ScheduleListRoute(
    onEditSchedule: (Long, Long) -> Unit,
    onAddPlan: (Long, LocalDate) -> Unit,
    onNavigateToMedicines: () -> Unit,
    viewModel: ScheduleListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    ScheduleListScreen(
        state = state,
        onToggle = viewModel::setEnabled,
        onEdit = onEditSchedule,
        onDelete = viewModel::delete,
        onDateSelect = viewModel::selectDate,
        onMonthChange = viewModel::setVisibleMonth,
        onAddPlan = onAddPlan,
        onNavigateToMedicines = onNavigateToMedicines
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    state: ScheduleListUiState,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: (Long, Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onAddPlan: (Long, LocalDate) -> Unit,
    onNavigateToMedicines: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ScheduleRowUi?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.tab_schedules)) })
        }
    ) { innerPadding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "calendar") {
                    CalendarCard(
                        selectedDate = state.selectedDate,
                        visibleMonth = state.visibleMonth,
                        dueDays = state.dueDays,
                        onDateSelect = onDateSelect,
                        onMonthChange = onMonthChange
                    )
                }
                item(key = "day_summary") {
                    Text(
                        text = if (state.dayDoseCount > 0) {
                            stringResource(
                                R.string.schedule_day_doses,
                                state.selectedDateText,
                                state.dayDoseCount
                            )
                        } else {
                            stringResource(R.string.schedule_day_none, state.selectedDateText)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                item(key = "add_plan") {
                    AddPlanCard(
                        medicines = state.medicines,
                        startDateText = state.selectedDateText,
                        onAddPlan = { medicineId -> onAddPlan(medicineId, state.selectedDate) },
                        onNavigateToMedicines = onNavigateToMedicines
                    )
                }
                item(key = "all_plans_title") {
                    Text(
                        text = stringResource(R.string.schedule_all_plans_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
                if (state.groups.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.medicine_rules_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } else {
                    items(state.groups, key = { it.medicineId }) { group ->
                        ScheduleGroupCard(
                            group = group,
                            onToggle = onToggle,
                            onEdit = onEdit,
                            onDelete = { pendingDelete = it }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = stringResource(R.string.schedule_delete_title)) },
            text = { Text(text = stringResource(R.string.schedule_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(row.scheduleId)
                        pendingDelete = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 月历卡：切换月份、有用药的日期打点、点选日期。星期起始日跟随 locale。 */
@Composable
private fun CalendarCard(
    selectedDate: LocalDate,
    visibleMonth: YearMonth,
    dueDays: Set<Long>,
    onDateSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { LocalDate.now() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(visibleMonth.minusMonths(1)) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_schedule_prev_month)
                    )
                }
                Text(
                    text = remember(visibleMonth, locale) {
                        visibleMonth.format(monthTitleFormatter(locale))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onMonthChange(visibleMonth.plusMonths(1)) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_schedule_next_month)
                    )
                }
            }

            val weekFields = remember(locale) { WeekFields.of(locale) }
            val weekdayLabels = remember(locale) {
                val first = weekFields.firstDayOfWeek.value
                (0 until 7).map { shift ->
                    DayOfWeek.of(((first - 1 + shift) % 7) + 1)
                        .getDisplayName(TextStyle.NARROW, locale)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                for (label in weekdayLabels) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            val leadingBlanks = remember(visibleMonth, locale) {
                val firstDay = visibleMonth.atDay(1)
                (firstDay.dayOfWeek.value - weekFields.firstDayOfWeek.value + 7) % 7
            }
            val rowCount = (leadingBlanks + visibleMonth.lengthOfMonth() + 6) / 7
            for (row in 0 until rowCount) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (column in 0 until 7) {
                        val dayIndex = row * 7 + column - leadingBlanks
                        if (dayIndex < 0 || dayIndex >= visibleMonth.lengthOfMonth()) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            val date = visibleMonth.atDay(dayIndex + 1)
                            DayCell(
                                date = date,
                                isSelected = date == selectedDate,
                                isToday = date == today,
                                hasDose = date.toEpochDay() in dueDays,
                                onClick = { onDateSelect(date) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 中文显示"2026年11月"，其余语言用 locale 的月份名。 */
private fun monthTitleFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        if (locale.language == "zh") "y年M月" else "LLLL yyyy",
        locale
    )

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasDose: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(
                    if (isToday && !isSelected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else null
            )
        }
        Spacer(Modifier.height(2.dp))
        // 没有用药的日期也保留占位圆点，保证行高一致
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    if (hasDose) MaterialTheme.colorScheme.primary else Color.Transparent
                )
        )
    }
}

/** 增加计划卡：选择药品后直接进入计划编辑，开始日期取日历选中日。 */
@Composable
private fun AddPlanCard(
    medicines: List<MedicineOptionUi>,
    startDateText: String,
    onAddPlan: (Long) -> Unit,
    onNavigateToMedicines: () -> Unit
) {
    var selectedMedicineId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = medicines.firstOrNull { it.medicineId == selectedMedicineId }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (medicines.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_add_no_medicines),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onNavigateToMedicines,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.today_empty_action))
                }
            } else {
                DropdownField(
                    label = stringResource(R.string.schedule_add_medicine_label),
                    selectedText = selected?.name.orEmpty(),
                    options = medicines.map { it.medicineId to it.name },
                    onSelect = { selectedMedicineId = it }
                )
                Text(
                    text = stringResource(R.string.schedule_add_start_on, startDateText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { selected?.let { onAddPlan(it.medicineId) } },
                    enabled = selected != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(text = stringResource(R.string.medicine_rule_add))
                }
            }
        }
    }
}

@Composable
private fun ScheduleGroupCard(
    group: ScheduleGroupUi,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: (Long, Long) -> Unit,
    onDelete: (ScheduleRowUi) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
        ) {
            Text(
                text = group.medicineName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            for (row in group.rows) {
                ScheduleRow(
                    row = row,
                    onToggle = onToggle,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    row: ScheduleRowUi,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: (Long, Long) -> Unit,
    onDelete: (ScheduleRowUi) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.ruleText,
                    style = MaterialTheme.typography.bodyMedium
                )
                row.taperText?.let { taper ->
                    Text(
                        text = taper,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = row.enabled,
                onCheckedChange = { onToggle(row.scheduleId, it) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { onEdit(row.medicineId, row.scheduleId) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.medicine_rule_edit))
            }
            TextButton(
                onClick = { onDelete(row) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.common_delete))
            }
        }
    }
}
