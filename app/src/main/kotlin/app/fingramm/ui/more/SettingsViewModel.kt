package app.fingramm.ui.more

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fingramm.R
import app.fingramm.data.repo.AppSettings
import app.fingramm.data.repo.BackupRepository
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.ImportMode
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.domain.backup.BackupCodec
import app.fingramm.domain.backup.BackupData
import app.fingramm.domain.backup.BackupDecodeResult
import app.fingramm.domain.backup.BackupError
import app.fingramm.domain.backup.BackupFile
import app.fingramm.domain.backup.CsvExporter
import app.fingramm.domain.backup.CsvHeaders
import app.fingramm.domain.backup.CsvLabels
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.ReminderSettings
import app.fingramm.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val backup: BackupRepository,
) : ViewModel() {
    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    fun setMode(mode: ReminderMode) = updateReminder { it.copy(mode = mode) }

    fun setTime(hour: Int, minute: Int) = updateReminder { it.copy(hour = hour, minute = minute) }

    fun setWeekday(weekday: Int) = updateReminder { it.copy(weekday = weekday) }

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLockEnabled(enabled) }
    }

    /** Полное удаление данных. После него приложение вернётся к онбордингу. */
    fun wipeAll() {
        viewModelScope.launch {
            backup.wipe()
            scheduler.apply(ReminderSettings())
        }
    }

    private fun updateReminder(transform: (ReminderSettings) -> ReminderSettings) {
        viewModelScope.launch {
            val updated = transform(settings.current().reminder)
            settings.setReminder(updated)
            scheduler.apply(updated)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Сводка импортируемого файла для окна подтверждения. */
data class ImportPreview(
    val data: BackupData,
    val transactions: Int,
    val categories: Int,
    val goals: Int,
    val hasProfile: Boolean,
)

sealed interface BackupMessage {
    data object ExportOk : BackupMessage

    data object ExportFailed : BackupMessage

    data object ImportOk : BackupMessage

    data class ImportRejected(val error: BackupError, val details: List<String>) : BackupMessage

    data object ReadFailed : BackupMessage
}

data class BackupUiState(
    val busy: Boolean = false,
    val preview: ImportPreview? = null,
    val hasData: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupRepository,
    private val budgetRepository: BudgetRepository,
    private val scheduler: ReminderScheduler,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _messages = Channel<BackupMessage>(Channel.BUFFERED)
    val messages: Flow<BackupMessage> = _messages.receiveAsFlow()

    private val resolver: ContentResolver get() = context.contentResolver

    init {
        viewModelScope.launch {
            budgetRepository.profile.collect {
                    p ->
                _state.update { it.copy(hasData = p != null) }
            }
        }
    }

    fun suggestedJsonName(): String = "fingramm-backup-${LocalDate.now(clock)}.json"

    fun suggestedCsvName(): String = "fingramm-operations-${LocalDate.now(clock)}.csv"

    fun exportJson(uri: Uri) = runIo(BackupMessage.ExportFailed) {
        val data = backup.snapshot()
        val text = BackupCodec.encode(BackupFile(exportedAt = Instant.now(clock).toString(), data = data))
        write(uri, text)
        BackupMessage.ExportOk
    }

    fun exportCsv(uri: Uri) = runIo(BackupMessage.ExportFailed) {
        val data = backup.snapshot()
        val currency = data.profile?.currency ?: CurrencyCode.RUB
        val text = CsvExporter.export(
            data.transactions,
            data.categories,
            currency,
            headers = CsvHeaders(
                date = context.getString(R.string.csv_date),
                type = context.getString(R.string.csv_type),
                amount = context.getString(R.string.csv_amount),
                currency = context.getString(R.string.csv_currency),
                category = context.getString(R.string.csv_category),
                kind = context.getString(R.string.csv_kind),
                account = context.getString(R.string.csv_account),
                note = context.getString(R.string.csv_note),
            ),
            labels = CsvLabels(
                income = context.getString(R.string.income),
                expense = context.getString(R.string.expense),
                mandatory = context.getString(R.string.category_kind_mandatory),
                variable = context.getString(R.string.category_kind_variable),
                cash = context.getString(R.string.account_cash),
                card = context.getString(R.string.account_card),
                savings = context.getString(R.string.account_savings),
            ),
        )
        write(uri, text)
        BackupMessage.ExportOk
    }

    /** Читает и проверяет файл; ничего не меняет, пока пользователь не подтвердит. */
    fun prepareImport(uri: Uri) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                val text = withContext(Dispatchers.IO) { read(uri) }
                when (val result = BackupCodec.decode(text)) {
                    is BackupDecodeResult.Failure -> BackupMessage.ImportRejected(result.error, result.details)
                    is BackupDecodeResult.Success -> {
                        val data = result.file.data
                        _state.update {
                            it.copy(
                                preview = ImportPreview(
                                    data,
                                    data.transactions.size,
                                    data.categories.size,
                                    data.goals.size,
                                    data.profile != null,
                                ),
                            )
                        }
                        null
                    }
                }
            } catch (ignored: IOException) {
                BackupMessage.ReadFailed
            }
            _state.update { it.copy(busy = false) }
            message?.let { _messages.send(it) }
        }
    }

    fun cancelImport() = _state.update { it.copy(preview = null) }

    fun confirmImport(mode: ImportMode) {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(preview = null, busy = true) }
        viewModelScope.launch {
            backup.importData(preview.data, mode)
            scheduler.apply(settings.current().reminder)
            _state.update { it.copy(busy = false) }
            _messages.send(BackupMessage.ImportOk)
        }
    }

    private fun runIo(failure: BackupMessage, block: suspend () -> BackupMessage) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) { block() }
            } catch (ignored: IOException) {
                failure
            }
            _state.update { it.copy(busy = false) }
            _messages.send(message)
        }
    }

    private fun write(uri: Uri, text: String) {
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("openOutputStream returned null")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    private fun read(uri: Uri): String {
        val stream = resolver.openInputStream(uri) ?: throw IOException("openInputStream returned null")
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(READ_CHUNK_BYTES)
            var total = 0
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                total += count
                if (total > MAX_IMPORT_BYTES) throw IOException("file too large")
                output.write(chunk, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 64 * 1024 * 1024
        const val READ_CHUNK_BYTES = 8192
    }
}
