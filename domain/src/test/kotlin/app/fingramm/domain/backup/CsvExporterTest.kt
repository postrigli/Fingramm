package app.fingramm.domain.backup

import app.fingramm.domain.model.CurrencyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    private val bom = 0xFEFF.toChar().toString()

    private val headers = CsvHeaders("Дата", "Тип", "Сумма", "Валюта", "Категория", "Вид", "Счёт", "Комментарий")
    private val labels = CsvLabels("Доход", "Расход", "Обязательный", "Переменный", "Наличные", "Карта", "Накопления")

    private fun export() = CsvExporter.export(
        TestData.backup().transactions,
        TestData.backup().categories,
        CurrencyCode.RUB,
        headers,
        labels,
    )

    @Test
    fun `starts with BOM and header and uses CRLF`() {
        val csv = export()
        assertTrue(csv.startsWith(bom + "Дата;Тип;Сумма;Валюта;Категория;Вид;Счёт;Комментарий\r\n"))
        assertEquals(4, csv.trimEnd().split("\r\n").size)
    }

    @Test
    fun `rows are sorted by date and escaped`() {
        val rows = export().trimEnd().split("\r\n").drop(1)
        assertEquals("2026-10-05;Доход;120000,00;RUB;Зарплата;;Карта;Аванс", rows[0])
        assertEquals("2026-10-09;Расход;500,00;RUB;Кафе;Переменный;;", rows[1])
        assertEquals("2026-10-10;Расход;1234,56;RUB;Продукты;Обязательный;Наличные;\"Магнит; \"\"молоко\"\"\"", rows[2])
    }

    @Test
    fun `formula injection is neutralised`() {
        assertEquals("'=SUM(A1)", CsvExporter.cell("=SUM(A1)"))
        assertEquals("'+7", CsvExporter.cell("+7"))
        assertEquals("'@cmd", CsvExporter.cell("@cmd"))
        assertEquals("plain", CsvExporter.cell("plain"))
        assertEquals("\"line1\nline2\"", CsvExporter.cell("line1\nline2"))
    }

    @Test
    fun `amount formatting`() {
        assertEquals("0,05", CsvExporter.amount(5))
        assertEquals("1,00", CsvExporter.amount(100))
        assertEquals("-0,50", CsvExporter.amount(-50))
        assertEquals("-1,50", CsvExporter.amount(-150))
    }
}
