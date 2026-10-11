package app.fingramm.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fingramm.data.db.AppDatabase
import app.fingramm.data.repo.BackupRepository
import app.fingramm.data.repo.BudgetRepository
import app.fingramm.data.repo.CategoryRepository
import app.fingramm.data.repo.GoalRepository
import app.fingramm.data.repo.ImportMode
import app.fingramm.data.repo.RecurringRepository
import app.fingramm.data.repo.SettingsRepository
import app.fingramm.data.repo.StatsRepository
import app.fingramm.data.repo.TransactionFilter
import app.fingramm.data.repo.TransactionRepository
import app.fingramm.domain.model.Category
import app.fingramm.domain.model.CategoryKind
import app.fingramm.domain.model.DefaultCategories
import app.fingramm.domain.model.GoalHorizon
import app.fingramm.domain.model.RecurrenceFrequency
import app.fingramm.domain.model.RecurringTemplate
import app.fingramm.domain.model.ReminderMode
import app.fingramm.domain.model.ReminderSettings
import app.fingramm.domain.model.Transaction
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.period.BudgetPeriod
import app.fingramm.domain.recurring.PendingOccurrence
import app.fingramm.domain.survey.DefaultNames
import app.fingramm.domain.survey.DraftAmount
import app.fingramm.domain.survey.ExpenseDraft
import app.fingramm.domain.survey.GoalDraft
import app.fingramm.domain.survey.SurveyDraft
import app.fingramm.domain.survey.SurveyMapper
import app.fingramm.domain.survey.SurveyResult
import app.fingramm.notifications.RecurringOccurrenceHandler
import app.fingramm.widget.WidgetStateLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Репозитории поверх Room в памяти (Robolectric): запросы, поиск, фильтры, регулярные операции, бэкап. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoriesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsRepository
    private lateinit var budget: BudgetRepository
    private lateinit var categories: CategoryRepository
    private lateinit var transactions: TransactionRepository
    private lateinit var goals: GoalRepository
    private lateinit var recurring: RecurringRepository
    private lateinit var stats: StatsRepository
    private lateinit var backup: BackupRepository

    private val today = LocalDate.of(2026, 10, 10)
    private val names = DefaultNames("Доход", "Актив", "Кредиты и долги")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.IO + Job())
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        settings = SettingsRepository(store)
        budget = BudgetRepository(db)
        categories = CategoryRepository(db)
        transactions = TransactionRepository(db)
        goals = GoalRepository(db)
        recurring = RecurringRepository(db)
        stats = StatsRepository(db, settings)
        backup = BackupRepository(db, settings)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private val validDraft = SurveyDraft(
        familySize = 3,
        hasChildren = true,
        childrenCount = 1,
        incomeSources = listOf(DraftAmount("Зарплата", 10_000_000), DraftAmount("Подработка", 2_000_000)),
        mandatory = ExpenseDraft(totalMinor = 4_000_000),
        variable = ExpenseDraft(totalMinor = 2_000_000),
        hasDebts = true,
        debtPaymentMinor = 1_000_000,
        hasCushion = true,
        cushionMinor = 6_000_000,
        mainGoal = app.fingramm.domain.survey.MainGoalDraft(
            app.fingramm.domain.model.MainGoalKind.CUSHION,
            targetMinor = 12_000_000,
        ),
        midGoals = listOf(GoalDraft("Квартира", 500_000_000, today.plusYears(3))),
    )

    private fun presetCategories(): List<Category> = DefaultCategories.all.map {
        Category(name = it.key, type = it.type, kind = it.kind, icon = it.icon, isDebtPayment = it.isDebtPayment)
    }

    private fun surveyResult(draft: SurveyDraft = validDraft): SurveyResult =
        SurveyMapper.toResult(draft, today, defaultNames = names) { "Подушка" }

    private fun onboard() = runBlocking { budget.completeOnboarding(surveyResult(), presetCategories()) }

    private fun categoryId(name: String): Long = runBlocking {
        categories.categories.first().first { it.name == name }.id
    }

    private fun tx(
        amount: Long,
        category: String,
        date: LocalDate,
        note: String = "",
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(type = type, amountMinor = amount, categoryId = categoryId(category), date = date, note = note)

    // ---------- Онбординг ----------

    @Test
    fun onboardingPersistsProfileSurveyIncomeDebtsGoalsAndCategories() = runBlocking {
        onboard()
        val data = checkNotNull(budget.getBudget())
        assertEquals(3, data.profile.familySize)
        assertEquals(12_000_000L, data.incomeSources.sumOf { it.amountMinor })
        assertEquals(1_000_000L, data.debts.single().monthlyPaymentMinor)
        assertEquals(6_000_000L, data.survey.cushionMinor)
        assertEquals(DefaultCategories.all.size, categories.categories.first().size)
        val savedGoals = goals.goals.first().associateBy { it.title }
        assertEquals(setOf("Подушка", "Квартира"), savedGoals.keys)
        // Существующая подушка засчитана в прогресс главной цели.
        assertEquals(6_000_000L, savedGoals.getValue("Подушка").savedMinor)
        assertEquals(GoalHorizon.MID, savedGoals.getValue("Квартира").horizon)
        assertEquals(GoalHorizon.YEAR, savedGoals.getValue("Подушка").horizon)
    }

    @Test
    fun updatingSurveyReplacesRowsAndKeepsGoalsAndCategories() = runBlocking {
        onboard()
        val edited = validDraft.copy(
            incomeSources = listOf(DraftAmount("Зарплата", 9_000_000)),
            hasDebts = false,
        )
        budget.updateSurvey(surveyResult(edited))
        val data = checkNotNull(budget.getBudget())
        assertEquals(listOf(9_000_000L), data.incomeSources.map { it.amountMinor })
        assertTrue(data.debts.isEmpty())
        assertEquals(2, goals.goals.first().size)
        assertEquals(DefaultCategories.all.size, categories.categories.first().size)
    }

    @Test
    fun onboardingDoesNotReseedExistingCategories() = runBlocking {
        onboard()
        budget.completeOnboarding(surveyResult(), presetCategories())
        assertEquals(DefaultCategories.all.size, categories.categories.first().size)
    }

    // ---------- Операции ----------

    @Test
    fun filtersByTypeCategoryAndPeriod() = runBlocking {
        onboard()
        transactions.add(tx(100, "groceries", today), 1)
        transactions.add(tx(200, "cafe", today.minusDays(1)), 2)
        transactions.add(tx(300, "salary", today, type = TransactionType.INCOME), 3)
        transactions.add(tx(400, "groceries", today.minusMonths(2)), 4)
        val all = categories.categories.first()

        fun observe(filter: TransactionFilter) = runBlocking { transactions.observe(filter, all, 100).first() }

        assertEquals(4, observe(TransactionFilter()).size)
        assertEquals(1, observe(TransactionFilter(type = TransactionType.INCOME)).size)
        assertEquals(2, observe(TransactionFilter(categoryId = categoryId("groceries"))).size)
        val thisMonth =
            observe(TransactionFilter(from = YearMonth.from(today).atDay(1), to = YearMonth.from(today).atEndOfMonth()))
        assertEquals(listOf(300L, 100L, 200L).sorted(), thisMonth.map { it.amountMinor }.sorted())
        assertEquals(today, observe(TransactionFilter()).first().date)
    }

    @Test
    fun searchIgnoresCaseForCyrillicAndMatchesCategoryNames() = runBlocking {
        onboard()
        transactions.add(tx(100, "groceries", today, note = "Молоко и хлеб"), 1)
        transactions.add(tx(200, "cafe", today, note = "обед"), 2)
        val all = categories.categories.first().map { if (it.name == "cafe") it.copy(name = "Кафе") else it }

        fun search(query: String) = runBlocking {
            transactions.observe(TransactionFilter(query = query), all, 100).first()
        }

        assertEquals(listOf(100L), search("МОЛОКО").map { it.amountMinor })
        assertEquals(listOf(100L), search("хле").map { it.amountMinor })
        // По названию категории: «каф» находит операцию в категории «Кафе».
        assertEquals(listOf(200L), search("КАФ").map { it.amountMinor })
        assertTrue(search("такси").isEmpty())
    }

    @Test
    fun searchTreatsLikeWildcardsLiterally() = runBlocking {
        onboard()
        transactions.add(tx(100, "groceries", today, note = "скидка 100% и_и"), 1)
        transactions.add(tx(200, "groceries", today, note = "обычная покупка"), 2)
        val all = categories.categories.first()

        fun search(query: String) = runBlocking {
            transactions.observe(TransactionFilter(query = query), all, 100).first()
        }

        assertEquals(listOf(100L), search("100%").map { it.amountMinor })
        assertEquals(listOf(100L), search("и_и").map { it.amountMinor })
        assertTrue(search("%").size == 1)
        assertTrue(search("_").size == 1)
    }

    @Test
    fun limitCapsTheListNewestFirst() = runBlocking {
        onboard()
        (1..5).forEach { transactions.add(tx(it * 100L, "groceries", today.minusDays(it.toLong())), it.toLong()) }
        val page = transactions.observe(TransactionFilter(), categories.categories.first(), 3).first()
        assertEquals(listOf(100L, 200L, 300L), page.map { it.amountMinor })
    }

    @Test
    fun deleteAndRestoreKeepsTheSameId() = runBlocking {
        onboard()
        val id = transactions.add(tx(100, "groceries", today, note = "хлеб"), 1)
        val stored = checkNotNull(transactions.get(id))
        transactions.delete(id)
        assertEquals(null, transactions.get(id))
        transactions.restore(stored)
        assertEquals(stored, transactions.get(id))
    }

    @Test
    fun countCreatedSinceSupportsSmartReminders() = runBlocking {
        onboard()
        transactions.add(tx(100, "groceries", today), 1_000)
        assertEquals(1, transactions.countCreatedSince(500))
        assertEquals(0, transactions.countCreatedSince(2_000))
    }

    @Test
    fun monthTotalsGroupByMonthCategoryAndType() = runBlocking {
        onboard()
        transactions.add(tx(100, "groceries", today), 1)
        transactions.add(tx(50, "groceries", today.minusDays(3)), 2)
        transactions.add(tx(900, "salary", today, type = TransactionType.INCOME), 3)
        transactions.add(tx(70, "groceries", today.minusMonths(1)), 4)
        val rows = stats.monthCategoryTotals.first()
        val october = rows.filter { it.month == YearMonth.of(2026, 10) }
        assertEquals(150L, october.first { it.type == TransactionType.EXPENSE }.totalMinor)
        assertEquals(900L, october.first { it.type == TransactionType.INCOME }.totalMinor)
        assertEquals(70L, rows.first { it.month == YearMonth.of(2026, 9) }.totalMinor)
    }

    // ---------- Категории ----------

    @Test
    fun categoryLimitIsSavedUpdatedAndRemoved() = runBlocking {
        onboard()
        val cafe = categories.categories.first().first { it.name == "cafe" }
        categories.save(cafe, 500_000)
        assertEquals(500_000L, categories.limits.first().single().limitMinor)
        categories.save(cafe.copy(icon = "🍰"), 700_000)
        assertEquals(700_000L, categories.limits.first().single().limitMinor)
        categories.save(cafe, null)
        assertTrue(categories.limits.first().isEmpty())
    }

    @Test
    fun incomeCategoriesNeverGetLimits() = runBlocking {
        onboard()
        val salary = categories.categories.first().first { it.name == "salary" }
        categories.save(salary, 1_000)
        assertTrue(categories.limits.first().isEmpty())
    }

    @Test
    fun newCategoryIsInsertedWithGeneratedId() = runBlocking {
        onboard()
        val taxi = Category(name = "Такси", type = TransactionType.EXPENSE, kind = CategoryKind.VARIABLE, icon = "🚕")
        val id = categories.save(taxi, null)
        assertTrue(id > 0)
        assertEquals("Такси", categories.get(id)?.name)
    }

    // ---------- Цели ----------

    @Test
    fun goalTopUpNeverGoesBelowZero() = runBlocking {
        onboard()
        val goal = goals.goals.first().first { it.title == "Квартира" }
        goals.addToSaved(goal.id, 5_000)
        assertEquals(5_000L, goals.get(goal.id)?.savedMinor)
        goals.addToSaved(goal.id, -9_000)
        assertEquals(0L, goals.get(goal.id)?.savedMinor)
    }

    // ---------- Регулярные операции ----------

    private fun template(due: LocalDate) = RecurringTemplate(
        type = TransactionType.EXPENSE,
        amountMinor = 1_000_000,
        categoryId = categoryId("loans"),
        note = "Ипотека",
        dayOfMonth = 5,
        nextDueDate = due,
    )

    @Test
    fun confirmingCreatesTheTransactionAndMovesTheTemplateOnce() = runBlocking {
        onboard()
        val id = recurring.save(template(LocalDate.of(2026, 10, 5)))
        val occurrence = PendingOccurrence(checkNotNull(recurring.get(id)), LocalDate.of(2026, 10, 5))
        recurring.confirm(occurrence, 1_000)
        recurring.confirm(occurrence, 2_000) // повторное нажатие не должно создать дубль
        val created = transactions.observe(TransactionFilter(), categories.categories.first(), 10).first()
        assertEquals(1, created.size)
        assertEquals(LocalDate.of(2026, 10, 5), created.single().date)
        assertEquals(id, created.single().recurringId)
        assertEquals(LocalDate.of(2026, 11, 5), recurring.get(id)?.nextDueDate)
    }

    @Test
    fun skippingMovesTheTemplateWithoutCreatingAnything() = runBlocking {
        onboard()
        val id = recurring.save(template(LocalDate.of(2026, 10, 5)))
        recurring.skip(PendingOccurrence(checkNotNull(recurring.get(id)), LocalDate.of(2026, 10, 5)))
        assertTrue(transactions.observe(TransactionFilter(), categories.categories.first(), 10).first().isEmpty())
        assertEquals(LocalDate.of(2026, 11, 5), recurring.get(id)?.nextDueDate)
    }

    @Test
    fun deletingATemplateKeepsItsTransactionsAndClearsTheLink() = runBlocking {
        onboard()
        val id = recurring.save(template(LocalDate.of(2026, 10, 5)))
        recurring.confirm(PendingOccurrence(checkNotNull(recurring.get(id)), LocalDate.of(2026, 10, 5)), 1)
        recurring.delete(id)
        val remaining = transactions.observe(TransactionFilter(), categories.categories.first(), 10).first().single()
        assertEquals(null, remaining.recurringId)
    }

    // ---------- Бэкап ----------

    private fun populate() = runBlocking {
        onboard()
        settings.setReminder(ReminderSettings(ReminderMode.WEEKLY, 19, 15, 6))
        transactions.add(tx(100, "groceries", today, note = "Магнит; \"молоко\""), 1)
        transactions.add(tx(900, "salary", today, type = TransactionType.INCOME), 2)
        categories.save(categories.categories.first().first { it.name == "cafe" }, 300_000)
        recurring.save(template(LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun exportWipeImportRestoresTheExactState() = runBlocking {
        populate()
        val before = backup.snapshot()
        assertNotNull(before.profile)

        backup.wipe()
        val empty = backup.snapshot()
        assertEquals(null, empty.profile)
        assertTrue(empty.transactions.isEmpty() && empty.categories.isEmpty() && empty.goals.isEmpty())
        assertEquals(ReminderSettings(), empty.reminder)

        backup.importData(before, ImportMode.REPLACE)
        assertEquals(before, backup.snapshot())
    }

    @Test
    fun mergingTheSameBackupTwiceAddsNothing() = runBlocking {
        populate()
        val snapshot = backup.snapshot()
        backup.importData(snapshot, ImportMode.MERGE)
        backup.importData(snapshot, ImportMode.MERGE)
        assertEquals(snapshot, backup.snapshot())
    }

    @Test
    fun replaceRemovesDataThatIsNotInTheFile() = runBlocking {
        populate()
        val small = backup.snapshot().copy(transactions = emptyList())
        backup.importData(small, ImportMode.REPLACE)
        assertTrue(backup.snapshot().transactions.isEmpty())
    }

    // ---------- Виджет месяца ----------

    private fun widgetLoader(): WidgetStateLoader {
        val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        return WidgetStateLoader(stats, budget, settings, clock)
    }

    @Test
    fun widgetBeforeOnboardingIsNotConfigured() = runBlocking {
        val state = widgetLoader().load()
        assertEquals(false, state.configured)
        assertEquals(YearMonth.of(2026, 10), state.month)
        assertEquals(0L, state.incomeMinor + state.expensesMinor)
    }

    @Test
    fun widgetSumsOnlyTheCurrentCalendarMonth() = runBlocking {
        onboard()
        transactions.add(tx(100_000, "salary", today.withDayOfMonth(1), type = TransactionType.INCOME), 1)
        transactions.add(tx(30_000, "groceries", today.withDayOfMonth(1)), 2)
        transactions.add(tx(20_000, "cafe", today), 3)
        // Конец прошлого месяца и начало следующего в итоги не входят.
        transactions.add(tx(999_000, "groceries", today.withDayOfMonth(1).minusDays(1)), 4)
        transactions.add(tx(888_000, "salary", today.plusMonths(1).withDayOfMonth(1), type = TransactionType.INCOME), 5)
        val state = widgetLoader().load()
        assertEquals(true, state.configured)
        assertEquals(100_000L, state.incomeMinor)
        assertEquals(50_000L, state.expensesMinor)
        assertEquals(50_000L, state.balanceMinor)
        assertEquals(false, state.amountsHidden)
    }

    @Test
    fun widgetHidesAmountsWhileTheAppLockIsOn() = runBlocking {
        onboard()
        settings.setLockEnabled(true)
        assertEquals(true, widgetLoader().load().amountsHidden)
        settings.setLockEnabled(false)
        assertEquals(false, widgetLoader().load().amountsHidden)
    }

    // ---------- День начала месяца ----------

    private fun rowsFor(category: String, type: TransactionType) = runBlocking {
        stats.monthCategoryTotals.first()
            .filter { it.categoryId == categoryId(category) && it.type == type }
            .associate { it.month to it.totalMinor }
    }

    @Test
    fun monthTotalsAreCalendarMonthsByDefault() = runBlocking {
        onboard()
        transactions.add(tx(100, "groceries", LocalDate.of(2026, 10, 1)), 1)
        transactions.add(tx(200, "groceries", LocalDate.of(2026, 10, 31)), 2)
        transactions.add(tx(400, "groceries", LocalDate.of(2026, 11, 1)), 3)
        assertEquals(
            mapOf(YearMonth.of(2026, 10) to 300L, YearMonth.of(2026, 11) to 400L),
            rowsFor("groceries", TransactionType.EXPENSE),
        )
    }

    @Test
    fun monthTotalsFollowTheMonthStartDayAndNameThePeriodAfterItsStartMonth() = runBlocking {
        onboard()
        settings.setMonthStartDay(25)
        transactions.add(tx(1, "groceries", LocalDate.of(2026, 10, 24)), 1) // сентябрьский период
        transactions.add(tx(10, "groceries", LocalDate.of(2026, 10, 25)), 2) // октябрьский: 25.10–24.11
        transactions.add(tx(100, "groceries", LocalDate.of(2026, 11, 24)), 3)
        transactions.add(tx(1000, "groceries", LocalDate.of(2026, 11, 25)), 4) // ноябрьский
        assertEquals(
            mapOf(
                YearMonth.of(2026, 9) to 1L,
                YearMonth.of(2026, 10) to 110L,
                YearMonth.of(2026, 11) to 1000L,
            ),
            rowsFor("groceries", TransactionType.EXPENSE),
        )
    }

    @Test
    fun monthTotalsMatchTheDomainPeriodForEveryStartDay() = runBlocking {
        onboard()
        val end = LocalDate.of(2028, 4, 1)
        val dates = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.takeWhile { it < end }.toList()
        dates.forEachIndexed { index, date -> transactions.add(tx(1, "groceries", date), index.toLong()) }
        for (startDay in listOf(1, 2, 15, 27, 28)) {
            settings.setMonthStartDay(startDay)
            val perPeriod = dates.groupingBy { BudgetPeriod.containing(it, startDay) }.eachCount()
            val expected = perPeriod.mapValues { it.value.toLong() }
            assertEquals("startDay $startDay", expected, rowsFor("groceries", TransactionType.EXPENSE))
        }
    }

    @Test
    fun changingTheStartDayReEmitsTheTotals() = runBlocking {
        onboard()
        transactions.add(tx(5, "groceries", LocalDate.of(2026, 10, 10)), 1)
        val before = rowsFor("groceries", TransactionType.EXPENSE)
        settings.setMonthStartDay(15) // 10 октября теперь в период, начавшийся 15 сентября
        val after = rowsFor("groceries", TransactionType.EXPENSE)
        assertEquals(mapOf(YearMonth.of(2026, 10) to 5L), before)
        assertEquals(mapOf(YearMonth.of(2026, 9) to 5L), after)
    }

    @Test
    fun theStartDayIsClampedAndResetByWipe() = runBlocking {
        settings.setMonthStartDay(99)
        assertEquals(28, settings.current().monthStartDay)
        settings.setMonthStartDay(-3)
        assertEquals(1, settings.current().monthStartDay)
        settings.setMonthStartDay(17)
        onboard()
        backup.wipe()
        assertEquals(1, settings.current().monthStartDay)
    }

    @Test
    fun theStartDayTravelsWithBackups() = runBlocking {
        onboard()
        settings.setMonthStartDay(12)
        val saved = backup.snapshot()
        assertEquals(12, saved.monthStartDay)
        backup.wipe()
        backup.importData(saved, ImportMode.REPLACE)
        assertEquals(12, settings.current().monthStartDay)
        // Слияние не меняет настройку текущего пользователя.
        backup.importData(saved.copy(monthStartDay = 3), ImportMode.MERGE)
        assertEquals(12, settings.current().monthStartDay)
    }

    @Test
    fun widgetFollowsTheStartDay() = runBlocking {
        onboard()
        settings.setMonthStartDay(25)
        // «Сегодня» 10 октября: период начался 25 сентября.
        transactions.add(tx(40_000, "groceries", LocalDate.of(2026, 9, 25)), 1)
        transactions.add(tx(10_000, "groceries", LocalDate.of(2026, 9, 24)), 2) // прошлый период
        transactions.add(tx(70_000, "salary", LocalDate.of(2026, 10, 9), type = TransactionType.INCOME), 3)
        val state = widgetLoader().load()
        assertEquals(YearMonth.of(2026, 9), state.month)
        assertEquals(25, state.startDay)
        assertEquals(40_000L, state.expensesMinor)
        assertEquals(70_000L, state.incomeMinor)
    }

    // ---------- Регулярные операции по дням недели ----------

    private fun weeklyTemplate(next: LocalDate) = template(next).copy(
        dayOfMonth = 1,
        frequency = RecurrenceFrequency.WEEKLY,
        weekdays = setOf(1, 3, 5),
        notifyAtMinute = 8 * 60 + 30,
    )

    @Test
    fun aWeeklyTemplateKeepsItsDaysAndNotificationTime() = runBlocking {
        onboard()
        val id = recurring.save(weeklyTemplate(LocalDate.of(2026, 10, 12)))
        val saved = checkNotNull(recurring.get(id))
        assertEquals(RecurrenceFrequency.WEEKLY, saved.frequency)
        assertEquals(setOf(1, 3, 5), saved.weekdays)
        assertEquals(8 * 60 + 30, saved.notifyAtMinute)
    }

    @Test
    fun aTemplateWithoutNotificationStaysWithoutIt() = runBlocking {
        onboard()
        val id = recurring.save(template(LocalDate.of(2026, 10, 5)))
        val saved = checkNotNull(recurring.get(id))
        assertEquals(RecurrenceFrequency.MONTHLY, saved.frequency)
        assertEquals(emptySet<Int>(), saved.weekdays)
        assertEquals(null, saved.notifyAtMinute)
    }

    @Test
    fun handlingAWeeklyOccurrenceMovesItToTheNextSelectedWeekday() = runBlocking {
        onboard()
        val id = recurring.save(weeklyTemplate(LocalDate.of(2026, 10, 12))) // понедельник
        recurring.confirm(PendingOccurrence(checkNotNull(recurring.get(id)), LocalDate.of(2026, 10, 12)), 1)
        assertEquals(LocalDate.of(2026, 10, 14), recurring.get(id)?.nextDueDate) // среда
        recurring.skip(PendingOccurrence(checkNotNull(recurring.get(id)), LocalDate.of(2026, 10, 14)))
        assertEquals(LocalDate.of(2026, 10, 16), recurring.get(id)?.nextDueDate) // пятница
        recurring.skip(PendingOccurrence(checkNotNull(recurring.get(id)), LocalDate.of(2026, 10, 16)))
        assertEquals(LocalDate.of(2026, 10, 19), recurring.get(id)?.nextDueDate) // снова понедельник
        val created = transactions.observe(TransactionFilter(), categories.categories.first(), 10).first()
        assertEquals(1, created.size) // создана только подтверждённая; пропущенные операций не создают
    }

    @Test
    fun weeklyTemplatesSurviveBackupAndRestore() = runBlocking {
        onboard()
        val id = recurring.save(weeklyTemplate(LocalDate.of(2026, 10, 12)))
        val saved = checkNotNull(recurring.get(id))
        val snapshot = backup.snapshot()
        backup.wipe()
        backup.importData(snapshot, ImportMode.REPLACE)
        assertEquals(saved, recurring.templates.first().single())
    }

    // ---------- Кнопки уведомления и правка ожидающего списания ----------

    private fun handler() = RecurringOccurrenceHandler(
        recurring,
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
    )

    @Test
    fun confirmingFromTheNotificationCreatesTheOperationOnlyOnce() = runBlocking {
        onboard()
        val id = recurring.save(template(LocalDate.of(2026, 10, 5)))
        assertTrue(handler().confirm(id))
        assertTrue(!handler().confirm(id)) // следующее списание в ноябре, ждать больше нечего
        val created = transactions.observe(TransactionFilter(), categories.categories.first(), 10).first()
        assertEquals(1, created.size)
        assertEquals(id, created.single().recurringId)
        assertEquals(LocalDate.of(2026, 11, 5), recurring.get(id)?.nextDueDate)
    }

    @Test
    fun cancellingFromTheNotificationSkipsWithoutCreatingAnything() = runBlocking {
        onboard()
        val id = recurring.save(template(LocalDate.of(2026, 10, 5)))
        assertTrue(handler().skip(id))
        assertTrue(!handler().skip(id))
        assertTrue(transactions.observe(TransactionFilter(), categories.categories.first(), 10).first().isEmpty())
        assertEquals(LocalDate.of(2026, 11, 5), recurring.get(id)?.nextDueDate)
    }

    @Test
    fun notificationButtonsHandleTheOldestOverdueOccurrenceFirst() = runBlocking {
        onboard()
        val id = recurring.save(weeklyTemplate(LocalDate.of(2026, 10, 5))) // ждут 5, 7 и 9 октября
        assertEquals(LocalDate.of(2026, 10, 5), handler().oldestPending(id)?.dueDate)
        handler().skip(id)
        assertEquals(LocalDate.of(2026, 10, 7), handler().oldestPending(id)?.dueDate)
    }

    @Test
    fun notificationButtonsIgnoreAMissingTemplate() = runBlocking {
        assertTrue(!handler().confirm(404))
        assertTrue(!handler().skip(404))
    }

    @Test
    fun confirmingAnEditedOccurrenceKeepsTheEditsAndMovesTheTemplate() = runBlocking {
        onboard()
        val id = recurring.save(template(LocalDate.of(2026, 10, 5)))
        val edited = Transaction(
            type = TransactionType.EXPENSE,
            amountMinor = 1_234_500,
            categoryId = categoryId("loans"),
            date = LocalDate.of(2026, 10, 6),
            note = "Ипотека, с доплатой",
        )
        val savedId = recurring.confirmEdited(id, edited, today, 5_000)
        val saved = checkNotNull(transactions.get(savedId))
        assertEquals(1_234_500, saved.amountMinor)
        assertEquals(LocalDate.of(2026, 10, 6), saved.date)
        assertEquals(id, saved.recurringId)
        assertEquals(5_000, saved.createdAt)
        assertEquals(LocalDate.of(2026, 11, 5), recurring.get(id)?.nextDueDate)
    }

    @Test
    fun anEditedOccurrenceThatIsNoLongerWaitingIsStillSavedButDoesNotMoveTheTemplate() = runBlocking {
        onboard()
        val id = recurring.save(template(LocalDate.of(2026, 11, 5))) // ближайшее списание ещё впереди
        val edited = Transaction(
            type = TransactionType.EXPENSE,
            amountMinor = 700,
            categoryId = categoryId("loans"),
            date = today,
        )
        val saved = checkNotNull(transactions.get(recurring.confirmEdited(id, edited, today, 1)))
        assertEquals(null, saved.recurringId)
        assertEquals(LocalDate.of(2026, 11, 5), recurring.get(id)?.nextDueDate)
    }
}
