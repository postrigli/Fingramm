package app.fingramm.data.repo

import androidx.room.withTransaction
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.toDomain
import app.fingramm.data.toEntity
import app.fingramm.domain.metrics.PlanInputs
import app.fingramm.domain.model.Asset
import app.fingramm.domain.model.BudgetProfile
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CurrencyCode
import app.fingramm.domain.model.Debt
import app.fingramm.domain.model.IncomeSource
import app.fingramm.domain.model.SurveyAnswers
import app.fingramm.domain.survey.SurveyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Профиль бюджета и ответы опроса: всё, что нужно для плановых расчётов. */
data class BudgetData(
    val profile: BudgetProfile,
    val survey: SurveyAnswers,
    val incomeSources: List<IncomeSource>,
    val assets: List<Asset>,
    val debts: List<Debt>,
) {
    val planInputs: PlanInputs get() = PlanInputs(survey, incomeSources, debts, assets)
}

@Singleton
class BudgetRepository @Inject constructor(private val db: AppDatabase) {
    private val profileDao = db.profileDao()
    private val categoryDao = db.categoryDao()
    private val goalDao = db.goalDao()

    /** Null — онбординг ещё не пройден. */
    val profile: Flow<BudgetProfile?> = profileDao.observeProfile().map { it?.toDomain() }.distinctUntilChanged()

    val budget: Flow<BudgetData?> = combine(
        profileDao.observeProfile(),
        profileDao.observeSurvey(),
        profileDao.observeIncomeSources(),
        profileDao.observeAssets(),
        profileDao.observeDebts(),
    ) { profile, survey, income, assets, debts ->
        if (profile == null || survey == null) {
            null
        } else {
            BudgetData(
                profile.toDomain(),
                survey.toDomain(),
                income.map { it.toDomain() },
                assets.map { it.toDomain() },
                debts.map { it.toDomain() },
            )
        }
    }

    suspend fun getBudget(): BudgetData? {
        val profile = profileDao.getProfile() ?: return null
        val survey = profileDao.getSurvey() ?: return null
        return BudgetData(
            profile.toDomain(),
            survey.toDomain(),
            profileDao.getIncomeSources().map { it.toDomain() },
            profileDao.getAssets().map { it.toDomain() },
            profileDao.getDebts().map { it.toDomain() },
        )
    }

    /**
     * Завершение онбординга одной транзакцией: профиль, ответы, цели и (при первом запуске)
     * предустановленные категории. [presetCategories] — уже с локализованными названиями.
     */
    suspend fun completeOnboarding(result: SurveyResult, presetCategories: List<Category>) {
        db.withTransaction {
            writeSurvey(result)
            if (categoryDao.count() == 0) {
                categoryDao.insertAll(presetCategories.map { it.toEntity() })
            }
            goalDao.insertAll(result.goals.map { it.toEntity() })
        }
    }

    /** Редактирование ответов опроса: цели не затрагиваются (они живут на вкладке «Цели»). */
    suspend fun updateSurvey(result: SurveyResult) {
        db.withTransaction { writeSurvey(result) }
    }

    private suspend fun writeSurvey(result: SurveyResult) {
        profileDao.upsertProfile(result.profile.toEntity())
        profileDao.upsertSurvey(result.survey.toEntity())
        profileDao.clearIncomeSources()
        profileDao.insertIncomeSources(result.incomeSources.map { it.toEntity().copy(id = 0) })
        profileDao.clearAssets()
        profileDao.insertAssets(result.assets.map { it.toEntity().copy(id = 0) })
        profileDao.clearDebts()
        profileDao.insertDebts(result.debts.map { it.toEntity().copy(id = 0) })
    }

    /** Смена основной валюты. Суммы не пересчитываются: курсы и мультивалютность вне рамок v1. */
    suspend fun setCurrency(currency: CurrencyCode) {
        val profile = profileDao.getProfile() ?: return
        profileDao.upsertProfile(profile.copy(currency = currency))
    }
}
