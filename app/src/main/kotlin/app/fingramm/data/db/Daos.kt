package app.fingramm.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.fingramm.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM budget_profile WHERE id = $SINGLETON_ID")
    fun observeProfile(): Flow<BudgetProfileEntity?>

    @Query("SELECT * FROM budget_profile WHERE id = $SINGLETON_ID")
    suspend fun getProfile(): BudgetProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: BudgetProfileEntity)

    @Query("SELECT * FROM survey_answers WHERE id = $SINGLETON_ID")
    fun observeSurvey(): Flow<SurveyAnswersEntity?>

    @Query("SELECT * FROM survey_answers WHERE id = $SINGLETON_ID")
    suspend fun getSurvey(): SurveyAnswersEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSurvey(survey: SurveyAnswersEntity)

    @Query("SELECT * FROM income_source ORDER BY id")
    fun observeIncomeSources(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM income_source ORDER BY id")
    suspend fun getIncomeSources(): List<IncomeSourceEntity>

    @Insert
    suspend fun insertIncomeSources(items: List<IncomeSourceEntity>)

    @Query("DELETE FROM income_source")
    suspend fun clearIncomeSources()

    @Query("SELECT * FROM asset ORDER BY id")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM asset ORDER BY id")
    suspend fun getAssets(): List<AssetEntity>

    @Insert
    suspend fun insertAssets(items: List<AssetEntity>)

    @Query("DELETE FROM asset")
    suspend fun clearAssets()

    @Query("SELECT * FROM debt ORDER BY id")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debt ORDER BY id")
    suspend fun getDebts(): List<DebtEntity>

    @Insert
    suspend fun insertDebts(items: List<DebtEntity>)

    @Query("DELETE FROM debt")
    suspend fun clearDebts()

    @Query("DELETE FROM survey_answers")
    suspend fun clearSurvey()

    @Query("DELETE FROM budget_profile")
    suspend fun clearProfile()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY archived, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM category")
    suspend fun clear()

    @Query("SELECT * FROM category_limit")
    fun observeLimits(): Flow<List<CategoryLimitEntity>>

    @Query("SELECT * FROM category_limit")
    suspend fun getLimits(): List<CategoryLimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimit(limit: CategoryLimitEntity)

    @Insert
    suspend fun insertLimits(limits: List<CategoryLimitEntity>)

    @Query("DELETE FROM category_limit WHERE categoryId = :categoryId")
    suspend fun deleteLimit(categoryId: Long)

    @Query("DELETE FROM category_limit")
    suspend fun clearLimits()
}

/** Сумма операций типа в категории за расчётный месяц (`ym` = `yyyy-MM` месяца начала периода). */
data class MonthCategoryRow(
    val ym: String,
    val categoryId: Long,
    val type: TransactionType,
    val total: Long,
)

data class CategoryUsageRow(val categoryId: Long, val uses: Int)

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT * FROM transaction_entry
        WHERE (:fromDate IS NULL OR date >= :fromDate)
          AND (:toDate IS NULL OR date <= :toDate)
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:query = '' OR noteLower LIKE '%' || :query || '%' ESCAPE '\' OR categoryId IN (:matchingCategoryIds))
        ORDER BY date DESC, id DESC
        LIMIT :limit
        """,
    )
    @Suppress("LongParameterList")
    fun observeFiltered(
        fromDate: String?,
        toDate: String?,
        type: String?,
        categoryId: Long?,
        query: String,
        matchingCategoryIds: List<Long>,
        limit: Int,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transaction_entry WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transaction_entry ORDER BY id")
    suspend fun getAll(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity): Long

    @Insert
    suspend fun insertAll(items: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Delete
    suspend fun delete(entity: TransactionEntity)

    @Query("DELETE FROM transaction_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transaction_entry")
    suspend fun clear()

    /**
     * Итоги по расчётным месяцам. Дата сдвигается на [shiftDays] назад (день начала месяца − 1) и обрезается
     * до `yyyy-MM`: при сдвиге 0 это календарный месяц. Тот же сдвиг делает `BudgetPeriod.containing`.
     */
    @Query(
        """
        SELECT substr(date(date, '-' || :shiftDays || ' days'), 1, 7) AS ym, categoryId, type, SUM(amountMinor) AS total
        FROM transaction_entry
        GROUP BY ym, categoryId, type
        """,
    )
    fun observeMonthCategoryTotals(shiftDays: Int): Flow<List<MonthCategoryRow>>

    @Query(
        """
        SELECT categoryId, COUNT(*) AS uses FROM transaction_entry
        WHERE type = :type GROUP BY categoryId
        """,
    )
    fun observeCategoryUsage(type: String): Flow<List<CategoryUsageRow>>

    @Query("SELECT COUNT(*) FROM transaction_entry WHERE createdAt >= :sinceMillis")
    suspend fun countCreatedSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM transaction_entry")
    fun observeCount(): Flow<Int>
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_template ORDER BY id")
    fun observeAll(): Flow<List<RecurringTemplateEntity>>

    @Query("SELECT * FROM recurring_template ORDER BY id")
    suspend fun getAll(): List<RecurringTemplateEntity>

    @Query("SELECT * FROM recurring_template WHERE id = :id")
    suspend fun getById(id: Long): RecurringTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecurringTemplateEntity): Long

    @Insert
    suspend fun insertAll(items: List<RecurringTemplateEntity>)

    @Update
    suspend fun update(entity: RecurringTemplateEntity)

    @Query("DELETE FROM recurring_template WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recurring_template")
    suspend fun clear()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goal ORDER BY horizon, id")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal ORDER BY id")
    suspend fun getAll(): List<GoalEntity>

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun getById(id: Long): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GoalEntity): Long

    @Insert
    suspend fun insertAll(items: List<GoalEntity>)

    @Update
    suspend fun update(entity: GoalEntity)

    @Query("DELETE FROM goal WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM goal")
    suspend fun clear()
}
