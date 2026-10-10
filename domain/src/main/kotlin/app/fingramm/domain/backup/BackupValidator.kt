package app.fingramm.domain.backup

/** Проверка целостности данных бэкапа перед импортом. Возвращает человекочитаемые замечания. */
public object BackupValidator {
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59
    private const val MAX_WEEKDAY = 7
    private const val MAX_DAY_OF_MONTH = 31

    public fun validate(data: BackupData): List<String> =
        profile(data) + uniqueIds(data) + transactions(data) + templates(data) + limits(data) + goals(data) +
            nonNegativeAmounts(data) + reminder(data)

    private fun profile(data: BackupData): List<String> {
        val issues = mutableListOf<String>()
        if ((data.profile == null) != (data.survey == null)) issues += "profile и survey должны быть заданы вместе"
        data.profile?.let {
            if (it.familySize < 1) issues += "profile.familySize < 1"
            if (it.childrenCount < 0) issues += "profile.childrenCount < 0"
        }
        return issues
    }

    private fun uniqueIds(data: BackupData): List<String> = listOfNotNull(
        duplicates("categories", data.categories.map { it.id }),
        duplicates("transactions", data.transactions.map { it.id }),
        duplicates("recurringTemplates", data.recurringTemplates.map { it.id }),
        duplicates("goals", data.goals.map { it.id }),
        duplicates("categoryLimits", data.categoryLimits.map { it.categoryId }),
    )

    private fun duplicates(name: String, ids: List<Long>): String? =
        if (ids.size != ids.toSet().size) "$name: повторяющиеся идентификаторы" else null

    private fun transactions(data: BackupData): List<String> {
        val categoryIds = data.categories.map { it.id }.toSet()
        val templateIds = data.recurringTemplates.map { it.id }.toSet()
        return data.transactions.flatMap { t ->
            listOfNotNull(
                "transaction ${t.id}: сумма должна быть > 0".takeIf { t.amountMinor <= 0 },
                "transaction ${t.id}: неизвестная категория ${t.categoryId}".takeIf { t.categoryId !in categoryIds },
                "transaction ${t.id}: неизвестный шаблон ${t.recurringId}"
                    .takeIf { t.recurringId != null && t.recurringId !in templateIds },
            )
        }
    }

    private fun templates(data: BackupData): List<String> {
        val categoryIds = data.categories.map { it.id }.toSet()
        return data.recurringTemplates.flatMap { r ->
            listOfNotNull(
                "recurring ${r.id}: сумма должна быть > 0".takeIf { r.amountMinor <= 0 },
                "recurring ${r.id}: неизвестная категория ${r.categoryId}".takeIf { r.categoryId !in categoryIds },
                "recurring ${r.id}: день месяца вне 1..31".takeIf { r.dayOfMonth !in 1..MAX_DAY_OF_MONTH },
            )
        }
    }

    private fun limits(data: BackupData): List<String> {
        val categoryIds = data.categories.map { it.id }.toSet()
        return data.categoryLimits.flatMap { l ->
            listOfNotNull(
                "limit: неизвестная категория ${l.categoryId}".takeIf { l.categoryId !in categoryIds },
                "limit ${l.categoryId}: лимит должен быть > 0".takeIf { l.limitMinor <= 0 },
            )
        }
    }

    private fun goals(data: BackupData): List<String> = data.goals.flatMap { g ->
        listOfNotNull(
            "goal ${g.id}: накоплено < 0".takeIf { g.savedMinor < 0 },
            "goal ${g.id}: цель должна быть > 0".takeIf { g.targetMinor != null && g.targetMinor <= 0 },
        )
    }

    private fun nonNegativeAmounts(data: BackupData): List<String> =
        data.incomeSources.filter { it.amountMinor < 0 }.map { "income ${it.id}: сумма < 0" } +
            data.assets.filter { it.valueMinor < 0 }.map { "asset ${it.id}: стоимость < 0" } +
            data.debts.filter { it.monthlyPaymentMinor < 0 }.map { "debt ${it.id}: платёж < 0" }

    private fun reminder(data: BackupData): List<String> {
        val r = data.reminder
        val valid = r.hour in 0..MAX_HOUR && r.minute in 0..MAX_MINUTE && r.weekday in 1..MAX_WEEKDAY
        return if (valid) emptyList() else listOf("reminder: время вне допустимого диапазона")
    }
}
