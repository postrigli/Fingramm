package app.fingramm.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fingramm.R
import app.fingramm.domain.model.CurrencyCode
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
        click(str(R.string.action_start))
        rule.runOnIdle {
            assertTrue(latest.showErrors)
            assertFalse(latest.valid)
        }
    }

    @Test
    fun startWithoutPaymentsIsAllowedAndAsksForNotificationPermissionBecauseOfTheEveningReminder() {
        launch()
        click(str(R.string.action_start))
        rule.runOnIdle {
            assertEquals(1, started)
            assertEquals(1, permissionAsked)
            assertTrue(latest.valid)
        }
    }

    @Test
    fun startWithoutAnyNotificationsDoesNotAskForPermission() {
        launch(OnboardingUiState(eveningReminder = false))
        click(str(R.string.action_start))
        rule.runOnIdle {
            assertEquals(1, started)
            assertEquals(0, permissionAsked)
        }
    }

    private companion object {
        const val STARTED = "started"
    }
}
