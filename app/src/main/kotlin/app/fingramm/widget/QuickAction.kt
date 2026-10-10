package app.fingramm.widget

import android.content.Context
import android.content.Intent
import app.fingramm.MainActivity
import app.fingramm.domain.model.TransactionType

/**
 * Быстрое действие, которое виджет передаёт приложению через Intent.action.
 * Это единственный способ «заказать» экран извне: данные в Intent не передаются, поэтому подделать нечего,
 * а экран блокировки остаётся впереди — действие выполняется только после разблокировки.
 */
sealed interface QuickAction {
    val intentAction: String

    /** Открыть форму новой операции с уже выбранным типом. */
    data class AddTransaction(val type: TransactionType) : QuickAction {
        override val intentAction: String
            get() = when (type) {
                TransactionType.INCOME -> QuickAction.ACTION_ADD_INCOME
                TransactionType.EXPENSE -> QuickAction.ACTION_ADD_EXPENSE
            }
    }

    /** Открыть сводку (нажатие на показатели месяца). */
    data object OpenDashboard : QuickAction {
        override val intentAction: String = QuickAction.ACTION_OPEN_DASHBOARD
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "app.fingramm.action.ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "app.fingramm.action.ADD_INCOME"
        const val ACTION_OPEN_DASHBOARD = "app.fingramm.action.OPEN_DASHBOARD"

        private const val LAUNCH_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        /** Разбирает входящий Intent; всё незнакомое (в том числе обычный запуск с экрана приложений) — null. */
        fun fromIntent(intent: Intent?): QuickAction? = when (intent?.action) {
            ACTION_ADD_EXPENSE -> AddTransaction(TransactionType.EXPENSE)
            ACTION_ADD_INCOME -> AddTransaction(TransactionType.INCOME)
            ACTION_OPEN_DASHBOARD -> OpenDashboard
            else -> null
        }

        /**
         * Intent для PendingIntent виджета. Активность `singleTop`: если приложение уже открыто, оно не дублируется,
         * а получает действие в `onNewIntent`.
         */
        fun launchIntent(context: Context, action: QuickAction): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(action.intentAction)
                .addFlags(LAUNCH_FLAGS)
    }
}
