package app.fingramm.ui.more

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.RecurringRepository
import app.fingramm.domain.model.AccountType
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.recurring.RecurringSchedule
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
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class RecurringUiState(
    val templates: List<RecurringTemplate> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val currency: CurrencyCode = CurrencyCode.RUB,
)

@HiltViewModel
class RecurringListViewModel @Inject constructor(
    repository: RecurringRepository,
    categoryRepository: CategoryRepository,
    budgetRepository: BudgetRepository,
) : ViewModel() {
    val state: StateFlow<RecurringUiState> = combine(
        repository.templates,
        categoryRepository.categories,
        budgetRepository.profile,
    ) { templates, categories, profile ->
        RecurringUiState(templates, categories.associateBy { it.id }, profile?.currency ?: CurrencyCode.RUB)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecurringUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class RecurringFormState(
    val loaded: Boolean = false,
    val editingId: Long? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountMinor: Long? = null,
    val categoryId: Long? = null,
    val dayOfMonth: Int? = null,
    val note: String = "",
    val accountType: AccountType? = null,
    val active: Boolean = true,
    val categories: List<Category> = emptyList(),
    val showErrors: Boolean = false,
) {
    val amountError: Boolean get() = showErrors && (amountMinor ?: 0L) <= 0L
    val categoryError: Boolean get() = showErrors && categoryId == null
    val dayError: Boolean get() = showErrors && (dayOfMonth == null || dayOfMonth !in 1..MAX_DAY)

    companion object {
        const val MAX_DAY = 31
    }
}

@HiltViewModel
class RecurringEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecurringRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val _state = MutableStateFlow(RecurringFormState(editingId = id))
    val state: StateFlow<RecurringFormState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    private var original: RecurringTemplate? = null
    private var allCategories: List<Category> = emptyList()

    init {
        viewModelScope.launch {
            allCategories = categoryRepository.categories.first()
            val template = id?.let { repository.get(it) }
            original = template
            val type = template?.type ?: TransactionType.EXPENSE
            _state.update {
                it.copy(
                    loaded = true,
                    type = type,
                    amountMinor = template?.amountMinor,
                    categoryId = template?.categoryId,
                    dayOfMonth = template?.dayOfMonth,
                    note = template?.note.orEmpty(),
                    accountType = template?.accountType,
                    active = template?.active ?: true,
                    categories = visible(type, template?.categoryId),
                )
            }
        }
    }

    fun update(transform: (RecurringFormState) -> RecurringFormState) = _state.update(transform)

    fun setType(type: TransactionType) = _state.update {
        val keep = it.categoryId?.takeIf { c -> allCategories.firstOrNull { cat -> cat.id == c }?.type == type }
        it.copy(type = type, categoryId = keep, categories = visible(type, keep))
    }

    fun save() {
        val s = _state.value
        val amount = s.amountMinor
        val category = s.categoryId
        val day = s.dayOfMonth
        if (amount == null || amount <= 0 || category == null || day == null || day !in 1..RecurringFormState.MAX_DAY) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            val base = original
            val today = LocalDate.now(clock)
            // День изменился или шаблон новый — ближайшее списание пересчитывается от сегодняшней даты.
            val next = if (base != null && base.dayOfMonth == day) {
                base.nextDueDate
            } else {
                RecurringSchedule.firstDueOnOrAfter(today, day)
            }
            repository.save(
                RecurringTemplate(
                    id = base?.id ?: 0,
                    type = s.type,
                    amountMinor = amount,
                    categoryId = category,
                    note = s.note.trim(),
                    accountType = s.accountType,
                    dayOfMonth = day,
                    nextDueDate = next,
                    active = s.active,
                ),
            )
            _done.send(Unit)
        }
    }

    fun delete() {
        val templateId = original?.id ?: return
        viewModelScope.launch {
            repository.delete(templateId)
            _done.send(Unit)
        }
    }

    private fun visible(type: TransactionType, selected: Long?): List<Category> =
        allCategories.filter { it.type == type && (!it.archived || it.id == selected) }
}
