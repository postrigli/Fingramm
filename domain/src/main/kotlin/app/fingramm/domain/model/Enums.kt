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

@Serializable
public enum class ReminderMode { DAILY_EVENING, WEEKLY, OFF }

/** Как повторяется регулярная операция: раз в месяц в выбранный день или по выбранным дням недели. */
@Serializable
public enum class RecurrenceFrequency { MONTHLY, WEEKLY }
