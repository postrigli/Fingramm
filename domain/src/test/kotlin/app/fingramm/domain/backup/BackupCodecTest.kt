package app.fingramm.domain.backup

import app.fingramm.domain.model.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun failure(text: String) = BackupCodec.decode(text) as BackupDecodeResult.Failure

    @Test
    fun `export then import restores identical data`() {
        val data = TestData.backup()
        val text = BackupCodec.encode(BackupFile(exportedAt = "2026-10-10T10:00:00Z", data = data))
        val result = BackupCodec.decode(text) as BackupDecodeResult.Success
        assertEquals(data, result.file.data)
        assertEquals(BACKUP_SCHEMA_VERSION, result.file.schemaVersion)
    }

    @Test
    fun `empty backup round trips`() {
        val text = BackupCodec.encode(BackupFile(data = BackupData()))
        assertEquals(BackupData(), (BackupCodec.decode(text) as BackupDecodeResult.Success).file.data)
    }

    @Test
    fun `dates are stored as ISO strings and the file carries a version`() {
        val text = BackupCodec.encode(BackupFile(data = TestData.backup()))
        assertTrue(text.contains("\"schemaVersion\": 1"))
        assertTrue(text.contains("\"createdAt\": \"2026-08-10\""))
        assertTrue(text.contains("\"app\": \"fingramm\""))
    }

    @Test
    fun `garbage is malformed`() {
        assertEquals(BackupError.MALFORMED, failure("not json").error)
        assertEquals(BackupError.MALFORMED, failure("").error)
        assertEquals(BackupError.MALFORMED, failure("[]").error)
    }

    @Test
    fun `unknown enum value is malformed`() {
        val text = BackupCodec.encode(BackupFile(data = TestData.backup())).replace("\"RUB\"", "\"GBP\"")
        assertEquals(BackupError.MALFORMED, failure(text).error)
    }

    @Test
    fun `bad date is malformed`() {
        val text = BackupCodec.encode(BackupFile(data = TestData.backup())).replace("2026-08-10", "2026-13-45")
        assertEquals(BackupError.MALFORMED, failure(text).error)
    }

    @Test
    fun `foreign json is not a backup`() {
        assertEquals(BackupError.NOT_A_BACKUP, failure("""{"app":"other","data":{}}""").error)
    }

    @Test
    fun `newer schema is rejected`() {
        val text = BackupCodec.encode(BackupFile(schemaVersion = BACKUP_SCHEMA_VERSION + 1, data = BackupData()))
        assertEquals(BackupError.NEWER_VERSION, failure(text).error)
    }

    @Test
    fun `unknown keys are ignored for forward compatibility`() {
        val text = BackupCodec.encode(BackupFile(data = BackupData())).replace("\"app\"", "\"future\": 1, \"app\"")
        assertTrue(BackupCodec.decode(text) is BackupDecodeResult.Success)
    }

    @Test
    fun `dangling references are invalid data`() {
        val data = TestData.backup()
        val broken = data.copy(transactions = data.transactions.map { it.copy(categoryId = 999) })
        val result = failure(BackupCodec.encode(BackupFile(data = broken)))
        assertEquals(BackupError.INVALID_DATA, result.error)
        assertTrue(result.details.any { it.contains("999") })
    }

    @Test
    fun `validator catches duplicates negatives and mismatched profile`() {
        val data = TestData.backup()
        val issues = BackupValidator.validate(
            data.copy(
                survey = null,
                categories = data.categories + data.categories.first(),
                transactions = data.transactions.map { it.copy(amountMinor = 0) },
                goals = data.goals.map { it.copy(savedMinor = -1) },
                reminder = data.reminder.copy(hour = 25),
            ),
        )
        assertTrue(issues.any { it.contains("profile") })
        assertTrue(issues.any { it.contains("categories") })
        assertTrue(issues.any { it.contains("сумма должна быть") })
        assertTrue(issues.any { it.contains("накоплено") })
        assertTrue(issues.any { it.contains("reminder") })
    }

    @Test
    fun `weekly recurring templates survive a round trip`() {
        val base = TestData.backup()
        val weekly = base.recurringTemplates.first().copy(
            frequency = RecurrenceFrequency.WEEKLY,
            weekdays = setOf(1, 3, 5),
            notifyAtMinute = 8 * 60 + 15,
        )
        val data = base.copy(recurringTemplates = listOf(weekly) + base.recurringTemplates.drop(1))
        val decoded = BackupCodec.decode(BackupCodec.encode(BackupFile(data = data))) as BackupDecodeResult.Success
        assertEquals(weekly, decoded.file.data.recurringTemplates.first())
    }

    @Test
    fun `old recurring templates are monthly without a notification`() {
        val old = TestData.backup().recurringTemplates.first()
        assertEquals(RecurrenceFrequency.MONTHLY, old.frequency)
        assertEquals(emptySet<Int>(), old.weekdays)
        assertEquals(null, old.notifyAtMinute)
    }

    @Test
    fun `validator checks weekdays and notification time of recurring templates`() {
        val data = TestData.backup()
        val template = data.recurringTemplates.first()
        fun issues(changed: app.fingramm.domain.model.RecurringTemplate) =
            BackupValidator.validate(data.copy(recurringTemplates = listOf(changed)))

        val noDays = template.copy(frequency = RecurrenceFrequency.WEEKLY, weekdays = emptySet())
        assertTrue(issues(noDays).any { it.contains("дни недели") })
        val badDay = template.copy(frequency = RecurrenceFrequency.WEEKLY, weekdays = setOf(8))
        assertTrue(issues(badDay).any { it.contains("дни недели") })
        assertTrue(issues(template.copy(notifyAtMinute = 1440)).any { it.contains("время уведомления") })
        assertTrue(issues(template.copy(notifyAtMinute = -1)).any { it.contains("время уведомления") })
        val valid = template.copy(frequency = RecurrenceFrequency.WEEKLY, weekdays = setOf(7), notifyAtMinute = 1439)
        assertTrue(issues(valid).none { it.contains("recurring") })
    }

    @Test
    fun `month start day survives a round trip`() {
        val data = TestData.backup().copy(monthStartDay = 17)
        val decoded = BackupCodec.decode(BackupCodec.encode(BackupFile(data = data)))
        assertEquals(17, (decoded as BackupDecodeResult.Success).file.data.monthStartDay)
    }

    @Test
    fun `a file without month start day is the calendar month`() {
        val json = """{"app":"fingramm","schemaVersion":1,"data":{}}"""
        val decoded = BackupCodec.decode(json) as BackupDecodeResult.Success
        assertEquals(1, decoded.file.data.monthStartDay)
    }

    @Test
    fun `month start day outside 1 to 28 is invalid`() {
        listOf(0, 29, -1).forEach { day ->
            val issues = BackupValidator.validate(TestData.backup().copy(monthStartDay = day))
            assertTrue("day $day", issues.any { it.contains("monthStartDay") })
        }
        listOf(1, 28).forEach { day ->
            assertTrue(BackupValidator.validate(TestData.backup().copy(monthStartDay = day)).isEmpty())
        }
    }

    @Test
    fun `valid data has no issues`() {
        assertTrue(BackupValidator.validate(TestData.backup()).isEmpty())
    }
}
