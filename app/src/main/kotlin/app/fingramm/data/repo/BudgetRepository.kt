package app.fingramm.data.repo

import androidx.room.withTransaction
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.toDomain
import app.fingramm.data.toEntity
import app.fingramm.domain.model.BudgetProfile
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CurrencyCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Профиль бюджета: основная валюта и признак «первый запуск пройден». */
@Singleton
class BudgetRepository
    @Inject
    constructor(private val db: AppDatabase) {
        private val profileDao = db.profileDao()
        private val categoryDao = db.categoryDao()

        /** Null — первый запуск ещё не пройден. */
        val profile: Flow<BudgetProfile?> = profileDao.observeProfile().map { it?.toDomain() }.distinctUntilChanged()

        /**
         * Завершение первого запуска одной транзакцией: профиль с валютой и (если категорий ещё нет)
         * предустановленные категории. [presetCategories] — уже с локализованными названиями.
         */
        suspend fun completeOnboarding(
            currency: CurrencyCode,
            today: LocalDate,
            presetCategories: List<Category>,
        ) {
            db.withTransaction {
                profileDao.upsertProfile(BudgetProfile(currency = currency, createdAt = today).toEntity())
                if (categoryDao.count() == 0) {
                    categoryDao.insertAll(presetCategories.map { it.toEntity() })
                }
            }
        }

        /** Смена основной валюты. Суммы не пересчитываются: курсы и мультивалютность вне рамок приложения. */
        suspend fun setCurrency(currency: CurrencyCode) {
            val profile = profileDao.getProfile() ?: return
            profileDao.upsertProfile(profile.copy(currency = currency))
        }
    }
