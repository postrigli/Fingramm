package app.fingramm.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.data.repo.TransactionRepository
import app.fingramm.domain.model.AccountType
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class TransactionFormState(
    val loaded: Boolean = false,
    val editingId: Long? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountMinor: Long? = null,
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val accountType: AccountType? = null,
    val categories: List<Category> = emptyList(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
) {
    val amountError: Boolean get() = showErrors && (amountMinor ?: 0L) <= 0L
    val categoryError: Boolean get() = showErrors && categoryId == null
}

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val statsRepository: StatsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }

    private val _state = MutableStateFlow(TransactionFormState(date = LocalDate.now(clock), editingId = id))
    val state: StateFlow<TransactionFormState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    private var original: Transaction? = null
    private var allCategories: List<Category> = emptyList()
    private var usage: Map<Long, Int> = emptyMap()

    init {
        viewModelScope.launch {
            allCategories = categoryRepository.categories.first()
            val existing = id?.let { transactions.get(it) }
            original = existing
            val type = existing?.type ?: TransactionType.EXPENSE
            usage = statsRepository.categoryUsage(type).first()
            _state.update {
                it.copy(
                    loaded = true,
                    type = type,
                    amountMinor = existing?.amountMinor,
                    categoryId = existing?.categoryId,
                    date = existing?.date ?: it.date,
                    note = existing?.note.orEmpty(),
                    accountType = existing?.accountType,
                    categories = visibleCategories(type, existing?.categoryId),
                )
            }
        }
    }

    fun setType(type: TransactionType) {
        viewModelScope.launch {
            usage = statsRepository.categoryUsage(type).first()
            _state.update {
                val keep = it.categoryId?.takeIf {
                        selected ->
                    allCategories.firstOrNull { c -> c.id == selected }?.type == type
                }
                it.copy(type = type, categoryId = keep, categories = visibleCategories(type, keep))
            }
        }
    }

    fun setAmount(value: Long?) = _state.update { it.copy(amountMinor = value) }

    fun setCategory(categoryId: Long) = _state.update { it.copy(categoryId = categoryId) }

    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun setAccount(account: AccountType?) = _state.update { it.copy(accountType = account) }

    fun save() {
        val current = _state.value
        val amount = current.amountMinor
        val categoryId = current.categoryId
        if (amount == null || amount <= 0 || categoryId == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val base = original
            val transaction = Transaction(
                id = base?.id ?: 0,
                type = current.type,
                amountMinor = amount,
                categoryId = categoryId,
                date = current.date,
                note = current.note.trim(),
                accountType = current.accountType,
                memberId = base?.memberId,
                recurringId = base?.recurringId,
                createdAt = base?.createdAt ?: 0,
            )
            if (base == null) {
                transactions.add(
                    transaction,
                    System.currentTimeMillis(),
                )
            } else {
                transactions.update(transaction)
            }
            _done.send(Unit)
        }
    }

    fun delete() {
        val base = original ?: return
        viewModelScope.launch {
            transactions.delete(base.id)
            _done.send(Unit)
        }
    }

    /** Категории выбранного типа: активные, самые частые — первыми; выбранная архивная остаётся доступной. */
    private fun visibleCategories(type: TransactionType, selectedId: Long?): List<Category> =
        allCategories
            .filter { it.type == type && (!it.archived || it.id == selectedId) }
            .sortedWith(compareByDescending<Category> { usage[it.id] ?: 0 }.thenBy { it.name.lowercase() })
}
