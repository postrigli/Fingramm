@file:OptIn(ExperimentalMaterial3Api::class)

package app.fingramm.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.data.repo.ImportMode
import app.fingramm.domain.backup.BackupError
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.InfoCard
import app.fingramm.ui.common.RadioOption
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var rejected by remember { mutableStateOf<BackupMessage.ImportRejected?>(null) }

    val exportJson =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let(viewModel::exportJson)
        }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::prepareImport)
    }

    val exportOk = stringResource(R.string.backup_export_ok)
    val exportFailed = stringResource(R.string.backup_export_failed)
    val importOk = stringResource(R.string.backup_import_ok)
    val readFailed = stringResource(R.string.backup_read_failed)
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            when (message) {
                BackupMessage.ExportOk -> snackbar.showSnackbar(exportOk)
                BackupMessage.ExportFailed -> snackbar.showSnackbar(exportFailed)
                BackupMessage.ImportOk -> snackbar.showSnackbar(importOk)
                BackupMessage.ReadFailed -> snackbar.showSnackbar(readFailed)
                is BackupMessage.ImportRejected -> rejected = message
            }
        }
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.backup_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ScreenColumn(Modifier.padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            InfoCard { Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodyMedium) }

            SectionTitle(stringResource(R.string.backup_export_section))
            OutlinedButton(
                onClick = { exportJson.launch(viewModel.suggestedJsonName()) },
                enabled = state.hasData && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_export_json)) }
            OutlinedButton(
                onClick = { exportCsv.launch(viewModel.suggestedCsvName()) },
                enabled = state.hasData && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_export_csv)) }
            Text(stringResource(R.string.backup_csv_hint), style = MaterialTheme.typography.bodySmall)

            SectionTitle(stringResource(R.string.backup_import_section))
            OutlinedButton(
                onClick = { importPicker.launch(arrayOf("*/*")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_import)) }
        }
    }

    state.preview?.let { preview ->
        ImportDialog(
            preview = preview,
            hasCurrentData = state.hasData,
            onDismiss = viewModel::cancelImport,
            onConfirm = viewModel::confirmImport,
        )
    }
    rejected?.let { problem ->
        AlertDialog(
            onDismissRequest = { rejected = null },
            title = { Text(stringResource(R.string.backup_rejected_title)) },
            text = {
                Column {
                    Text(stringResource(rejectionText(problem.error)))
                    problem.details.take(MAX_DETAILS).forEach {
                        Text(
                            "• $it",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { rejected = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

private fun rejectionText(error: BackupError): Int = when (error) {
    BackupError.MALFORMED -> R.string.backup_rejected_malformed
    BackupError.NOT_A_BACKUP -> R.string.backup_rejected_not_backup
    BackupError.NEWER_VERSION -> R.string.backup_rejected_newer
    BackupError.INVALID_DATA -> R.string.backup_rejected_invalid
}

@Composable
private fun ImportDialog(
    preview: ImportPreview,
    hasCurrentData: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ImportMode) -> Unit,
) {
    // По умолчанию — замена (с предупреждением); если данных ещё нет, слияние не имеет смысла.
    var mode by remember { mutableStateOf(ImportMode.REPLACE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.backup_import_summary,
                        preview.transactions,
                        preview.categories,
                        preview.goals,
                    ),
                )
                if (hasCurrentData) {
                    RadioOption(
                        selected = mode == ImportMode.REPLACE,
                        onSelect = { mode = ImportMode.REPLACE },
                        label = stringResource(R.string.backup_mode_replace),
                        description = stringResource(R.string.backup_mode_replace_hint),
                    )
                    RadioOption(
                        selected = mode == ImportMode.MERGE,
                        onSelect = { mode = ImportMode.MERGE },
                        label = stringResource(R.string.backup_mode_merge),
                        description = stringResource(R.string.backup_mode_merge_hint),
                    )
                    if (mode == ImportMode.REPLACE) {
                        Text(
                            stringResource(R.string.backup_replace_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (hasCurrentData) mode else ImportMode.REPLACE) }) {
                Text(stringResource(R.string.backup_import_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_DETAILS = 5
