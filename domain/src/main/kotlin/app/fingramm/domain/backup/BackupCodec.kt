package app.fingramm.domain.backup

import kotlinx.serialization.json.Json
import java.time.DateTimeException

public enum class BackupError {
    /** Не JSON или нарушена структура (в т.ч. неизвестные значения перечислений, неверные даты). */
    MALFORMED,
    NOT_A_BACKUP,
    NEWER_VERSION,
    INVALID_DATA,
}

public sealed interface BackupDecodeResult {
    public data class Success(val file: BackupFile) : BackupDecodeResult

    public data class Failure(val error: BackupError, val details: List<String> = emptyList()) : BackupDecodeResult
}

public object BackupCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    public fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Разбирает и проверяет бэкап. Ничего не применяет — решение о замене/слиянии принимает пользователь. */
    public fun decode(text: String): BackupDecodeResult {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // включает SerializationException
            return malformed(e.message?.lineSequence()?.first())
        } catch (e: DateTimeException) {
            return malformed(e.message)
        }
        return check(file)
    }

    private fun malformed(detail: String?) = BackupDecodeResult.Failure(BackupError.MALFORMED, listOfNotNull(detail))

    private fun check(file: BackupFile): BackupDecodeResult {
        if (file.app != BACKUP_APP_ID) return BackupDecodeResult.Failure(BackupError.NOT_A_BACKUP)
        if (file.schemaVersion !in 1..BACKUP_SCHEMA_VERSION) {
            return BackupDecodeResult.Failure(BackupError.NEWER_VERSION, listOf(file.schemaVersion.toString()))
        }
        val issues = BackupValidator.validate(file.data)
        return if (issues.isEmpty()) {
            BackupDecodeResult.Success(file)
        } else {
            BackupDecodeResult.Failure(BackupError.INVALID_DATA, issues)
        }
    }
}
