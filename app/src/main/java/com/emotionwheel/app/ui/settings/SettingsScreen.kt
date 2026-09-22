package com.emotionwheel.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionwheel.app.R
import com.emotionwheel.app.ui.containerViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SyncStamp: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = containerViewModel {
        SettingsViewModel(it.settings, it.cloudBackup)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val failedTemplate = stringResource(R.string.settings_sync_failed)

    LaunchedEffect(error) {
        error?.let {
            snackbarHost.showSnackbar(String.format(failedTemplate, it))
            viewModel.consumeError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_backup_section))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_backup_toggle),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(
                            if (state.backupAvailable) R.string.settings_backup_description
                            else R.string.settings_backup_unavailable
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.backupEnabled && state.backupAvailable,
                    onCheckedChange = viewModel::setBackupEnabled,
                    enabled = state.backupAvailable,
                )
            }

            Text(
                text = state.lastSyncAt?.let { millis ->
                    stringResource(
                        R.string.settings_sync_last,
                        Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .format(SyncStamp),
                    )
                } ?: stringResource(R.string.settings_sync_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.pendingCount > 0) {
                Text(
                    text = stringResource(R.string.settings_sync_pending, state.pendingCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { viewModel.syncNow(context) },
                enabled = state.backupAvailable && state.backupEnabled && !syncing,
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = stringResource(R.string.settings_sync_running),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Text(stringResource(R.string.settings_sync_now))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(stringResource(R.string.settings_about_section))
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}
