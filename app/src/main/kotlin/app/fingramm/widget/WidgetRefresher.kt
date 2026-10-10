package app.fingramm.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Пока приложение запущено, держит виджет месяца актуальным: любое изменение операций, профиля или блокировки
 * перерисовывает его. Когда процесса нет, обновляют система (раз в час) и события смены даты.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: WidgetStateLoader,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            loader.observe().collectLatest { state ->
                // Серия быстрых записей (например, импорт) даёт одну перерисовку, а не десятки.
                delay(DEBOUNCE_MS)
                MonthWidgetProvider.updateAll(context, state)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
