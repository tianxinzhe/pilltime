package com.alex.pilltime.ui.medicine

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alex.pilltime.R
import com.alex.pilltime.domain.model.MedicineForm
import com.alex.pilltime.util.ImageStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 药品编辑入口。 */
@Composable
fun MedicineEditRoute(
    medicineId: Long,
    onBack: () -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Long) -> Unit,
    viewModel: MedicineEditViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(medicineId) { viewModel.load(medicineId) }
    LaunchedEffect(viewModel) { viewModel.close.collect { onBack() } }

    MedicineEditScreen(
        state = state,
        viewModel = viewModel,
        onBack = onBack,
        onAddSchedule = onAddSchedule,
        onEditSchedule = onEditSchedule
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineEditScreen(
    state: MedicineEditUiState,
    viewModel: MedicineEditViewModel,
    onBack: () -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Long) -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.attachPhoto(uri)
    }
    val scanAvailable = remember { viewModel.isScanAvailable() }
    val namePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.scanMedicineName(uri)
    }

    var pendingScheduleDelete by remember { mutableStateOf<MedicineScheduleUi?>(null) }
    var confirmMedicineDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (state.isNew) {
                                R.string.medicine_edit_title_new
                            } else {
                                R.string.medicine_edit_title
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
            val nameTrailingIcon: (@Composable () -> Unit)? = if (scanAvailable) {
                {
                    if (state.nameScanRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                namePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.medicine_scan_name_cd)
                            )
                        }
                    }
                }
            } else {
                null
            }
            LabeledTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = stringResource(R.string.medicine_name_label),
                isError = state.nameError,
                trailingIcon = nameTrailingIcon
            )
            if (state.nameError) {
                FieldError(text = stringResource(R.string.medicine_name_required))
            }
            if (state.nameScanFailed) {
                Text(
                    text = stringResource(R.string.medicine_scan_name_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownField(
                label = stringResource(R.string.medicine_form_label),
                selectedText = stringResource(formLabelRes(state.form)),
                options = MedicineForm.entries.map { it to stringResource(formLabelRes(it)) },
                onSelect = viewModel::onFormChange
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledTextField(
                    value = state.specValue,
                    onValueChange = viewModel::onSpecValueChange,
                    label = stringResource(R.string.medicine_spec_value_label),
                    keyboardType = KeyboardType.Decimal,
                    isError = state.specError,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    value = state.specUnit,
                    onValueChange = viewModel::onSpecUnitChange,
                    label = stringResource(R.string.medicine_spec_unit_label),
                    isError = state.specError,
                    modifier = Modifier.weight(1f)
                )
            }
            DropdownField(
                label = stringResource(R.string.medicine_dose_unit_label),
                selectedText = stringResource(doseUnitLabelRes(state.doseUnit)),
                options = DOSE_UNIT_OPTIONS.map { it to stringResource(doseUnitLabelRes(it)) },
                onSelect = viewModel::onDoseUnitChange
            )
            if (state.specError) {
                FieldError(text = stringResource(R.string.medicine_spec_required))
            }

            SwitchRow(
                label = stringResource(R.string.medicine_splittable_label),
                checked = state.splittable,
                onCheckedChange = viewModel::onSplittableChange
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledTextField(
                    value = state.packSize,
                    onValueChange = viewModel::onPackSizeChange,
                    label = stringResource(R.string.medicine_pack_size_label),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                LabeledTextField(
                    value = state.packUnit,
                    onValueChange = viewModel::onPackUnitChange,
                    label = stringResource(R.string.medicine_pack_unit_label),
                    modifier = Modifier.weight(1f)
                )
            }

            SwitchRow(
                label = stringResource(R.string.medicine_auto_deduct_label),
                checked = state.autoDeductStock,
                onCheckedChange = viewModel::onAutoDeductChange
            )

            LabeledTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                label = stringResource(R.string.medicine_note_label),
                singleLine = false,
                minLines = 3
            )

            PhotoSection(
                state = state,
                onPick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemove = viewModel::removePhoto
            )

            RulesSection(
                state = state,
                onAdd = onAddSchedule,
                onEdit = onEditSchedule,
                onToggle = viewModel::setScheduleEnabled,
                onDelete = { pendingScheduleDelete = it }
            )

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
                    onClick = { confirmMedicineDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.medicine_delete_action),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    pendingScheduleDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingScheduleDelete = null },
            title = { Text(text = stringResource(R.string.schedule_delete_title)) },
            text = { Text(text = stringResource(R.string.schedule_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSchedule(schedule.id)
                        pendingScheduleDelete = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingScheduleDelete = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (confirmMedicineDelete) {
        AlertDialog(
            onDismissRequest = { confirmMedicineDelete = false },
            title = { Text(text = stringResource(R.string.medicine_delete_title)) },
            text = {
                Text(text = stringResource(R.string.medicine_delete_message, state.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmMedicineDelete = false
                        viewModel.deleteMedicine()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmMedicineDelete = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (state.nameCandidates.isNotEmpty()) {
        NameCandidatesDialog(
            candidates = state.nameCandidates,
            onConfirm = viewModel::applyNameCandidate,
            onDismiss = viewModel::dismissNameCandidates
        )
    }
}

/** 识别结果只做预填：用户选中并确认后才写入药名，仍需手动保存。 */
@Composable
private fun NameCandidatesDialog(
    candidates: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(candidates) { mutableStateOf(candidates.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.medicine_scan_name_title)) },
        text = {
            Column {
                for (candidate in candidates) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { selected = candidate },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == candidate, onClick = null)
                        Spacer(Modifier.size(8.dp))
                        Text(text = candidate, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.medicine_scan_name_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}

private val DOSE_UNIT_OPTIONS = listOf("片", "粒", "ml", "滴", "喷", "U")

/** 剂型与剂量单位的显示文案都由 strings.xml 提供，数据里保存稳定值。 */
private fun formLabelRes(form: MedicineForm): Int = when (form) {
    MedicineForm.TABLET -> R.string.medicine_form_tablet
    MedicineForm.CAPSULE -> R.string.medicine_form_capsule
    MedicineForm.LIQUID -> R.string.medicine_form_liquid
    MedicineForm.DROPS -> R.string.medicine_form_drops
    MedicineForm.SPRAY -> R.string.medicine_form_spray
    MedicineForm.PEN -> R.string.medicine_form_pen
}

private fun doseUnitLabelRes(unit: String): Int = when (unit.trim().lowercase()) {
    "片", "tablet", "tablets" -> R.string.dose_unit_tablet
    "粒", "capsule", "capsules" -> R.string.dose_unit_capsule
    "ml" -> R.string.dose_unit_ml
    "滴", "drop", "drops" -> R.string.dose_unit_drop
    "喷", "spray", "sprays" -> R.string.dose_unit_spray
    "u", "unit", "units" -> R.string.dose_unit_unit
    else -> R.string.dose_unit_other
}

@Composable
private fun PhotoSection(
    state: MedicineEditUiState,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    val photoTitle = stringResource(R.string.medicine_photo_title)
    Text(
        text = photoTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    val bitmap = remember(state.imagePath) { ImageStore.loadImage(state.imagePath) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(
                R.string.cd_medicine_photo,
                state.name.ifEmpty { photoTitle }
            ),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Text(
            text = stringResource(R.string.medicine_photo_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
        ) {
            Text(
                text = stringResource(
                    if (state.imagePath == null) {
                        R.string.medicine_photo_pick
                    } else {
                        R.string.medicine_photo_retake
                    }
                ),
                textAlign = TextAlign.Center
            )
        }
        if (state.imagePath != null) {
            TextButton(
                onClick = onRemove,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    text = stringResource(R.string.medicine_photo_delete),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    if (state.photoError) {
        Text(
            text = stringResource(R.string.medicine_photo_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun RulesSection(
    state: MedicineEditUiState,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (MedicineScheduleUi) -> Unit
) {
    Text(
        text = stringResource(R.string.medicine_rules_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    if (state.isNew) {
        Text(
            text = stringResource(R.string.medicine_rules_need_save),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    if (state.schedules.isEmpty()) {
        Text(
            text = stringResource(R.string.medicine_rules_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    for (schedule in state.schedules) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.ruleText,
                    style = MaterialTheme.typography.bodyMedium
                )
                schedule.taperText?.let { taper ->
                    Text(
                        text = taper,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = { onToggle(schedule.id, it) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { onEdit(schedule.id) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.medicine_rule_edit))
            }
            TextButton(
                onClick = { onDelete(schedule) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.common_delete))
            }
        }
        HorizontalDivider()
    }
    Spacer(Modifier.height(4.dp))
    OutlinedButton(
        onClick = onAdd,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Text(
            text = stringResource(R.string.medicine_rule_add),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DateFieldButton(label: String, value: String?, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Text(
            text = stringResource(
                R.string.medicine_date_value,
                label,
                value ?: stringResource(R.string.common_not_set)
            ),
            textAlign = TextAlign.Center
        )
    }
}

/** 所有数值/文本输入共用，统一最小触控高度。 */
@Composable
internal fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        trailingIcon = trailingIcon
    )
}

@Composable
internal fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

/** 表单下拉框：只读文本框 + 菜单，选项文案已由调用方本地化。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> DropdownField(
    label: String,
    selectedText: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for ((value, text) in options) {
                DropdownMenuItem(
                    text = { Text(text = text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 日期选择：确认回调本地日期，清除回调 null。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicineDatePickerDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.toEpochDay()?.times(86_400_000L)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    onConfirm(
                        millis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onConfirm(null) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_clear))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        }
    ) {
        DatePicker(state = state)
    }
}