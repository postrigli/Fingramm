package app.fingramm.data

import app.fingramm.data.db.AssetEntity
import app.fingramm.data.db.BudgetProfileEntity
import app.fingramm.data.db.CategoryEntity
import app.fingramm.data.db.CategoryLimitEntity
import app.fingramm.data.db.DebtEntity
import app.fingramm.data.db.GoalEntity
import app.fingramm.data.db.IncomeSourceEntity
import app.fingramm.data.db.RecurringTemplateEntity
import app.fingramm.data.db.SurveyAnswersEntity
import app.fingramm.data.db.TransactionEntity
import app.fingramm.domain.model.Asset
import app.fingramm.domain.model.BudgetProfile
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryLimit
import app.fingramm.domain.model.Debt
import app.fingramm.domain.model.Goal
import app.fingramm.domain.model.IncomeSource
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.SurveyAnswers
import app.fingramm.domain.model.Transaction

fun BudgetProfileEntity.toDomain() = BudgetProfile(familySize, hasChildren, childrenCount, currency, createdAt)

fun BudgetProfile.toEntity() = BudgetProfileEntity(
    familySize = familySize,
    hasChildren = hasChildren,
    childrenCount = childrenCount,
    currency = currency,
    createdAt = createdAt,
)

fun SurveyAnswersEntity.toDomain() = SurveyAnswers(
    mandatoryMode = mandatoryMode,
    mandatoryTotalMinor = mandatoryTotalMinor,
    mandatoryItems = mandatoryItems,
    variableMode = variableMode,
    variableTotalMinor = variableTotalMinor,
    variableItems = variableItems,
    variableUnknown = variableUnknown,
    hasCushion = hasCushion,
    cushionMinor = cushionMinor,
    savings = savings,
    includeAssetsInCapital = includeAssetsInCapital,
    experience = experience,
    timeCommitment = timeCommitment,
)

fun SurveyAnswers.toEntity() = SurveyAnswersEntity(
    mandatoryMode = mandatoryMode,
    mandatoryTotalMinor = mandatoryTotalMinor,
    mandatoryItems = mandatoryItems,
    variableMode = variableMode,
    variableTotalMinor = variableTotalMinor,
    variableItems = variableItems,
    variableUnknown = variableUnknown,
    hasCushion = hasCushion,
    cushionMinor = cushionMinor,
    savings = savings,
    includeAssetsInCapital = includeAssetsInCapital,
    experience = experience,
    timeCommitment = timeCommitment,
)

fun IncomeSourceEntity.toDomain() = IncomeSource(id, name, amountMinor)

fun IncomeSource.toEntity() = IncomeSourceEntity(id, name, amountMinor)

fun AssetEntity.toDomain() = Asset(id, name, valueMinor)

fun Asset.toEntity() = AssetEntity(id, name, valueMinor)

fun DebtEntity.toDomain() = Debt(id, name, monthlyPaymentMinor, balanceMinor)

fun Debt.toEntity() = DebtEntity(id, name, monthlyPaymentMinor, balanceMinor)

fun CategoryEntity.toDomain() = Category(id, name, type, kind, icon, archived, isDebtPayment)

fun Category.toEntity() = CategoryEntity(id, name, type, kind, icon, archived, isDebtPayment)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    type = type,
    amountMinor = amountMinor,
    categoryId = categoryId,
    date = date,
    note = note,
    accountType = accountType,
    memberId = memberId,
    recurringId = recurringId,
    createdAt = createdAt,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    type = type,
    amountMinor = amountMinor,
    categoryId = categoryId,
    date = date,
    note = note,
    noteLower = note.lowercase(),
    accountType = accountType,
    memberId = memberId,
    recurringId = recurringId,
    createdAt = createdAt,
)

fun RecurringTemplateEntity.toDomain() = RecurringTemplate(
    id = id,
    type = type,
    amountMinor = amountMinor,
    categoryId = categoryId,
    note = note,
    accountType = accountType,
    dayOfMonth = dayOfMonth,
    nextDueDate = nextDueDate,
    active = active,
)

fun RecurringTemplate.toEntity() = RecurringTemplateEntity(
    id = id,
    type = type,
    amountMinor = amountMinor,
    categoryId = categoryId,
    note = note,
    accountType = accountType,
    dayOfMonth = dayOfMonth,
    nextDueDate = nextDueDate,
    active = active,
)

fun GoalEntity.toDomain() = Goal(id, title, targetMinor, savedMinor, deadline, horizon, createdAt)

fun Goal.toEntity() = GoalEntity(id, title, targetMinor, savedMinor, deadline, horizon, createdAt)

fun CategoryLimitEntity.toDomain() = CategoryLimit(categoryId, limitMinor)

fun CategoryLimit.toEntity() = CategoryLimitEntity(categoryId, limitMinor)
