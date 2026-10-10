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
import app.fingramm.domain.survey.SurveyStep
import app.fingramm.ui.common.PrimaryButton
import app.fingramm.ui.common.rememberNotificationPermissionRequest
import app.fingramm.ui.survey.SurveyStepContent

/** Мастер первого запуска: по одному вопросу на экран, с прогрессом, возвратом и пропуском необязательного. */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onRestore: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.finished.collect { onFinished() } }

    val requestPermission = rememberNotificationPermissionRequest()
    val steps = SurveyStep.entries
    val index = steps.indexOf(state.step)
    val questionCount = steps.lastIndex
    BackHandler(enabled = index > 0) { viewModel.back() }
    val errors = if (state.showErrors) state.errorsFor(state.step, viewModel.today) else emptyList()

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            if (index > 0) {
                val label = stringResource(R.string.survey_progress, index, questionCount)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(
                        progress = { index.toFloat() / questionCount },
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
                        TextButton(onClick = viewModel::back) { Text(stringResource(R.string.action_back)) }
                    }
                    if (state.step.optional) {
                        TextButton(onClick = viewModel::skip) { Text(stringResource(R.string.action_skip)) }
                    }
                    val nextLabel = when (state.step) {
                        SurveyStep.WELCOME -> R.string.action_start
                        SurveyStep.HABITS -> R.string.action_finish
                        else -> R.string.action_next
                    }
                    PrimaryButton(
                        text = stringResource(nextLabel),
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val mode = state.draft.reminderMode ?: ReminderDefaults.suggested(state.draft.timeCommitment)
                            if (state.step == SurveyStep.HABITS && mode != ReminderMode.OFF) {
                                requestPermission { viewModel.next() }
                            } else {
                                viewModel.next()
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
                step = state.step,
                draft = state.draft,
                errors = errors,
                onChange = viewModel::update,
                onReminderPicked = { mode -> if (mode != ReminderMode.OFF) requestPermission { } },
            )
            if (state.step == SurveyStep.WELCOME) {
                TextButton(onClick = onRestore, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.onboarding_restore))
                }
            }
        }
    }
}
