package app.fingramm.domain.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsCalculatorTest {
    private fun rub(major: Long) = major * 100

    private fun flow(income: Long, mandatory: Long, variable: Long, debt: Long) =
        CashFlow(rub(income), rub(mandatory), rub(variable), rub(debt))

    private fun position(cushion: Long = 0, savings: Long = cushion, assets: Long = 0, debt: Long = 0) =
        Position(rub(cushion), rub(savings), rub(assets), rub(debt))

    // ---------- Контрольный набор 1: обычная семья ----------

    @Test
    fun `dataset 1 - typical family is healthy`() {
        val report = MetricsCalculator.calculate(
            flow(income = 100_000, mandatory = 45_000, variable = 25_000, debt = 10_000),
            position(cushion = 300_000, savings = 450_000, assets = 1_000_000, debt = 400_000),
        )
        assertEquals(rub(20_000), report[MetricId.FREE_CASH_FLOW].moneyMinor)
        assertEquals(MetricStatus.GOOD, report[MetricId.FREE_CASH_FLOW].status)
        assertEquals(0.2, report[MetricId.SAVINGS_RATE].ratio!!, 1e-9)
        assertEquals(MetricStatus.GOOD, report[MetricId.SAVINGS_RATE].status)
        assertEquals(0.1, report[MetricId.DEBT_LOAD].ratio!!, 1e-9)
        assertEquals(MetricStatus.GOOD, report[MetricId.DEBT_LOAD].status)
        assertEquals(3.75, report[MetricId.CUSHION_MONTHS].months!!, 1e-9)
        assertEquals(MetricStatus.GOOD, report[MetricId.CUSHION_MONTHS].status)
        assertEquals(0.45, report[MetricId.MANDATORY_SHARE].ratio!!, 1e-9)
        assertEquals(MetricStatus.GOOD, report[MetricId.MANDATORY_SHARE].status)
        // 450 000 + 1 000 000 − 400 000
        assertEquals(rub(1_050_000), report[MetricId.NET_WORTH].moneyMinor)
        assertEquals(MetricStatus.NEUTRAL, report[MetricId.NET_WORTH].status)
    }

    // ---------- Контрольный набор 2: нулевой доход ----------

    @Test
    fun `dataset 2 - zero income`() {
        val report = MetricsCalculator.calculate(
            flow(income = 0, mandatory = 30_000, variable = 10_000, debt = 0),
            position(cushion = 60_000),
        )
        assertEquals(rub(-40_000), report[MetricId.FREE_CASH_FLOW].moneyMinor)
        assertEquals(MetricStatus.RISK, report[MetricId.FREE_CASH_FLOW].status)

        val savings = report[MetricId.SAVINGS_RATE]
        assertFalse(savings.isDefined)
        assertEquals(MetricStatus.RISK, savings.status)

        // Платежей нет — долговой нагрузки нет, даже без дохода.
        assertEquals(0.0, report[MetricId.DEBT_LOAD].ratio!!, 1e-9)
        assertEquals(MetricStatus.GOOD, report[MetricId.DEBT_LOAD].status)

        val share = report[MetricId.MANDATORY_SHARE]
        assertFalse(share.isDefined)
        assertEquals(MetricStatus.RISK, share.status)

        assertEquals(1.5, report[MetricId.CUSHION_MONTHS].months!!, 1e-9)
        assertEquals(MetricStatus.ATTENTION, report[MetricId.CUSHION_MONTHS].status)
    }

    @Test
    fun `zero income with debt payments is a debt load risk`() {
        val result = MetricsCalculator.debtLoad(flow(0, 0, 0, 5_000))
        assertFalse(result.isDefined)
        assertEquals(MetricStatus.RISK, result.status)
    }

    // ---------- Контрольный набор 3: долги больше дохода ----------

    @Test
    fun `dataset 3 - debts exceed income`() {
        val report = MetricsCalculator.calculate(
            flow(income = 50_000, mandatory = 30_000, variable = 10_000, debt = 60_000),
            position(cushion = 0, savings = 0, assets = 0, debt = 2_000_000),
        )
        assertEquals(rub(-50_000), report[MetricId.FREE_CASH_FLOW].moneyMinor)
        assertEquals(MetricStatus.RISK, report[MetricId.FREE_CASH_FLOW].status)
        assertEquals(-1.0, report[MetricId.SAVINGS_RATE].ratio!!, 1e-9)
        assertEquals(MetricStatus.RISK, report[MetricId.SAVINGS_RATE].status)
        assertEquals(1.2, report[MetricId.DEBT_LOAD].ratio!!, 1e-9)
        assertEquals(MetricStatus.RISK, report[MetricId.DEBT_LOAD].status)
        assertEquals(0.0, report[MetricId.CUSHION_MONTHS].months!!, 1e-9)
        assertEquals(MetricStatus.RISK, report[MetricId.CUSHION_MONTHS].status)
        assertEquals(rub(-2_000_000), report[MetricId.NET_WORTH].moneyMinor)
        assertEquals(MetricStatus.ATTENTION, report[MetricId.NET_WORTH].status)
    }

    // ---------- Граничные случаи ----------

    @Test
    fun `no income and no expenses is all neutral or good`() {
        val report = MetricsCalculator.calculate(flow(0, 0, 0, 0), position())
        assertEquals(MetricStatus.NEUTRAL, report[MetricId.FREE_CASH_FLOW].status)
        assertEquals(MetricStatus.NEUTRAL, report[MetricId.SAVINGS_RATE].status)
        assertEquals(MetricStatus.GOOD, report[MetricId.DEBT_LOAD].status)
        assertEquals(MetricStatus.NEUTRAL, report[MetricId.CUSHION_MONTHS].status)
        assertEquals(MetricStatus.NEUTRAL, report[MetricId.MANDATORY_SHARE].status)
        assertEquals(0L, report[MetricId.NET_WORTH].moneyMinor)
    }

    @Test
    fun `income without expenses has full savings rate and undefined cushion`() {
        val report = MetricsCalculator.calculate(flow(80_000, 0, 0, 0), position(cushion = 100_000))
        assertEquals(1.0, report[MetricId.SAVINGS_RATE].ratio!!, 1e-9)
        assertEquals(MetricStatus.GOOD, report[MetricId.SAVINGS_RATE].status)
        assertNull(report[MetricId.CUSHION_MONTHS].months)
        assertEquals(MetricStatus.NEUTRAL, report[MetricId.CUSHION_MONTHS].status)
        assertEquals(0.0, report[MetricId.MANDATORY_SHARE].ratio!!, 1e-9)
        assertEquals(MetricStatus.GOOD, report[MetricId.MANDATORY_SHARE].status)
    }

    @Test
    fun `savings rate thresholds`() {
        // Ровно 10% — норма, на копейку меньше — внимание.
        assertEquals(MetricStatus.GOOD, MetricsCalculator.savingsRate(CashFlow(10_000, 9_000, 0, 0)).status)
        assertEquals(MetricStatus.ATTENTION, MetricsCalculator.savingsRate(CashFlow(10_000, 9_001, 0, 0)).status)
        assertEquals(MetricStatus.ATTENTION, MetricsCalculator.savingsRate(CashFlow(10_000, 10_000, 0, 0)).status)
        assertEquals(MetricStatus.RISK, MetricsCalculator.savingsRate(CashFlow(10_000, 10_001, 0, 0)).status)
    }

    @Test
    fun `free cash flow thresholds`() {
        assertEquals(MetricStatus.GOOD, MetricsCalculator.freeCashFlow(CashFlow(10_000, 9_000, 0, 0)).status)
        assertEquals(MetricStatus.ATTENTION, MetricsCalculator.freeCashFlow(CashFlow(10_000, 9_500, 0, 0)).status)
        assertEquals(MetricStatus.ATTENTION, MetricsCalculator.freeCashFlow(CashFlow(10_000, 10_000, 0, 0)).status)
        assertEquals(MetricStatus.RISK, MetricsCalculator.freeCashFlow(CashFlow(10_000, 10_001, 0, 0)).status)
    }

    @Test
    fun `debt load thresholds`() {
        fun status(payment: Long) = MetricsCalculator.debtLoad(CashFlow(10_000, 0, 0, payment)).status
        assertEquals(MetricStatus.GOOD, status(3_000))
        assertEquals(MetricStatus.ATTENTION, status(3_001))
        assertEquals(MetricStatus.ATTENTION, status(4_000))
        assertEquals(MetricStatus.RISK, status(4_001))
    }

    @Test
    fun `mandatory share thresholds`() {
        fun status(mandatory: Long) = MetricsCalculator.mandatoryShare(CashFlow(10_000, mandatory, 0, 0)).status
        assertEquals(MetricStatus.GOOD, status(5_000))
        assertEquals(MetricStatus.ATTENTION, status(5_001))
        assertEquals(MetricStatus.ATTENTION, status(7_000))
        assertEquals(MetricStatus.RISK, status(7_001))
    }

    @Test
    fun `cushion months thresholds`() {
        val flow = CashFlow(0, 1_000, 0, 0)
        fun status(cushion: Long) = MetricsCalculator.cushionMonths(flow, Position(cushion, cushion, 0, 0)).status
        assertEquals(MetricStatus.RISK, status(999))
        assertEquals(MetricStatus.ATTENTION, status(1_000))
        assertEquals(MetricStatus.ATTENTION, status(2_999))
        assertEquals(MetricStatus.GOOD, status(3_000))
        assertEquals(MetricStatus.GOOD, status(9_000))
    }

    @Test
    fun `cushion months counts debt payments as outflow`() {
        val result = MetricsCalculator.cushionMonths(
            CashFlow(0, 1_000, 1_000, 2_000),
            Position(8_000, 8_000, 0, 0),
        )
        assertEquals(2.0, result.months!!, 1e-9)
    }

    @Test
    fun `net worth excludes nothing it is given and flags negative`() {
        val result = MetricsCalculator.netWorth(Position(0, 1_000, 5_000, 7_000))
        assertEquals(-1_000L, result.moneyMinor)
        assertEquals(MetricStatus.ATTENTION, result.status)
        assertTrue(MetricsCalculator.netWorth(Position(0, 0, 0, 0)).status == MetricStatus.NEUTRAL)
    }

    @Test
    fun `report keeps a stable metric order`() {
        val ids = MetricsCalculator.calculate(flow(1, 1, 1, 1), position()).results.map { it.id }
        assertEquals(MetricId.entries.toList(), ids)
    }
}
