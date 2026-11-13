package com.medtimer.app.ui.stock

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medtimer.app.R

@Composable
fun StockRoute(
    medicineId: Long,
    onBack: () -> Unit,
    viewModel: StockViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(medicineId) { viewModel.load(medicineId) }

    StockScreen(
        state = state,
        onBack = onBack,
        onPurchase = viewModel::purchase,
        onAdjust = viewModel::adjust
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    state: StockUiState,
    onBack: () -> Unit,
    onPurchase: (String) -> Unit,
    onAdjust: (String) -> Unit
) {
    var showPurchase by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.stock_title)) },
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
            state.missing -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.stock_not_found),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onBack,
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) {
                        Text(text = stringResource(R.string.common_back))
                    }
                }
            }
            else -> {
                val noticeText = when (state.notice) {
                    StockNotice.NONE -> null
                    StockNotice.NO_CHANGE -> stringResource(R.string.stock_no_change)
                    StockNotice.INVALID_QTY -> stringResource(R.string.stock_qty_invalid)
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "header") { StockHeader(state) }

                    if (state.outOfStock) {
                        item(key = "banner_empty") {
                            StockBanner(text = stringResource(R.string.stock_banner_empty))
                        }
                    } else if (state.lowStock) {
                        item(key = "banner_low") {
                            StockBanner(text = stringResource(R.string.stock_banner_low))
                        }
                    }
                    if (state.expired) {
                        item(key = "banner_expired") {
                            StockBanner(text = stringResource(R.string.stock_banner_expired))
                        }
                    }
                    if (state.expiringBeforeRunOut) {
                        item(key = "banner_expiring") {
                            StockBanner(text = stringResource(R.string.stock_banner_expiring))
                        }
                    }

                    item(key = "actions") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showPurchase = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.stock_action_purchase),
                                    textAlign = TextAlign.Center
                                )
                            }
                            OutlinedButton(
                                onClick = { showAdjust = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.stock_action_adjust),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (noticeText != null) {
                        item(key = "notice") {
                            Text(
                                text = noticeText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.expireText != null || state.shelfText != null) {
                        item(key = "facts") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.expireText?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                state.shelfText?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item(key = "changes_title") {
                        Text(
                            text = stringResource(R.string.stock_changes_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (state.changes.isEmpty()) {
                        item(key = "changes_empty") {
                            Text(
                                text = stringResource(R.string.stock_changes_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(state.changes, key = { it.id }) { change ->
                            StockChangeRow(change = change)
                        }
                    }
                }
            }
        }
    }

    if (showPurchase) {
        QuantityDialog(
            title = stringResource(R.string.stock_purchase_title),
            doseUnit = state.doseUnit,
            requirePositive = true,
            onConfirm = onPurchase,
            onDismiss = { showPurchase = false }
        )
    }
    if (showAdjust) {
        QuantityDialog(
            title = stringResource(R.string.stock_adjust_title),
            doseUnit = state.doseUnit,
            requirePositive = false,
            onConfirm = onAdjust,
            onDismiss = { showAdjust = false }
        )
    }
}

@Composable
private fun StockHeader(state: StockUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = state.medicineName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(text = state.stockText, style = MaterialTheme.typography.titleLarge)
        Text(
            text = state.daysText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 事实性提示条，只陈述库存与有效期状态。 */
@Composable
private fun StockBanner(
    text: String,
    container: Color = MaterialTheme.colorScheme.errorContainer,
    content: Color = MaterialTheme.colorScheme.onErrorContainer
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
        }
    }
}

@Composable
private fun StockChangeRow(change: StockChangeUi) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = change.reasonText, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = change.timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                change.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(text = change.deltaText, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
    }
}

/** 数量输入统一走 ×10 整数解析；入库要求大于 0，盘点允许 0。 */
@Composable
private fun QuantityDialog(
    title: String,
    doseUnit: String,
    requirePositive: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val parsed = parseQuantityX10(text)
    val invalid = text.isNotBlank() && (parsed == null || (requirePositive && parsed <= 0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(text = stringResource(R.string.stock_qty_label, doseUnit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = invalid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (invalid) {
                    Text(
                        text = stringResource(R.string.stock_qty_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(text)
                    onDismiss()
                },
                enabled = parsed != null && (!requirePositive || parsed > 0),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
        }
    )
}