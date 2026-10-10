package app.fingramm.ui.more

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryKind
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val limits: Map<Long, Long> = emptyMap(),
    val currency: CurrencyCode = CurrencyCode.RUB,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    repository: CategoryRepository,
    budgetRepository: BudgetRepository,
) : ViewModel() {
    val state: StateFlow<CategoriesUiState> = combine(
        repository.categories,
        repository.limits,
        budgetRepository.profile,
    ) { categories, limits, profile ->
        CategoriesUiState(
            categories,
            limits.associate {
                it.categoryId to it.limitMinor
            },
            profile?.currency ?: CurrencyCode.RUB,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoriesUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class CategoryFormState(
    val loaded: Boolean = false,
    val editingId: Long? = null,
    val name: String = "",
    val icon: String = DEFAULT_ICON,
    val type: TransactionType = TransactionType.EXPENSE,
    val kind: CategoryKind = CategoryKind.VARIABLE,
    val isDebtPayment: Boolean = false,
    val archived: Boolean = false,
    val limitMinor: Long? = null,
    val showErrors: Boolean = false,
    val duplicateName: Boolean = false,
) {
    val nameError: Boolean get() = showErrors && name.isBlank()

    companion object {
        const val DEFAULT_ICON = "📦"
    }
}

@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CategoryRepository,
) : ViewModel() {
    private val id: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val initialType: TransactionType =
        savedStateHandle.get<String>("type")?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
            ?: TransactionType.EXPENSE

    private val _state = MutableStateFlow(CategoryFormState(editingId = id, type = initialType))
    val state: StateFlow<CategoryFormState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val category = id?.let { repository.get(it) }
            val limit = id?.let { target -> repository.limits.first().firstOrNull { it.categoryId == target } }
            _state.update {
                if (category == null) {
                    it.copy(loaded = true)
                } else {
                    it.copy(
                        loaded = true,
                        name = category.name,
                        icon = category.icon,
                        type = category.type,
                        kind = category.kind,
                        isDebtPayment = category.isDebtPayment,
                        archived = category.archived,
                        limitMinor = limit?.limitMinor,
                    )
                }
            }
        }
    }

    fun update(transform: (CategoryFormState) -> CategoryFormState) = _state.update(transform)

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            val existing = repository.categories.first()
            val duplicate = existing.any {
                it.id != id && it.type == s.type && it.name.trim().equals(s.name.trim(), ignoreCase = true)
            }
            if (duplicate) {
                _state.update { it.copy(duplicateName = true) }
                return@launch
            }
            val isExpense = s.type == TransactionType.EXPENSE
            repository.save(
                Category(
                    id = id ?: 0,
                    name = s.name.trim(),
                    type = s.type,
                    kind = if (isExpense) s.kind else CategoryKind.VARIABLE,
                    icon = s.icon.ifBlank { CategoryFormState.DEFAULT_ICON },
                    archived = s.archived,
                    isDebtPayment = isExpense && s.kind == CategoryKind.MANDATORY && s.isDebtPayment,
                ),
                limitMinor = s.limitMinor.takeIf { isExpense },
            )
            _done.send(Unit)
        }
    }
}

/** Палитра иконок категорий (эмодзи — без внешних ресурсов и с нативной поддержкой TalkBack). */
val CategoryEmojis: List<String> = listOf(
    "🛒", "🍎", "🥦", "🍞", "☕", "🍔", "🍕", "🏠", "💡", "🔥", "💧", "📱", "🌐", "🚌", "🚗", "⛽",
    "🚕", "✈️", "💊", "🦷", "👕", "👟", "🎬", "🎮", "📚", "🎓", "🧸", "🐶", "🎁", "💳", "🏦", "💼",
    "🛠", "🏢", "💰", "📦", "🔁", "🧴", "🏋️", "🎉",
)
