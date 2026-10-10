@file:OptIn(ExperimentalMaterial3Api::class)

package app.fingramm.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.R
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.survey.ReminderDefaults
import app.fingramm.domain.survey.SurveyDraft
import app.fingramm.domain.survey.SurveyStep
import app.fingramm.domain.survey.SurveyWizard
import app.fingramm.domain.survey.WizardState
import app.fingramm.ui.common.NotificationPermissionRequest
import app.fingramm.ui.common.PrimaryButton
import app.fingramm.ui.common.rememberNotificationPermissionRequest
import app.fingramm.ui.survey.SurveyStepContent
import java.time.LocalDate

/** Мастер первого запуска: по одному вопросу на экран, с прогрессом, возвратом и пропуском необязательного. */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onRestore: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.finished.collect { onFinished() } }
    OnboardingContent(
        wizard = ui.wizard,
        saving = ui.saving,
        today = viewModel.today,
        onUpdate = viewModel::update,
        onBack = viewModel::back,
        onNext = viewModel::next,
        onSkip = viewModel::skip,
        onRestore = onRestore,
        requestPermission = rememberNotificationPermissionRequest(),
    )
}

/** Без состояния и без Hilt: вся логика переходов приходит снаружи, поэтому экран тестируется отдельно. */
@Composable
fun OnboardingContent(
    wizard: WizardState,
    saving: Boolean,
    today: LocalDate,
    onUpdate: ((SurveyDraft) -> SurveyDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onRestore: () -> Unit,
    requestPermission: NotificationPermissionRequest,
) {
    val index = wizard.stepIndex
    BackHandler(enabled = index > 0, onBack = onBack)
    val errors = SurveyWizard.visibleErrors(wizard, today)

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            if (index > 0) {
                val label = stringResource(R.string.survey_progress, index, wizard.questionCount)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(
                        progress = { index.toFloat() / wizard.questionCount },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .semantics { contentDescription = label },
                    )
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index > 0) {
                        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                    }
                    if (wizard.step.optional) {
                        TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip)) }
                    }
                    val nextLabel = when (wizard.step) {
                        SurveyStep.WELCOME -> R.string.action_start
                        SurveyStep.HABITS -> R.string.action_finish
                        else -> R.string.action_next
                    }
                    PrimaryButton(
                        text = stringResource(nextLabel),
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val draft = wizard.draft
                            val mode = draft.reminderMode ?: ReminderDefaults.suggested(draft.timeCommitment)
                            if (wizard.step == SurveyStep.HABITS && mode != ReminderMode.OFF) {
                                // Разрешение запрашиваем в момент подтверждения режима; отказ не блокирует сохранение.
                                requestPermission { onNext() }
                            } else {
                                onNext()
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SurveyStepContent(
                step = wizard.step,
                draft = wizard.draft,
                errors = errors,
                onChange = onUpdate,
                onReminderPicked = { mode -> if (mode != ReminderMode.OFF) requestPermission { } },
            )
            if (wizard.step == SurveyStep.WELCOME) {
                TextButton(onClick = onRestore, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.onboarding_restore))
                }
            }
        }
    }
}
