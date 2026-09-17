package com.extrairapk.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.extrairapk.app.R
import com.extrairapk.app.data.ExtractionState
import com.extrairapk.app.data.InstalledAppInfo
import com.extrairapk.app.util.FormatUtils

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(
    app: InstalledAppInfo,
    extractionState: ExtractionState,
    onDismiss: () -> Unit,
    onExtract: () -> Unit,
    onShare: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onInstall: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(icon = app.icon, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            DetailRow(stringResource(R.string.detail_version), app.versionName ?: "-")
            DetailRow(stringResource(R.string.detail_version_code), app.versionCode.toString())
            DetailRow(stringResource(R.string.detail_size), FormatUtils.formatBytes(app.totalApkBytes))
            DetailRow(stringResource(R.string.detail_installed_at), FormatUtils.formatDate(app.installedAt))
            DetailRow(stringResource(R.string.detail_updated_at), FormatUtils.formatDate(app.updatedAt))
            DetailRow(
                stringResource(R.string.detail_type),
                stringResource(if (app.isUserApp) R.string.type_user else R.string.type_system),
            )
            if (app.hasSplits) {
                DetailRow(
                    stringResource(R.string.detail_splits),
                    stringResource(R.string.splits_count, app.splitSourceDirs.size),
                )
            }

            Spacer(Modifier.height(20.dp))

            when (extractionState) {
                is ExtractionState.InProgress -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.extracting))
                }

                is ExtractionState.Done -> Column {
                    Text(
                        stringResource(R.string.extract_ready),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_share))
                        }
                        OutlinedButton(onClick = onSaveToDownloads, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_save))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.InstallMobile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_install))
                    }
                }

                is ExtractionState.Error -> Column {
                    Text(extractionState.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onExtract, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_retry))
                    }
                }

                ExtractionState.Idle -> Button(onClick = onExtract, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_extract))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
