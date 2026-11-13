package com.alex.pilltime.ui.export

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alex.pilltime.R
import java.io.File

@Composable
fun ExportRoute(
    onBack: () -> Unit,
    viewModel: ExportViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    ExportScreen(
        state = state,
        onBack = onBack,
        onRangeChange = viewModel::setRange,
        onGenerate = viewModel::generate
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    onBack: () -> Unit,
    onRangeChange: (Int) -> Unit,
    onGenerate: () -> Unit
) {
    val context = LocalContext.current
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "range_label") {
                Text(
                    text = stringResource(R.string.export_range_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item(key = "range") {
                RangeChips(
                    selected = state.rangeDays,
                    enabled = !state.generating,
                    onSelect = onRangeChange
                )
            }
            item(key = "generate") {
                Button(
                    onClick = onGenerate,
                    enabled = !state.generating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (state.generating) R.string.export_generating
                            else R.string.export_generate
                        )
                    )
                }
            }
            if (state.generating) {
                item(key = "progress") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            state.message?.let { message ->
                item(key = "message") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.messageIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            state.preview?.let { preview ->
                item(key = "preview") { PreviewCard(preview = preview) }
                item(key = "share") {
                    OutlinedButton(
                        onClick = { showPrivacyDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(text = stringResource(R.string.export_share))
                    }
                }
            }
        }
    }

    val preview = state.preview
    if (showPrivacyDialog && preview != null) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(text = stringResource(R.string.export_share_dialog_title)) },
            text = { Text(text = stringResource(R.string.export_share_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPrivacyDialog = false
                        sharePdf(context, preview.file)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.export_share_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPrivacyDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun RangeChips(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (days in listOf(7, 30, 90)) {
            FilterChip(
                selected = selected == days,
                enabled = enabled,
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
    7 -> R.string.export_range_7
    30 -> R.string.export_range_30
    else -> R.string.export_range_90
}

@Composable
private fun PreviewCard(preview: ExportPreviewUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.export_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            PreviewRow(
                label = stringResource(R.string.export_preview_range),
                value = preview.rangeText
            )
            PreviewRow(
                label = stringResource(R.string.export_preview_pages),
                value = preview.pageCount.toString()
            )
            PreviewRow(
                label = stringResource(R.string.export_preview_records),
                value = preview.recordCount.toString()
            )
            PreviewRow(
                label = stringResource(R.string.export_preview_size),
                value = preview.sizeText
            )
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 通过 FileProvider 输出 content:// URI，交给系统分享面板。 */
private fun sharePdf(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.export_share_chooser))
        )
    }
}