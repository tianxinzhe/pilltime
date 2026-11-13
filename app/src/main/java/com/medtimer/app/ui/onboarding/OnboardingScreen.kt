package com.medtimer.app.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medtimer.app.R

/**
 * 首次启动引导：单页分三段，不必强制走完。
 * 医疗声明在这里出现一次（合规三处之一）。
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 是否授权都不阻断引导 */ }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        OnboardingSection(index = "1", title = stringResource(R.string.onboarding_purpose_title)) {
            Text(
                text = stringResource(R.string.onboarding_purpose_body),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.onboarding_limits_body),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        OnboardingSection(
            index = "2",
            title = stringResource(R.string.onboarding_permissions_title)
        ) {
            Text(
                text = stringResource(R.string.onboarding_permissions_body),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.onboarding_perm_exact_alarm),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.onboarding_perm_notifications),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.onboarding_perm_full_screen),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.onboarding_perm_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Button(
                    onClick = {
                        notificationPermissionLauncher.launch(
                            android.Manifest.permission.POST_NOTIFICATIONS
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.onboarding_grant_notifications))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                OutlinedButton(
                    onClick = { openExactAlarmSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.onboarding_open_exact_alarm))
                }
            }
        }

        OnboardingSection(
            index = "3",
            title = stringResource(R.string.onboarding_disclaimer_title)
        ) {
            Text(
                text = stringResource(R.string.onboarding_disclaimer_body),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text(stringResource(R.string.onboarding_start))
        }
    }
}

@Composable
private fun OnboardingSection(
    index: String,
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$index. $title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

private fun openExactAlarmSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")
    )
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // 系统没有对应设置页时静默忽略
    }
}