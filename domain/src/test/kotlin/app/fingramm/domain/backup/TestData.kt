package app.fingramm.domain.backup

import app.fingramm.domain.model.AccountType
import app.fingramm.domain.model.Asset
import app.fingramm.domain.model.BookkeepingExperience
import app.fingramm.domain.model.BudgetProfile
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryKind
import app.fingramm.domain.model.CategoryLimit
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Debt
import app.fingramm.domain.model.Goal
import app.fingramm.domain.model.GoalHorizon
import app.fingramm.domain.model.IncomeSource
import app.fingramm.domain.model.InputMode
import app.fingramm.domain.model.NamedAmount
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.ReminderSettings
import app.fingramm.domain.model.SavingsEntry
import app.fingramm.domain.model.SavingsPlace
import app.fingramm.domain.model.SurveyAnswers
import app.fingramm.domain.model.TimeCommitment
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.model.TransactionType
import java.time.LocalDate

internal object TestData {
    val today: LocalDate = LocalDate.of(2026, 10, 10)

    val salary = Category(1, "Зарплата", TransactionType.INCOME, CategoryKind.VARIABLE, "💼")
    val groceries = Category(2, "Продукты", TransactionType.EXPENSE, CategoryKind.MANDATORY, "🛒")
    val cafe = Category(3, "Кафе", TransactionType.EXPENSE, CategoryKind.VARIABLE, "☕")
    val loans = Category(4, "Кредиты", TransactionType.EXPENSE, CategoryKind.MANDATORY, "💳", isDebtPayment = true)

    fun backup(): BackupData = BackupData(
        profile = BudgetProfile(3, true, 1, CurrencyCode.RUB, today.minusMonths(2)),
        survey = SurveyAnswers(
            mandatoryMode = InputMode.BY_CATEGORY,
            mandatoryItems = listOf(NamedAmount("Аренда", 3_000_000)),
            variableTotalMinor = 1_500_000,
            hasCushion = true,
            cushionMinor = 9_000_000,
            savings = listOf(SavingsEntry(SavingsPlace.DEPOSITS, 12_000_000)),
            includeAssetsInCapital = true,
            experience = BookkeepingExperience.APPS,
            timeCommitment = TimeCommitment.WEEKLY,
        ),
        incomeSources = listOf(IncomeSource(1, "Зарплата", 12_000_000)),
        assets = listOf(Asset(1, "Авто", 50_000_000)),
        debts = listOf(Debt(1, "Кредиты и долги", 1_000_000, 30_000_000)),
        categories = listOf(salary, groceries, cafe, loans),
        transactions = listOf(
            Transaction(
                1, TransactionType.INCOME, 12_000_000, 1, today.minusDays(5), "Аванс",
                AccountType.CARD, null, null, 111,
            ),
            Transaction(
                2, TransactionType.EXPENSE, 123_456, 2, today, "Магнит; \"молоко\"",
                AccountType.CASH, null, 1, 222,
            ),
            Transaction(3, TransactionType.EXPENSE, 50_000, 3, today.minusDays(1), "", null, null, null, 333),
        ),
        recurringTemplates = listOf(
            RecurringTemplate(
                1,
                TransactionType.EXPENSE,
                1_000_000,
                4,
                "Ипотека",
                AccountType.CARD,
                15,
                today.plusDays(5),
            ),
        ),
        goals = listOf(
            Goal(1, "Подушка", 18_000_000, 9_000_000, today.plusMonths(8), GoalHorizon.YEAR, today.minusMonths(2)),
            Goal(2, "Квартира", 500_000_000, 0, null, GoalHorizon.MID, today),
        ),
        categoryLimits = listOf(CategoryLimit(3, 500_000)),
        reminder = ReminderSettings(ReminderMode.WEEKLY, 19, 15, 6),
        monthStartDay = 25,
    )
}
