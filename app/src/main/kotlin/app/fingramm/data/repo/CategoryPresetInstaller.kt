package app.fingramm.data.repo

import android.content.Context
import app.fingramm.domain.model.DefaultCategories
import app.fingramm.ui.CategoryPresets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Добавляет в уже работающее приложение новые предустановленные категории после обновления. Существующие категории
 * не трогаются, а те, что пользователь завёл сам (например, «Табак»), не дублируются: сравнение идёт по названию.
 * Версия набора запоминается, поэтому удалённая или заархивированная пользователем категория не возвращается.
 */
@Singleton
class CategoryPresetInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val budget: BudgetRepository,
        private val categories: CategoryRepository,
        private val settings: SettingsRepository,
    ) {
        suspend fun sync() {
            if (budget.profile.first() == null) return
            val version = settings.current().presetsVersion
            if (version >= DefaultCategories.CURRENT_VERSION) return
            categories.addMissing(CategoryPresets.buildAfter(context, version))
            settings.setPresetsVersion(DefaultCategories.CURRENT_VERSION)
        }
    }
