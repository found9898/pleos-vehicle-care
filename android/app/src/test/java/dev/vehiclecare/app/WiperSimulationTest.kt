package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The blade-age model against a simulated Korean year. Rain from Gimpo airport reports 2015-2024 (733 hours a year,
 * July and August 27 %), 30.8 km of driving a day at 35 km/h, and three switch habits (materials/04_research/wiper
 * model-inputs b-4). The app counts each habit with its own rate bounds; the "true" rates are the habit's assumed ones.
 */
class WiperSimulationTest {
    /** Gimpo rain hours per month, January first. */
    private val rainHours = doubleArrayOf(43.0, 48.0, 37.0, 58.0, 70.0, 60.0, 109.0, 92.0, 65.0, 41.0, 57.0, 52.0)
    private val days = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /** Switch share and the habit's true wipes per minute: intermittent, low, high. */
    private data class Habit(val name: String, val hoursScale: Double, val share: List<Pair<WiperModel.Level, Pair<Double, Double>>>)
    private val light = Habit("적음", 15.0 / 27.3, listOf(WiperModel.Level.INTERMITTENT to (.65 to 10.0), WiperModel.Level.LOW to (.30 to 45.0), WiperModel.Level.HIGH to (.05 to 65.0)))
    private val central = Habit("중앙", 1.0, listOf(WiperModel.Level.INTERMITTENT to (.50 to 15.0), WiperModel.Level.LOW to (.40 to 45.0), WiperModel.Level.HIGH to (.10 to 65.0)))
    private val heavy = Habit("많음", 49.5 / 27.3, listOf(WiperModel.Level.INTERMITTENT to (.30 to 20.0), WiperModel.Level.LOW to (.50 to 50.0), WiperModel.Level.HIGH to (.20 to 70.0)))

    /** One year: the app's count and the true count. Driving hours a day times the month's share of rain hours. */
    private fun year(h: Habit, hoursScale: Double = h.hoursScale): Triple<WiperCycles, Double, Double> {
        val c = WiperCycles(); var ms = 0L; var truth = 0.0; var wiperHours = 0.0
        for (m in 0 until 12) {
            val minutes = 30.8 / 35 * 60 * days[m] * rainHours[m] / (days[m] * 24) * hoursScale
            wiperHours += minutes / 60
            for ((level, sr) in h.share) {
                val (share, rate) = sr
                c.update(level, ms); ms += (minutes * share * 60_000).toLong(); c.update(null, ms)
                truth += minutes * share * rate
            }
        }
        return Triple(c, truth, wiperHours)
    }

    @Test fun aCentralSeoulDriverWipesAboutFiftyThousandTimesAYear() {
        val rows = listOf(light, central, heavy).map { h -> h to year(h) }
        rows.forEach { (h, y) ->
            println("%s: 와이퍼 %.1f h, 실제 %,.0f회, 앱 %,.0f~%,.0f회, 앱 하한으로 더한 노화 %.0f일".format(
                h.name, y.third, y.second, y.first.low, y.first.high, WiperModel.excessDays(y.first.low, 365.0)))
            // The app's bounds hold the truth.
            assertTrue(y.first.low <= y.second && y.second <= y.first.high)
        }
        val (_, c) = rows[1]
        assertEquals(27.3, c.third, 1.5)
        assertEquals(52_000.0, c.second, 4_000.0)
        // The typical driver's wiping never moves the blade's age; only the lower count is used.
        assertEquals(0.0, WiperModel.excessDays(c.first.low, 365.0), 0.0)
    }

    /** First day the blade is due; [perYear] lower-bound wipes a year accumulate evenly. */
    private fun dueDay(from: LocalDate, outside: OutsideDays, perYear: Double = 0.0): Long {
        val plan = RecommendedCare.plan(Part.WIPER, "r")
        val record = ServiceRecord("r", Part.WIPER, from, null, ServiceAction.REPLACE)
        return (1L..400L).first { d ->
            val cycles = WiperCycles(perYear * d / 365, perYear * d / 365 * 1.6, mutableMapOf("r" to (0.0 to 0.0)))
            CompositeJudge.ageing(plan, record, from.plusDays(d), outside, cycles)!!.first == DueStatus.DUE
        }
    }

    @Test fun aDriverWipingTwoHundredThousandAYearIsDueByWipingWellBeforeAYear() {
        val from = LocalDate.of(2026, 1, 1)
        // A taxi-like pace: 250,000 true wipes a year, counted on the lower bound at 180,000.
        val plain = dueDay(from, OutsideDays())
        val busy = dueDay(from, OutsideDays(), 180_000.0)
        val typical = dueDay(from, OutsideDays(), WiperModel.TYPICAL_PER_YEAR)
        println("연 18만 회(하한) 운전자: %d일에 교체, 보통 %d일".format(busy, plain))
        assertEquals(365L, plain); assertEquals(365L, typical)
        assertTrue(busy < 300)
    }

    @Test fun sunAndHeatBringTheBladeIntoTheSixToTwelveMonthsFieldTestsSee() {
        val from = LocalDate.of(2026, 3, 1)
        // Parked in the sun every day from April to September, and July-August afternoons 3 °C over the normal.
        val sunny = OutsideDays()
        var d = LocalDate.of(2026, 4, 1)
        while (d.isBefore(LocalDate.of(2026, 10, 1))) {
            sunny.soak(d, 45.0, 30.0)
            if (d.monthValue in 7..8) sunny.add(d.atTime(15, 0), RubberAging.normalAt(d.monthValue, 15.0) + 3)
            d = d.plusDays(1)
        }
        val sun = dueDay(from, sunny); val shade = dueDay(from, OutsideDays())
        println("햇볕 주차(4~9월)와 더운 여름: %d일(%.1f개월), 그늘 %d일, 비 %.2f".format(sun, sun / 30.4, shade, shade.toDouble() / sun))
        // Consumer Reports saw most blades fall off at 6-9 months; Korea's most common replacement is 7-12 months.
        assertTrue(sun / 30.4 in 7.0..12.0)
        // Shade lasts 2-3 times longer than full sun (Champion): a partly sunny year must stay well inside that.
        assertTrue(shade.toDouble() / sun < 2.0)
    }
}
