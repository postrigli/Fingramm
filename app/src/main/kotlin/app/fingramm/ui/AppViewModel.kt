package app.fingramm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Состояние приложения верхнего уровня: куда вести пользователя, какая валюта, заблокировано ли. */
@HiltViewModel
class AppViewModel @Inject constructor(
    budgetRepository: BudgetRepository,
    settings: SettingsRepository,
) : ViewModel() {
    /** Стартовый маршрут определяется один раз по первому прочитанному состоянию; null — ещё читаем. */
    val startRoute: StateFlow<String?> = flow {
        emit(if (budgetRepository.profile.first() != null) Routes.DASHBOARD else Routes.ONBOARDING)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Есть ли профиль (пройден ли онбординг); null — ещё читаем. */
    val hasProfile: StateFlow<Boolean?> =
        budgetRepository.profile.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currency: StateFlow<CurrencyCode> = budgetRepository.profile
        .map { it?.currency ?: CurrencyCode.RUB }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CurrencyCode.RUB)

    private val unlocked = MutableStateFlow(false)

    /** Нужно ли показать экран блокировки; null — настройки ещё читаются. */
    val locked: StateFlow<Boolean?> = combine(settings.settings.map { it.lockEnabled }, unlocked) { enabled, open ->
        enabled && !open
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun unlock() {
        unlocked.value = true
    }

    /** Приложение ушло в фон — при следующем открытии снова потребуется подтверждение. */
    fun onBackgrounded() {
        unlocked.value = false
    }
}
