package app.fingramm.widget

import android.content.Context
import android.content.Intent
import app.fingramm.MainActivity
import app.fingramm.domain.model.TransactionType

/**
 * Быстрое действие, которое виджет или уведомление передаёт приложению через Intent.action.
 * Это единственный способ «заказать» экран извне. Из данных передаётся разве что номер регулярной операции
 * (чтобы открыть форму по ней), поэтому ничего ценного подделать нельзя, а экран блокировки остаётся впереди:
 * действие выполняется только после разблокировки.
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

    /** Открыть окно со списком операций, ожидающих подтверждения (нажатие на уведомление о платеже). */
    data object OpenPending : QuickAction {
        override val intentAction: String = QuickAction.ACTION_OPEN_PENDING
    }

    /** Открыть форму операции, заполненную по регулярной операции [templateId] (кнопка «Изменить» в уведомлении). */
    data class EditPending(val templateId: Long) : QuickAction {
        override val intentAction: String = QuickAction.ACTION_EDIT_PENDING
    }

    /** Открыть сводку (нажатие на показатели месяца). */
    data object OpenDashboard : QuickAction {
        override val intentAction: String = QuickAction.ACTION_OPEN_DASHBOARD
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "app.fingramm.action.ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "app.fingramm.action.ADD_INCOME"
        const val ACTION_OPEN_DASHBOARD = "app.fingramm.action.OPEN_DASHBOARD"
        const val ACTION_OPEN_PENDING = "app.fingramm.action.OPEN_PENDING"
        const val ACTION_EDIT_PENDING = "app.fingramm.action.EDIT_PENDING"
        const val EXTRA_TEMPLATE_ID = "app.fingramm.extra.TEMPLATE_ID"
        const val EXTRA_NOTIFICATION_ID = "app.fingramm.extra.NOTIFICATION_ID"

        private const val NO_ID = 0L
        private const val NO_NOTIFICATION = Int.MIN_VALUE

        private const val LAUNCH_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        /** Разбирает входящий Intent; всё незнакомое (в том числе обычный запуск с экрана приложений) — null. */
        fun fromIntent(intent: Intent?): QuickAction? = when (intent?.action) {
            ACTION_ADD_EXPENSE -> AddTransaction(TransactionType.EXPENSE)
            ACTION_ADD_INCOME -> AddTransaction(TransactionType.INCOME)
            ACTION_OPEN_DASHBOARD -> OpenDashboard
            ACTION_OPEN_PENDING -> OpenPending
            ACTION_EDIT_PENDING ->
                intent?.getLongExtra(EXTRA_TEMPLATE_ID, NO_ID)?.takeIf { it > 0 }?.let(::EditPending)
            else -> null
        }

        /** Номер уведомления, которое нужно убрать, когда пользователь из него перешёл в приложение. */
        fun notificationIdFrom(intent: Intent?): Int? =
            intent?.getIntExtra(EXTRA_NOTIFICATION_ID, NO_NOTIFICATION)?.takeIf { it != NO_NOTIFICATION }

        /**
         * Intent для PendingIntent виджета. Активность `singleTop`: если приложение уже открыто, оно не дублируется,
         * а получает действие в `onNewIntent`.
         */
        fun launchIntent(context: Context, action: QuickAction, notificationId: Int? = null): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(action.intentAction)
                .addFlags(LAUNCH_FLAGS)
                .also { intent ->
                    if (action is EditPending) intent.putExtra(EXTRA_TEMPLATE_ID, action.templateId)
                    if (notificationId != null) intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
    }
}
