package app.fingramm.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fingramm.R
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.money.Money
import app.fingramm.domain.survey.DraftAmount
import app.fingramm.domain.survey.ExpenseDraft
import app.fingramm.domain.survey.SurveyDraft
import app.fingramm.domain.survey.SurveyStep
import app.fingramm.domain.survey.SurveyWizard
import app.fingramm.domain.survey.WizardAdvance
import app.fingramm.domain.survey.WizardState
import app.fingramm.ui.onboarding.OnboardingContent
import app.fingramm.ui.theme.FingrammTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Базовые UI-тесты онбординга: экран получает ту же логику переходов, что и в приложении, без Hilt и БД. */
@RunWith(AndroidJUnit4::class)
class OnboardingContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 10)
    private var latest = WizardState()
    private var finishedDraft: SurveyDraft? = null

    private fun str(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun launch(initial: WizardState = WizardState()) {
        rule.setContent {
            FingrammTheme(dynamicColor = false) {
                var wizard by remember { mutableStateOf(initial) }
                var finished by remember { mutableStateOf(false) }
                latest = wizard
                OnboardingContent(
                    wizard = wizard,
                    saving = false,
                    today = today,
                    onUpdate = { transform -> wizard = wizard.copy(draft = transform(wizard.draft)) },
                    onBack = { wizard = SurveyWizard.back(wizard) },
                    onNext = { wizard = advance(SurveyWizard.next(wizard, today)) { finished = true } },
                    onSkip = { wizard = advance(SurveyWizard.skip(wizard, today)) { finished = true } },
                    onRestore = {},
                    requestPermission = { it(true) },
                )
                if (finished) Text(FINISHED)
            }
        }
    }

    private fun advance(result: WizardAdvance, onFinish: () -> Unit): WizardState = when (result) {
        is WizardAdvance.Moved -> result.state
        is WizardAdvance.Finish -> {
            finishedDraft = result.draft
            onFinish()
            latest
        }
    }

    private fun click(label: String) = rule.onNodeWithText(label).performClick()

    private fun type(label: String, text: String) = rule.onNodeWithText(label).performTextInput(text)

    @Test
    fun welcomeScreenExplainsPrivacyAndStartsTheSurvey() {
        launch()
        rule.onNodeWithText(str(R.string.survey_welcome_privacy)).assertIsDisplayed()
        click(str(R.string.action_start))
        rule.onNodeWithText(str(R.string.survey_title_family)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.survey_progress, 1, 10)).assertIsDisplayed()
    }

    @Test
    fun currencyCanBeChosenOnTheFirstScreen() {
        launch()
        click("${CurrencyCode.USD.symbol} ${CurrencyCode.USD.code}")
        rule.runOnIdle { assertEquals(CurrencyCode.USD, latest.draft.currency) }
    }

    @Test
    fun requiredStepShowsErrorsAndDoesNotAdvance() {
        launch(WizardState(step = SurveyStep.FAMILY))
        click(str(R.string.action_next))
        // Пустые поля: «размер семьи» и «есть ли дети» — оба сообщают «Заполните поле».
        rule.onAllNodesWithText(str(R.string.error_required)).onFirst().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.survey_title_family)).assertIsDisplayed()
    }

    @Test
    fun skipIsNotOfferedOnRequiredSteps() {
        launch(WizardState(step = SurveyStep.INCOME))
        rule.onNodeWithText(str(R.string.action_skip)).assertDoesNotExist()
    }

    @Test
    fun skipIsOfferedOnOptionalSteps() {
        launch(WizardState(step = SurveyStep.ASSETS))
        rule.onNodeWithText(str(R.string.action_skip)).assertIsDisplayed()
    }

    @Test
    fun backReturnsToThePreviousStepKeepingAnswers() {
        launch(WizardState(SurveyDraft(familySize = 4, hasChildren = false), SurveyStep.INCOME))
        click(str(R.string.action_back))
        rule.onNodeWithText(str(R.string.survey_title_family)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(4, latest.draft.familySize) }
    }

    @Test
    fun moneyFieldGroupsThousandsWhileTyping() {
        launch(WizardState(step = SurveyStep.INCOME))
        type(str(R.string.survey_per_month), "1234567")
        rule.runOnIdle {
            assertEquals(123_456_700L, latest.draft.incomeSources.first().amountMinor)
        }
        rule.onNodeWithText(Money.formatPlain(123_456_700L)).assertIsDisplayed()
    }

    @Test
    fun fullSurveyWithSkippedOptionalStepsFinishes() {
        launch()
        click(str(R.string.action_start))

        type(str(R.string.survey_family_size), "3")
        click(str(R.string.no))
        click(str(R.string.action_next))

        type(str(R.string.survey_per_month), "100000")
        click(str(R.string.action_next))

        type(str(R.string.survey_per_month), "40000")
        click(str(R.string.action_next))

        type(str(R.string.survey_per_month), "20000")
        click(str(R.string.action_next))

        click(str(R.string.no)) // долгов нет
        click(str(R.string.action_next))

        click(str(R.string.no)) // подушки нет
        click(str(R.string.action_next))

        repeat(OPTIONAL_STEPS) { click(str(R.string.action_skip)) }

        rule.onNodeWithText(FINISHED).assertIsDisplayed()
        rule.runOnIdle {
            val draft = checkNotNull(finishedDraft)
            assertEquals(3, draft.familySize)
            assertEquals(10_000_000L, draft.incomeSources.first().amountMinor)
            assertEquals(4_000_000L, draft.mandatory.totalMinor)
            assertEquals(2_000_000L, draft.variable.totalMinor)
            assertEquals(false, draft.hasDebts)
        }
    }

    @Test
    fun variableExpensesCanBeAnsweredWithDontKnow() {
        val draft = SurveyDraft(mandatory = ExpenseDraft(totalMinor = 1), incomeSources = listOf(DraftAmount("", 1)))
        launch(WizardState(draft, SurveyStep.VARIABLE))
        click(str(R.string.survey_variable_unknown))
        click(str(R.string.action_next))
        rule.onNodeWithText(str(R.string.survey_title_debts)).assertIsDisplayed()
    }

    private companion object {
        const val FINISHED = "FINISHED_MARKER"
        const val OPTIONAL_STEPS = 4
    }
}
