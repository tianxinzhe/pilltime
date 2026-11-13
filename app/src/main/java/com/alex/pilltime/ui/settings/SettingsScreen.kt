package com.alex.pilltime.ui.settings

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alex.pilltime.BuildConfig
import com.alex.pilltime.R
import com.alex.pilltime.data.entity.AppSettings
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 上线前替换为正式隐私政策 URL。 */
private const val PRIVACY_POLICY_URL = "https://example.com/privacy"

/** 时间选择目标：四餐时间或静默时段起止。 */
private sealed interface TimeTarget {
    data class Meal(val slot: MealSlot) : TimeTarget
    data object QuietStart : TimeTarget
    data object QuietEnd : TimeTarget
}

@Composable
fun SettingsRoute(
    onNavigateToExport: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    // 从系统设置返回时重新检测权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.notice) {
        when (state.notice) {
            SettingsNotice.NONE -> Unit
            SettingsNotice.TEST_SENT -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_test_reminder_toast),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.clearNotice()
            }

            SettingsNotice.INVALID_MINUTES -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_invalid_minutes),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.clearNotice()
            }
        }
    }

    SettingsScreen(
        state = state,
        onMealChange = viewModel::setMeal,
        onMinutesChange = viewModel::setMinutes,
        onQuietStartChange = viewModel::setQuietStart,
        onQuietEndChange = viewModel::setQuietEnd,
        onPause = viewModel::setPaused,
        onTestReminder = viewModel::sendTestReminder,
        onLanguageChange = viewModel::setLanguage,
        onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onOpenExactAlarmSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                openSettings(
                    context,
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        packageUri(context)
                    )
                )
            }
        },
        onOpenNotificationSettings = {
            openSettings(
                context,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        },
        onOpenFullScreenSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                openSettings(
                    context,
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        packageUri(context)
                    )
                )
            }
        },
        onOpenAppSettings = {
            openSettings(
                context,
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
            )
        },
        onOpenPrivacyPolicy = {
            openSettings(context, Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        },
        onNavigateToExport = onNavigateToExport,
        onNavigateToProfiles = onNavigateToProfiles
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onMealChange: (MealSlot, LocalTime) -> Unit,
    onMinutesChange: (MinutesField, Int) -> Unit,
    onQuietStartChange: (LocalTime) -> Unit,
    onQuietEndChange: (LocalTime) -> Unit,
    onPause: (PauseOption) -> Unit,
    onTestReminder: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToProfiles: () -> Unit
) {
    val context = LocalContext.current
    val settings = state.settings
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var minutesField by remember { mutableStateOf<MinutesField?>(null) }
    var minutesInput by remember { mutableStateOf("") }

    val locale = context.resources.configuration.locales[0]
    val pauseFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
    }

    TimePickerLauncher(
        target = timeTarget,
        settings = settings,
        onDismiss = { timeTarget = null },
        onPicked = { target, time ->
            when (target) {
                is TimeTarget.Meal -> onMealChange(target.slot, time)
                TimeTarget.QuietStart -> onQuietStartChange(time)
                TimeTarget.QuietEnd -> onQuietEndChange(time)
            }
        }
    )

    minutesField?.let { field ->
        MinutesDialog(
            field = field,
            value = minutesInput,
            onValueChange = { minutesInput = it },
            onConfirm = {
                onMinutesChange(field, minutesInput.toIntOrNull() ?: -1)
                minutesField = null
            },
            onDismiss = { minutesField = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (state.pauseActive) {
            item(key = "pause_banner") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = settings.pausedUntil?.let {
                            stringResource(
                                R.string.settings_pause_status,
                                pauseFormatter.format(it.atZone(ZoneId.systemDefault()).toLocalDateTime())
                            )
                        } ?: stringResource(R.string.settings_pause_status_indefinite),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        item(key = "meal_header") { SectionHeader(stringResource(R.string.settings_section_meal_times)) }
        item(key = "meal_card") {
            SettingsCard {
                MealRow(stringResource(R.string.settings_meal_breakfast), settings.breakfast) {
                    timeTarget = TimeTarget.Meal(MealSlot.BREAKFAST)
                }
                RowDivider()
                MealRow(stringResource(R.string.settings_meal_lunch), settings.lunch) {
                    timeTarget = TimeTarget.Meal(MealSlot.LUNCH)
                }
                RowDivider()
                MealRow(stringResource(R.string.settings_meal_dinner), settings.dinner) {
                    timeTarget = TimeTarget.Meal(MealSlot.DINNER)
                }
                RowDivider()
                MealRow(stringResource(R.string.settings_meal_bedtime), settings.bedtime) {
                    timeTarget = TimeTarget.Meal(MealSlot.BEDTIME)
                }
            }
        }

        item(key = "defaults_header") {
            SectionHeader(stringResource(R.string.settings_section_defaults))
        }
        item(key = "defaults_card") {
            SettingsCard {
                MinutesRow(
                    title = stringResource(R.string.settings_default_early),
                    value = settings.defaultEarlyMinutes
                ) {
                    minutesField = MinutesField.EARLY
                    minutesInput = settings.defaultEarlyMinutes.toString()
                }
                RowDivider()
                MinutesRow(
                    title = stringResource(R.string.settings_default_late),
                    value = settings.defaultLateMinutes
                ) {
                    minutesField = MinutesField.LATE
                    minutesInput = settings.defaultLateMinutes.toString()
                }
                RowDivider()
                MinutesRow(
                    title = stringResource(R.string.settings_merge_window),
                    value = settings.mergeWindowMinutes
                ) {
                    minutesField = MinutesField.MERGE
                    minutesInput = settings.mergeWindowMinutes.toString()
                }
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.settings_quiet_start),
                    value = formatTime(context, settings.quietStart)
                ) { timeTarget = TimeTarget.QuietStart }
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.settings_quiet_end),
                    value = formatTime(context, settings.quietEnd)
                ) { timeTarget = TimeTarget.QuietEnd }
            }
        }

        item(key = "permissions_header") {
            SectionHeader(stringResource(R.string.settings_section_permissions))
        }
        item(key = "permissions_card") {
            SettingsCard {
                PermissionRow(
                    name = stringResource(R.string.settings_perm_exact_alarm),
                    state = state.permissions.exactAlarm,
                    actionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        stringResource(R.string.settings_action_open)
                    } else {
                        null
                    },
                    onAction = onOpenExactAlarmSettings
                )
                RowDivider()
                PermissionRow(
                    name = stringResource(R.string.settings_perm_notifications),
                    state = state.permissions.notifications,
                    actionText = stringResource(R.string.settings_action_allow),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            state.permissions.notifications != PermissionState.OK
                        ) {
                            onRequestNotificationPermission()
                        } else {
                            onOpenNotificationSettings()
                        }
                    }
                )
                RowDivider()
                PermissionRow(
                    name = stringResource(R.string.settings_perm_full_screen),
                    state = state.permissions.fullScreen,
                    hint = stringResource(R.string.settings_full_screen_hint),
                    actionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        stringResource(R.string.settings_action_open)
                    } else {
                        null
                    },
                    onAction = onOpenFullScreenSettings
                )
                RowDivider()
                PermissionRow(
                    name = stringResource(R.string.settings_perm_battery),
                    state = state.permissions.battery,
                    hint = stringResource(R.string.settings_battery_hint),
                    actionText = stringResource(R.string.settings_action_open),
                    onAction = onOpenAppSettings
                )
            }
        }

        item(key = "tools_header") { SectionHeader(stringResource(R.string.settings_section_tools)) }
        item(key = "tools_card") {
            SettingsCard {
                SettingRow(
                    title = stringResource(R.string.settings_test_reminder),
                    value = ""
                ) { onTestReminder() }
            }
        }

        item(key = "pause_header") { SectionHeader(stringResource(R.string.settings_section_pause)) }
        item(key = "pause_card") {
            SettingsCard {
                TextButton(
                    onClick = { onPause(PauseOption.ONE_HOUR) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.settings_pause_1h))
                }
                RowDivider()
                TextButton(
                    onClick = { onPause(PauseOption.END_OF_TODAY) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.settings_pause_today))
                }
                RowDivider()
                TextButton(
                    onClick = { onPause(PauseOption.RESUME) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.settings_pause_resume))
                }
            }
        }

        item(key = "language_header") {
            SectionHeader(stringResource(R.string.settings_section_language))
        }
        item(key = "language_card") {
            SettingsCard {
                appLanguages.forEachIndexed { index, language ->
                    if (index > 0) RowDivider()
                    LanguageRow(
                        label = stringResource(language.labelRes),
                        selected = settings.appLanguage == language.tag
                    ) { onLanguageChange(language.tag) }
                }
            }
        }

        item(key = "data_header") { SectionHeader(stringResource(R.string.settings_section_data)) }
        item(key = "data_card") {
            SettingsCard {
                SettingRow(
                    title = stringResource(R.string.settings_profiles),
                    value = ""
                ) { onNavigateToProfiles() }
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.settings_export_report),
                    value = ""
                ) { onNavigateToExport() }
            }
        }

        item(key = "about_header") { SectionHeader(stringResource(R.string.settings_section_about)) }
        item(key = "about_card") {
            SettingsCard {
                InfoRow(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
                RowDivider()
                Text(
                    text = stringResource(R.string.settings_medical_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                RowDivider()
                // 药盒识别使用端侧模型，这里明确告知照片与识别过程不离开本机
                Text(
                    text = stringResource(R.string.settings_privacy_local_processing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.settings_privacy_policy),
                    value = ""
                ) { onOpenPrivacyPolicy() }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MealRow(title: String, time: LocalTime, onClick: () -> Unit) {
    SettingRow(title = title, value = formatTime(LocalContext.current, time), onClick = onClick)
}

@Composable
private fun MinutesRow(title: String, value: Int, onClick: () -> Unit) {
    SettingRow(
        title = title,
        value = stringResource(R.string.settings_minutes_value, value),
        onClick = onClick
    )
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PermissionRow(
    name: String,
    state: PermissionState,
    actionText: String?,
    onAction: () -> Unit,
    hint: String? = null
) {
    val statusColor = if (state == PermissionState.OK) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = permissionStatusText(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                }
            }
            if (actionText != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = actionText)
                }
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun permissionStatusText(state: PermissionState): String = when (state) {
    PermissionState.OK -> stringResource(R.string.settings_status_ok)
    PermissionState.DENIED -> stringResource(R.string.settings_status_denied)
    PermissionState.OFF -> stringResource(R.string.settings_status_off)
    PermissionState.RECOMMENDED -> stringResource(R.string.settings_status_recommended)
}

@Composable
private fun TimePickerLauncher(
    target: TimeTarget?,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onPicked: (TimeTarget, LocalTime) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(target) {
        val current = target ?: return@LaunchedEffect
        val initial = when (current) {
            is TimeTarget.Meal -> when (current.slot) {
                MealSlot.BREAKFAST -> settings.breakfast
                MealSlot.LUNCH -> settings.lunch
                MealSlot.DINNER -> settings.dinner
                MealSlot.BEDTIME -> settings.bedtime
            }

            TimeTarget.QuietStart -> settings.quietStart
            TimeTarget.QuietEnd -> settings.quietEnd
        }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                onPicked(current, LocalTime.of(hour, minute))
                onDismiss()
            },
            initial.hour,
            initial.minute,
            DateFormat.is24HourFormat(context)
        ).apply {
            setOnDismissListener { onDismiss() }
        }.show()
    }
}

@Composable
private fun MinutesDialog(
    field: MinutesField,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (field) {
        MinutesField.EARLY -> stringResource(R.string.settings_default_early)
        MinutesField.LATE -> stringResource(R.string.settings_default_late)
        MinutesField.MERGE -> stringResource(R.string.settings_merge_window)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        onValueChange(input.filter { it.isDigit() }.take(4))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_minutes_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private fun formatTime(context: Context, time: LocalTime): String {
    val locale = context.resources.configuration.locales[0]
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, locale))
}

private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // 系统没有对应设置页时静默忽略
    }
}