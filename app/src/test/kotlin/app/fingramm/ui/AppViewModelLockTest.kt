package app.fingramm.ui

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.security.ExternalFlowGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Блокировка приложения: включение отпечатка в настройках не должно запирать приложение сразу, а системное окно,
 * которое приложение открыло само (выбор файла для импорта), не должно считаться уходом в фон.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppViewModelLockTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsRepository
    private lateinit var guard: ExternalFlowGuard

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.IO + Job())
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        settings = SettingsRepository(store)
        guard = ExternalFlowGuard()
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun newViewModel() = AppViewModel(BudgetRepository(db), settings, guard)

    /** Ждёт, пока состояние блокировки станет [expected]; null — настройки ещё читаются. */
    private fun AppViewModel.awaitLocked(expected: Boolean) {
        runBlocking { withTimeout(TIMEOUT_MS) { locked.first { it == expected } } }
    }

    /** Даёт потокам настроек дойти до модели и проверяет, что состояние осталось [expected]. */
    private fun AppViewModel.assertStaysLocked(expected: Boolean) {
        runBlocking { delay(SETTLE_MS) }
        assertEquals(expected, locked.value)
    }

    /** Включает блокировку и ждёт, пока об этом узнает модель (её подписка на настройки работает асинхронно). */
    private fun enableLock() {
        runBlocking {
            settings.setLockEnabled(true)
            settings.settings.first { it.lockEnabled }
            delay(SETTLE_MS)
        }
    }

    @Test
    fun `without a lock the app is open`() {
        newViewModel().awaitLocked(false)
    }

    @Test
    fun `a cold start with the lock on asks for confirmation`() {
        runBlocking {
            settings.setLockEnabled(true)
            newViewModel().awaitLocked(true)
        }
    }

    @Test
    fun `enabling the lock in a running app does not lock it at once`() {
        runBlocking {
            val viewModel = newViewModel()
            viewModel.awaitLocked(false)
            enableLock()
            viewModel.assertStaysLocked(false)
        }
    }

    @Test
    fun `after the lock is enabled the app locks only once it goes to the background`() {
        runBlocking {
            val viewModel = newViewModel()
            viewModel.awaitLocked(false)
            enableLock()
            viewModel.onBackgrounded()
            viewModel.awaitLocked(true)
            viewModel.unlock()
            viewModel.awaitLocked(false)
        }
    }

    @Test
    fun `going to the background with the lock off does not lock the app later`() {
        runBlocking {
            val viewModel = newViewModel()
            viewModel.awaitLocked(false)
            viewModel.onBackgrounded()
            // Раньше после этого включение блокировки сразу запирало приложение и сбрасывало экран настроек.
            enableLock()
            viewModel.assertStaysLocked(false)
        }
    }

    @Test
    fun `turning the lock off opens a locked app`() {
        runBlocking {
            settings.setLockEnabled(true)
            val viewModel = newViewModel()
            viewModel.awaitLocked(true)
            settings.setLockEnabled(false)
            viewModel.awaitLocked(false)
        }
    }

    @Test
    fun `a file picker opened by the app does not lock it, a real exit does`() {
        runBlocking {
            val viewModel = newViewModel()
            viewModel.awaitLocked(false)
            enableLock()

            guard.begin() // приложение открывает системный выбор файла
            viewModel.onBackgrounded() // выбор файла закрыл приложение
            viewModel.assertStaysLocked(false)

            guard.end() // результат получен
            viewModel.onBackgrounded() // настоящий уход в фон
            viewModel.awaitLocked(true)
        }
    }

    @Test
    fun `the picker exemption covers one exit only`() {
        runBlocking {
            val viewModel = newViewModel()
            viewModel.awaitLocked(false)
            enableLock()

            guard.begin()
            viewModel.onBackgrounded() // освобождён
            viewModel.onBackgrounded() // второй уход без нового окна — уже блокировка
            viewModel.awaitLocked(true)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 300L
    }
}
