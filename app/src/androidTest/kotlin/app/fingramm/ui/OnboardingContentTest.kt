package app.fingramm.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fingramm.R
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.ui.onboarding.IncomeFrequency
import app.fingramm.ui.onboarding.IncomeKind
import app.fingramm.ui.onboarding.OnboardingContent
import app.fingramm.ui.onboarding.OnboardingUiState
import app.fingramm.ui.theme.FingrammTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI-тесты экрана первого запуска: он получает состояние снаружи, без Hilt и БД. */
@RunWith(AndroidJUnit4::class)
class OnboardingContentTest {
    @get:Rule
    val rule = createComposeRule()

    private var latest = OnboardingUiState()
    private var started = 0
    private var permissionAsked = 0

    private fun str(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun launch(initial: OnboardingUiState = OnboardingUiState()) {
        rule.setContent {
            FingrammTheme(dynamicColor = false) {
                var state by remember { mutableStateOf(initial) }
                latest = state
                OnboardingContent(
                    state = state,
                    onUpdate = { transform -> state = transform(state) },
                    onStart = {
                        started++
                        state = state.copy(showErrors = !state.valid)
                    },
                    onRestore = {},
                    requestPermission = { onResult ->
                        permissionAsked++
                        onResult(true)
                    },
                )
                if (started > 0) Text(STARTED)
            }
        }
    }

    private fun click(label: String) = rule.onNodeWithText(label).performScrollTo().performClick()

    /** Кнопка «Начать» закреплена внизу экрана и не лежит в прокручиваемой области. */
    private fun clickStart() = rule.onNodeWithText(str(R.string.action_start)).performClick()

    @Test
    fun screenExplainsPrivacyAndOffersTheWholeSetupOnOnePage() {
        launch()
        rule.onNodeWithText(str(R.string.onboarding_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.survey_welcome_privacy)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.onboarding_recurring_title)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.onboarding_evening_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun currencyCanBeChosen() {
        launch()
        click("${CurrencyCode.USD.code} ${CurrencyCode.USD.symbol}")
        rule.runOnIdle { assertEquals(CurrencyCode.USD, latest.currency) }
    }

    @Test
    fun tickingAPaymentRevealsItsFields() {
        launch()
        rule.onNodeWithText(str(R.string.onboarding_day)).assertDoesNotExist()
        click(str(R.string.starter_rent))
        rule.runOnIdle { assertTrue(latest.rows.first().enabled) }
        rule.onNodeWithText(str(R.string.onboarding_day)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aTickedPaymentWithoutAmountBlocksTheStart() {
        launch()
        click(str(R.string.starter_rent))
        clickStart()
        rule.runOnIdle {
            assertTrue(latest.showErrors)
            assertFalse(latest.valid)
        }
    }

    @Test
    fun startWithoutPaymentsIsAllowedAndAsksForNotificationPermissionBecauseOfTheEveningReminder() {
        launch()
        clickStart()
        rule.runOnIdle {
            assertEquals(1, started)
            assertEquals(1, permissionAsked)
            assertTrue(latest.valid)
        }
    }

    @Test
    fun startWithoutAnyNotificationsDoesNotAskForPermission() {
        launch(OnboardingUiState(eveningReminder = false))
        clickStart()
        rule.runOnIdle {
            assertEquals(1, started)
            assertEquals(0, permissionAsked)
        }
    }

    @Test
    fun balanceHelpExplainsHowToCountTheMoney() {
        launch()
        rule.onNodeWithText(str(R.string.onboarding_balance_title)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.balance_help_cd)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.balance_help_text)).assertIsDisplayed()
    }

    @Test
    fun theBalanceCanBeMarkedAsNegative() {
        launch(OnboardingUiState(balanceMinor = 150_000))
        click(str(R.string.balance_negative))
        rule.runOnIdle { assertEquals(-150_000L, latest.balanceMinor) }
    }

    @Test
    fun incomeOffersMonthlyTwiceMonthlyAndWeeklyPayments() {
        launch()
        click(str(R.string.income_primary))
        rule.runOnIdle { assertTrue(latest.primaryIncome.enabled) }
        click(str(R.string.income_freq_twice))
        rule.runOnIdle { assertEquals(IncomeFrequency.TWICE_MONTHLY, latest.primaryIncome.frequency) }
        rule.onNodeWithText(str(R.string.income_second_day)).performScrollTo().assertIsDisplayed()
        click(str(R.string.income_freq_week))
        rule.runOnIdle { assertEquals(IncomeFrequency.WEEKLY, latest.primaryIncome.frequency) }
    }

    @Test
    fun aSecondIncomeCanBeAddedAndRemoved() {
        launch()
        click(str(R.string.income_add_second))
        rule.runOnIdle { assertEquals(IncomeKind.RENT, latest.secondIncome?.kind) }
        rule.onNodeWithText(str(R.string.income_second_title)).performScrollTo().assertIsDisplayed()
        click(str(R.string.income_remove))
        rule.runOnIdle { assertEquals(null, latest.secondIncome) }
    }

    @Test
    fun anIncomeWithoutAmountBlocksTheStart() {
        launch()
        click(str(R.string.income_primary))
        clickStart()
        rule.runOnIdle {
            assertTrue(latest.showErrors)
            assertFalse(latest.valid)
        }
    }

    private companion object {
        const val STARTED = "started"
    }
}
