package com.medtimer.app.ui.medicine

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medtimer.app.R
import com.medtimer.app.domain.model.AnchorType
import com.medtimer.app.domain.model.MealType
import java.time.temporal.WeekFields

/** 计划编辑入口。 */
@Composable
fun ScheduleEditRoute(
    medicineId: Long,
    scheduleId: Long,
    onBack: () -> Unit,
    viewModel: ScheduleEditViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(medicineId, scheduleId) { viewModel.load(medicineId, scheduleId) }
    LaunchedEffect(viewModel) { viewModel.close.collect { onBack() } }

    ScheduleEditScreen(state = state, viewModel = viewModel, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(
    state: ScheduleEditUiState,
    viewModel: ScheduleEditViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val formatter = remember { MedicineTextFormatter(context) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var taperPickerIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val form = state.form
    val doseX10 = parseDoseX10(form.doseText)
    val integerWarning = !state.splittable && doseX10 != null && doseX10 % 10 != 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (state.isNew) {
                                R.string.schedule_edit_title_new
                            } else {
                                R.string.schedule_edit_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
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
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.medicineName.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_medicine_label, state.medicineName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.schedule_prn_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = form.isPrn,
                    onCheckedChange = { checked ->
                        viewModel.updateForm { it.copy(isPrn = checked) }
                    }
                )
            }
            if (form.isPrn) {
                Text(
                    text = stringResource(R.string.schedule_prn_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!form.isPrn) {
                SectionTitle(stringResource(R.string.schedule_anchor_label))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.anchorType == AnchorType.CLOCK,
                        onClick = {
                            viewModel.updateForm { it.copy(anchorType = AnchorType.CLOCK) }
                        },
                        label = { Text(stringResource(R.string.schedule_anchor_clock)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = form.anchorType == AnchorType.MEAL,
                        onClick = {
                            viewModel.updateForm { it.copy(anchorType = AnchorType.MEAL) }
                        },
                        label = { Text(stringResource(R.string.schedule_anchor_meal)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (form.anchorType == AnchorType.CLOCK) {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.schedule_time_value,
                                formatter.timeText(form.clockHour, form.clockMinute)
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MealChip(
                            meal = MealType.BREAKFAST,
                            selected = form.mealType == MealType.BREAKFAST,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                        MealChip(
                            meal = MealType.LUNCH,
                            selected = form.mealType == MealType.LUNCH,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MealChip(
                            meal = MealType.DINNER,
                            selected = form.mealType == MealType.DINNER,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                        MealChip(
                            meal = MealType.BEDTIME,
                            selected = form.mealType == MealType.BEDTIME,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    LabeledTextField(
                        value = form.offsetMinText,
                        onValueChange = { raw ->
                            if (SIGNED_MINUTES.matches(raw)) {
                                viewModel.updateForm { it.copy(offsetMinText = raw) }
                            }
                        },
                        label = stringResource(R.string.schedule_offset_label),
                        keyboardType = KeyboardType.Number,
                        isError = state.errors.contains(ScheduleFormError.OFFSET_INVALID)
                    )
                    if (state.errors.contains(ScheduleFormError.OFFSET_INVALID)) {
                        FieldError(text = stringResource(R.string.schedule_error_offset))
                    }
                }

                SectionTitle(stringResource(R.string.schedule_recurrence_label))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurrenceChip(
                        kind = RecurrenceKind.DAILY,
                        selected = form.recurrence == RecurrenceKind.DAILY,
                        label = stringResource(R.string.schedule_recurrence_daily),
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                    RecurrenceChip(
                        kind = RecurrenceKind.EVERY_N_DAYS,
                        selected = form.recurrence == RecurrenceKind.EVERY_N_DAYS,
                        label = stringResource(R.string.schedule_recurrence_every_n),
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurrenceChip(
                        kind = RecurrenceKind.WEEKLY,
                        selected = form.recurrence == RecurrenceKind.WEEKLY,
                        label = stringResource(R.string.schedule_recurrence_weekly),
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                    RecurrenceChip(
                        kind = RecurrenceKind.MONTHLY,
                        selected = form.recurrence == RecurrenceKind.MONTHLY,
                        label = stringResource(R.string.schedule_recurrence_monthly),
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }

                when (form.recurrence) {
                    RecurrenceKind.DAILY -> Unit
                    RecurrenceKind.EVERY_N_DAYS -> {
                        LabeledTextField(
                            value = form.everyNDaysText,
                            onValueChange = { raw ->
                                if (DIGITS.matches(raw)) {
                                    viewModel.updateForm { it.copy(everyNDaysText = raw) }
                                }
                            },
                            label = stringResource(R.string.schedule_interval_label),
                            keyboardType = KeyboardType.Number,
                            isError = state.errors.contains(ScheduleFormError.EVERY_N_DAYS_INVALID)
                        )
                        if (state.errors.contains(ScheduleFormError.EVERY_N_DAYS_INVALID)) {
                            FieldError(text = stringResource(R.string.schedule_error_interval))
                        }
                    }
                    RecurrenceKind.WEEKLY -> {
                        Text(
                            text = stringResource(R.string.schedule_weekdays_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // 周起始日跟随 locale，例如美国从周日开始
                        val locale = LocalContext.current.resources.configuration.locales[0]
                        val orderedDays = remember(locale) {
                            val first = WeekFields.of(locale).firstDayOfWeek.value
                            (0 until 7).map { ((first - 1 + it) % 7) + 1 }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (day in orderedDays.take(4)) {
                                WeekdayChip(day = day, form = form, viewModel = viewModel, modifier = Modifier.weight(1f))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (day in orderedDays.drop(4)) {
                                WeekdayChip(day = day, form = form, viewModel = viewModel, modifier = Modifier.weight(1f))
                            }
                        }
                        if (state.errors.contains(ScheduleFormError.WEEKDAYS_EMPTY)) {
                            FieldError(text = stringResource(R.string.schedule_error_weekdays))
                        }
                    }
                    RecurrenceKind.MONTHLY -> {
                        LabeledTextField(
                            value = form.monthlyDayText,
                            onValueChange = { raw ->
                                if (DIGITS.matches(raw)) {
                                    viewModel.updateForm { it.copy(monthlyDayText = raw) }
                                }
                            },
                            label = stringResource(R.string.schedule_monthly_day_label),
                            keyboardType = KeyboardType.Number,
                            isError = state.errors.contains(ScheduleFormError.MONTHLY_DAY_INVALID)
                        )
                        if (state.errors.contains(ScheduleFormError.MONTHLY_DAY_INVALID)) {
                            FieldError(text = stringResource(R.string.schedule_error_monthly_day))
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.medicine_date_value,
                            stringResource(R.string.schedule_start_date_label),
                            formatter.dateText(form.startDate)
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.schedule_long_term),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = form.longTerm,
                        onCheckedChange = { checked ->
                            viewModel.updateForm {
                                it.copy(
                                    longTerm = checked,
                                    endDate = if (checked) {
                                        null
                                    } else {
                                        it.endDate ?: it.startDate.plusMonths(1)
                                    }
                                )
                            }
                        }
                    )
                }
                if (!form.longTerm) {
                    OutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.medicine_date_value,
                                stringResource(R.string.schedule_end_date_label),
                                form.endDate?.let { formatter.dateText(it) }
                                    ?: stringResource(R.string.common_not_set)
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (state.errors.contains(ScheduleFormError.END_DATE_INVALID)) {
                    FieldError(text = stringResource(R.string.schedule_error_end_date))
                }
            }

            SectionTitle(stringResource(R.string.schedule_dose_label))
            LabeledTextField(
                value = form.doseText,
                onValueChange = { raw ->
                    if (DOSE_INPUT.matches(raw)) {
                        viewModel.updateForm { it.copy(doseText = raw) }
                    }
                },
                label = stringResource(R.string.schedule_dose_label_with_unit, state.doseUnit),
                keyboardType = KeyboardType.Decimal,
                isError = integerWarning ||
                    state.errors.contains(ScheduleFormError.DOSE_INVALID) ||
                    state.errors.contains(ScheduleFormError.DOSE_NOT_INTEGER)
            )
            when {
                integerWarning || state.errors.contains(ScheduleFormError.DOSE_NOT_INTEGER) ->
                    FieldError(
                        text = stringResource(
                            R.string.schedule_dose_integer_error,
                            state.doseUnit
                        )
                    )
                state.errors.contains(ScheduleFormError.DOSE_INVALID) ->
                    FieldError(text = stringResource(R.string.schedule_dose_invalid))
            }

            if (form.isPrn) {
                LabeledTextField(
                    value = form.prnMaxPerDayText,
                    onValueChange = { raw ->
                        if (DIGITS.matches(raw)) {
                            viewModel.updateForm { it.copy(prnMaxPerDayText = raw) }
                        }
                    },
                    label = stringResource(R.string.schedule_prn_max_label),
                    keyboardType = KeyboardType.Number,
                    isError = state.errors.contains(ScheduleFormError.PRN_LIMIT_INVALID)
                )
                if (state.errors.contains(ScheduleFormError.PRN_LIMIT_INVALID)) {
                    FieldError(text = stringResource(R.string.schedule_error_prn_limit))
                }
            }

            if (!form.isPrn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.schedule_taper_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.taperEnabled,
                        onCheckedChange = viewModel::setTaperEnabled
                    )
                }
                if (state.taperEnabled) {
                    Text(
                        text = stringResource(R.string.schedule_taper_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.taperSteps.forEachIndexed { index, step ->
                        TaperStepEditor(
                            index = index,
                            step = step,
                            doseUnit = state.doseUnit,
                            splittable = state.splittable,
                            formatter = formatter,
                            showRemove = state.taperSteps.size > 1,
                            onPickDate = { taperPickerIndex = index },
                            onDoseChange = { raw ->
                                viewModel.updateTaperStep(index, doseText = raw)
                            },
                            onRemove = { viewModel.removeTaperStep(index) }
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::addTaperStep,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(text = stringResource(R.string.schedule_taper_add_step))
                    }
                    if (state.errors.contains(ScheduleFormError.TAPER_EMPTY)) {
                        FieldError(text = stringResource(R.string.schedule_error_taper_empty))
                    }
                    if (state.errors.contains(ScheduleFormError.TAPER_DATE_DUPLICATE)) {
                        FieldError(text = stringResource(R.string.schedule_error_taper_date))
                    }
                    if (state.errors.contains(ScheduleFormError.TAPER_DOSE_INVALID)) {
                        FieldError(text = stringResource(R.string.schedule_error_taper_dose))
                    }
                    if (state.errors.contains(ScheduleFormError.TAPER_DOSE_NOT_INTEGER)) {
                        FieldError(
                            text = stringResource(
                                R.string.schedule_error_taper_integer,
                                state.doseUnit
                            )
                        )
                    }
                }
            }

            if (!form.isPrn) {
                SectionTitle(stringResource(R.string.schedule_window_label))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledTextField(
                        value = form.earlyText,
                        onValueChange = { raw ->
                            if (DIGITS.matches(raw)) {
                                viewModel.updateForm { it.copy(earlyText = raw) }
                            }
                        },
                        label = stringResource(R.string.schedule_early_label),
                        keyboardType = KeyboardType.Number,
                        isError = state.errors.contains(ScheduleFormError.WINDOW_INVALID),
                        modifier = Modifier.weight(1f)
                    )
                    LabeledTextField(
                        value = form.lateText,
                        onValueChange = { raw ->
                            if (DIGITS.matches(raw)) {
                                viewModel.updateForm { it.copy(lateText = raw) }
                            }
                        },
                        label = stringResource(R.string.schedule_late_label),
                        keyboardType = KeyboardType.Number,
                        isError = state.errors.contains(ScheduleFormError.WINDOW_INVALID),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.errors.contains(ScheduleFormError.WINDOW_INVALID)) {
                    FieldError(text = stringResource(R.string.schedule_error_window))
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_save),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }

            if (!state.isNew) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.schedule_delete_action),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        MedicineTimePickerDialog(
            hour = form.clockHour,
            minute = form.clockMinute,
            onConfirm = { hour, minute ->
                viewModel.updateForm { it.copy(clockHour = hour, clockMinute = minute) }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showStartPicker) {
        MedicineDatePickerDialog(
            initialDate = form.startDate,
            onConfirm = { date ->
                if (date != null) {
                    viewModel.updateForm { it.copy(startDate = date) }
                }
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        MedicineDatePickerDialog(
            initialDate = form.endDate,
            onConfirm = { date ->
                viewModel.updateForm { it.copy(endDate = date) }
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }

    taperPickerIndex?.let { index ->
        MedicineDatePickerDialog(
            initialDate = state.taperSteps.getOrNull(index)?.startDate,
            onConfirm = { date ->
                if (date != null) {
                    viewModel.updateTaperStep(index, startDate = date)
                }
                taperPickerIndex = null
            },
            onDismiss = { taperPickerIndex = null }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(text = stringResource(R.string.schedule_delete_title)) },
            text = { Text(text = stringResource(R.string.schedule_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 只允许数字与正负号的分钟输入。 */
private val SIGNED_MINUTES = Regex("^[+-]?\\d{0,4}$")
private val DIGITS = Regex("^\\d{0,4}$")
private val DOSE_INPUT = Regex("^\\d{0,7}(\\.\\d?)?$")

@Composable
private fun TaperStepEditor(
    index: Int,
    step: TaperStepDraft,
    doseUnit: String,
    splittable: Boolean,
    formatter: MedicineTextFormatter,
    showRemove: Boolean,
    onPickDate: () -> Unit,
    onDoseChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val doseX10 = parseDoseX10(step.doseText)
    val integerWarning = !splittable && doseX10 != null && doseX10 % 10 != 0
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_taper_step_title, index + 1),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedButton(
            onClick = onPickDate,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(text = formatter.dateText(step.startDate), textAlign = TextAlign.Center)
        }
        LabeledTextField(
            value = step.doseText,
            onValueChange = { raw ->
                if (DOSE_INPUT.matches(raw)) onDoseChange(raw)
            },
            label = stringResource(R.string.schedule_taper_dose_label, doseUnit),
            keyboardType = KeyboardType.Decimal,
            isError = integerWarning || doseX10 == null
        )
        if (integerWarning) {
            FieldError(text = stringResource(R.string.schedule_error_taper_integer, doseUnit))
        }
        if (showRemove) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.schedule_taper_remove))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun MealChip(
    meal: MealType,
    selected: Boolean,
    viewModel: ScheduleEditViewModel,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = { viewModel.updateForm { it.copy(mealType = meal) } },
        label = { Text(text = stringResource(mealLabelRes(meal))) },
        modifier = modifier
    )
}

@Composable
private fun RecurrenceChip(
    kind: RecurrenceKind,
    selected: Boolean,
    label: String,
    viewModel: ScheduleEditViewModel,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = { viewModel.updateForm { it.copy(recurrence = kind) } },
        label = { Text(text = label) },
        modifier = modifier
    )
}

@Composable
private fun WeekdayChip(
    day: Int,
    form: ScheduleForm,
    viewModel: ScheduleEditViewModel,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = day in form.weekdays,
        onClick = {
            viewModel.updateForm {
                val days = if (day in it.weekdays) it.weekdays - day else it.weekdays + day
                it.copy(weekdays = days)
            }
        },
        label = { Text(text = stringResource(weekdayLabelRes(day))) },
        modifier = modifier
    )
}

private fun mealLabelRes(meal: MealType): Int = when (meal) {
    MealType.BREAKFAST -> R.string.meal_breakfast
    MealType.LUNCH -> R.string.meal_lunch
    MealType.DINNER -> R.string.meal_dinner
    MealType.BEDTIME -> R.string.meal_bedtime
}

private fun weekdayLabelRes(day: Int): Int = when (day) {
    1 -> R.string.weekday_mon
    2 -> R.string.weekday_tue
    3 -> R.string.weekday_wed
    4 -> R.string.weekday_thu
    5 -> R.string.weekday_fri
    6 -> R.string.weekday_sat
    else -> R.string.weekday_sun
}

/** 时分选择：跟随系统 12/24 小时制，宽度自适应窄屏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineTimePickerDialog(
    hour: Int,
    minute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }
    val timeState = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = is24Hour
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.schedule_pick_time),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                TimePicker(state = timeState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(text = stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        onClick = { onConfirm(timeState.hour, timeState.minute) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(text = stringResource(R.string.common_ok))
                    }
                }
            }
        }
    }
}