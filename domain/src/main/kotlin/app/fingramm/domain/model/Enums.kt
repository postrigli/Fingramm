package app.fingramm.domain.model

import kotlinx.serialization.Serializable

/** Основная валюта бюджета. Мультивалютного учёта в v1 нет. */
@Serializable
public enum class CurrencyCode(public val code: String, public val symbol: String) {
    RUB("RUB", "₽"),
    USD("USD", "$"),
    EUR("EUR", "€"),
}

@Serializable
public enum class TransactionType { INCOME, EXPENSE }

/** Влияет на показатели: обязательные и переменные расходы считаются отдельно. Для доходов игнорируется. */
@Serializable
public enum class CategoryKind { MANDATORY, VARIABLE }

@Serializable
public enum class AccountType { CASH, CARD, SAVINGS }

@Serializable
public enum class GoalHorizon { YEAR, MID }

/** Способ ввода расходов в опросе: одной суммой или по категориям. */
@Serializable
public enum class InputMode { TOTAL, BY_CATEGORY }

@Serializable
public enum class SavingsPlace { CASH, SAVINGS_ACCOUNT, DEPOSITS, INVESTMENTS }

@Serializable
public enum class BookkeepingExperience { NEVER, EXCEL, APPS, QUIT }

@Serializable
public enum class TimeCommitment { DAILY, FEW_MINUTES_DAILY, WEEKLY, MONTHLY }

@Serializable
public enum class ReminderMode { DAILY_EVENING, WEEKLY, OFF }

/** Главная цель на год из опроса. */
@Serializable
public enum class MainGoalKind { CUSHION, PAY_OFF_DEBT, VACATION, START_INVESTING, OTHER }
