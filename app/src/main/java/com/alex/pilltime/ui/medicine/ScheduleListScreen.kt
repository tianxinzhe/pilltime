package com.alex.pilltime.ui.medicine

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alex.pilltime.R

/** 计划 tab 入口。 */
@Composable
fun ScheduleListRoute(
    onEditSchedule: (Long, Long) -> Unit,
    onNavigateToMedicines: () -> Unit,
    viewModel: ScheduleListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    ScheduleListScreen(
        state = state,
        onToggle = viewModel::setEnabled,
        onEdit = onEditSchedule,
        onAdd = onNavigateToMedicines,
        onDelete = viewModel::delete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    state: ScheduleListUiState,
    onToggle: (Long, Boolean) -> Unit,
    onEdit: (Long, Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ScheduleRowUi?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.tab_schedules)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.medicine_rule_add)
                )
            }
        }
    ) { innerPadding ->
        when {
            state.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.groups.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.medicine_rules_empty),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) {
                        Text(text = stringResource(R.string.today_empty_action))
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
