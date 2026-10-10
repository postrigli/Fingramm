@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.fingramm.ui.more

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.ReminderMode
import app.fingramm.security.AppLock
import app.fingramm.security.findFragmentActivity
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.ConfirmDialog
import app.fingramm.ui.common.InfoCard
import app.fingramm.ui.common.RadioOption
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle
import app.fingramm.ui.common.SwitchRow
import app.fingramm.ui.common.TimePickerDialogHost
import app.fingramm.ui.common.formatTime
import app.fingramm.ui.common.notificationsAllowed
import app.fingramm.ui.common.rememberNotificationPermissionRequest
import app.fingramm.ui.common.weekdayName
import app.fingramm.ui.survey.reminderModeLabel

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenBackup: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestPermission = rememberNotificationPermissionRequest()
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed(context)
        onPauseOrDispose { }
    }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var lockError by remember { mutableStateOf(false) }
    val reminder = settings.reminder

    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        ScreenColumn(Modifier.padding(padding)) {
            SectionTitle(stringResource(R.string.settings_reminders))
            Text(stringResource(R.string.settings_reminders_hint), style = MaterialTheme.typography.bodySmall)
            ReminderMode.entries.forEach { mode ->
                RadioOption(
                    selected = reminder.mode == mode,
                    onSelect = {
                        viewModel.setMode(mode)
                        if (mode != ReminderMode.OFF) requestPermission { granted -> allowed = granted }
                    },
                    label = stringResource(reminderModeLabel(mode)),
                )
            }
            if (reminder.mode != ReminderMode.OFF) {
                if (reminder.mode == ReminderMode.WEEKLY) {
                    Text(stringResource(R.string.settings_weekday), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (day in 1..WEEK_DAYS) {
                            FilterChip(
                                selected = reminder.weekday == day,
                                onClick = { viewModel.setWeekday(day) },
                                label = { Text(weekdayName(day)) },
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_time, formatTime(reminder.hour, reminder.minute)))
                }
                if (!allowed) {
                    InfoCard {
                        Text(
                            stringResource(R.string.settings_notifications_off),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            onClick = {
                                requestPermission { granted ->
                                    allowed = granted
                                    if (!granted) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.settings_notifications_allow)) }
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_security))
            val lockAvailable = remember { AppLock.isAvailable(context) }
            SwitchRow(
                checked = settings.lockEnabled,
                onCheckedChange = { enable ->
                    if (!enable) {
                        viewModel.setLockEnabled(false)
                    } else {
                        val activity = context.findFragmentActivity()
                        if (activity == null || !lockAvailable) {
                            lockError = true
                        } else {
                            // Включаем блокировку только после успешного подтверждения: так нельзя запереть себя.
                            AppLock.authenticate(
                                activity,
                                title = context.getString(R.string.lock_enable_title),
                                subtitle = null,
                                onSuccess = { viewModel.setLockEnabled(true) },
                                onFailure = { lockError = true },
                            )
                        }
                    }
                },
                label = stringResource(R.string.settings_lock),
                description = stringResource(
                    if (lockAvailable) R.string.settings_lock_hint else R.string.settings_lock_unavailable,
                ),
            )
            if (lockError) {
                Text(
                    stringResource(R.string.settings_lock_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionTitle(stringResource(R.string.settings_data))
            OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_title))
            }
            Button(
                onClick = { confirmWipe = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_wipe)) }
        }
    }

    if (pickingTime) {
        TimePickerDialogHost(
            hour = reminder.hour,
            minute = reminder.minute,
            onDismiss = { pickingTime = false },
            onConfirm = { hour, minute ->
                viewModel.setTime(hour, minute)
                pickingTime = false
            },
        )
    }
    if (confirmWipe) {
        ConfirmDialog(
            title = stringResource(R.string.settings_wipe_title),
            message = stringResource(R.string.settings_wipe_message),
            confirmLabel = stringResource(R.string.settings_wipe_confirm),
            onConfirm = {
                confirmWipe = false
                viewModel.wipeAll()
            },
            onDismiss = { confirmWipe = false },
        )
    }
}

private const val WEEK_DAYS = 7
