package app.fingramm.domain.backup

import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryLimit
import app.fingramm.domain.model.Goal
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.Transaction

/**
 * Слияние импортируемых данных с текущими. Результат — полный набор данных, который затем
 * записывается тем же способом, что и обычная замена.
 *
 * Правила:
 *  - профиль, ответы опроса, источники дохода, активы, долги и настройки остаются текущими
 *    (если текущего профиля нет — берутся из импорта целиком);
 *  - категории сопоставляются по (тип, название без учёта регистра), новые добавляются;
 *  - операции, шаблоны и цели добавляются, точные дубликаты пропускаются (повторный импорт безопасен);
 *  - лимиты добавляются только для категорий, у которых лимита ещё нет.
 */
public object BackupMerger {
    public fun merge(current: BackupData, incoming: BackupData): BackupData {
        if (current.profile == null) return incoming

        val (categories, categoryMap) = mergeCategories(current.categories, incoming.categories)
        val (templates, templateMap) = mergeTemplates(
            current.recurringTemplates,
            incoming.recurringTemplates,
            categoryMap,
        )
        return current.copy(
            categories = categories,
            transactions = mergeTransactions(current.transactions, incoming.transactions, categoryMap, templateMap),
            recurringTemplates = templates,
            goals = mergeGoals(current.goals, incoming.goals),
            categoryLimits = mergeLimits(current.categoryLimits, incoming.categoryLimits, categoryMap),
        )
    }

    /** Возвращает итоговый список категорий и соответствие «id в импорте → id в итоге». */
    private fun mergeCategories(
        current: List<Category>,
        incoming: List<Category>,
    ): Pair<List<Category>, Map<Long, Long>> {
        var nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val result = current.toMutableList()
        val idMap = mutableMapOf<Long, Long>()
        for (source in incoming) {
            val existing = result.firstOrNull {
                it.type == source.type && it.name.equals(source.name, ignoreCase = true)
            }
            if (existing != null) {
                idMap[source.id] = existing.id
            } else {
                val added = source.copy(id = nextId++)
                result += added
                idMap[source.id] = added.id
            }
        }
        return result to idMap
    }

    private fun mergeTemplates(
        current: List<RecurringTemplate>,
        incoming: List<RecurringTemplate>,
        categoryMap: Map<Long, Long>,
    ): Pair<List<RecurringTemplate>, Map<Long, Long>> {
        var nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val result = current.toMutableList()
        val idMap = mutableMapOf<Long, Long>()
        for (source in incoming) {
            val mapped = source.copy(categoryId = categoryMap.getValue(source.categoryId))
            val existing = result.firstOrNull { sameTemplate(it, mapped) }
            if (existing != null) {
                idMap[source.id] = existing.id
            } else {
                val added = mapped.copy(id = nextId++)
                result += added
                idMap[source.id] = added.id
            }
        }
        return result to idMap
    }

    private fun mergeTransactions(
        current: List<Transaction>,
        incoming: List<Transaction>,
        categoryMap: Map<Long, Long>,
        templateMap: Map<Long, Long>,
    ): List<Transaction> {
        var nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val result = current.toMutableList()
        val seen = current.mapTo(HashSet()) { fingerprint(it) }
        for (source in incoming) {
            val mapped = source.copy(
                categoryId = categoryMap.getValue(source.categoryId),
                recurringId = source.recurringId?.let { templateMap[it] },
            )
            if (seen.add(fingerprint(mapped))) result += mapped.copy(id = nextId++)
        }
        return result
    }

    private fun mergeGoals(current: List<Goal>, incoming: List<Goal>): List<Goal> {
        var nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val result = current.toMutableList()
        for (source in incoming) {
            if (result.none { sameGoal(it, source) }) result += source.copy(id = nextId++)
        }
        return result
    }

    private fun mergeLimits(
        current: List<CategoryLimit>,
        incoming: List<CategoryLimit>,
        categoryMap: Map<Long, Long>,
    ): List<CategoryLimit> {
        val result = current.toMutableList()
        for (source in incoming) {
            val categoryId = categoryMap.getValue(source.categoryId)
            if (result.none { it.categoryId == categoryId }) result += CategoryLimit(categoryId, source.limitMinor)
        }
        return result
    }

    private fun fingerprint(t: Transaction): List<Any?> =
        listOf(t.type, t.amountMinor, t.categoryId, t.date, t.note, t.accountType)

    private fun sameTemplate(a: RecurringTemplate, b: RecurringTemplate): Boolean =
        a.type == b.type && a.amountMinor == b.amountMinor && a.categoryId == b.categoryId &&
            a.dayOfMonth == b.dayOfMonth && a.note == b.note

    private fun sameGoal(a: Goal, b: Goal): Boolean =
        a.title.equals(b.title, ignoreCase = true) && a.targetMinor == b.targetMinor && a.horizon == b.horizon
}
