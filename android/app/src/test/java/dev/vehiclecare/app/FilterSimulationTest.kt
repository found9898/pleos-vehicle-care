package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The cabin filter's dust load against the anchors in materials/04_research/cabin_filter/model-inputs.md: Wei's Shanghai
 * ageing, Korea's mean air, a commuter and a taxi, and how much recirculation saves. A year is driven day by day through
 * the production FilterDust, one fresh-air and one recirculated stretch a day.
 */
class FilterSimulationTest {
    private val hourMs = 3_600_000L

    @Test fun weisShanghaiHourIsOneHour() {
        assertEquals(1.0, FilterModel.weight(FilterModel.WEI_FLOW, 4, FilterModel.WEI_PM25), 1e-9)
        assertEquals(500.0, 500 * FilterModel.weight(FilterModel.WEI_FLOW, 4, FilterModel.WEI_PM25), 1e-9)
    }

    /** One year from [start]: [freshH] and [recircH] hours a day at [m3h], plus [ramH] hours of ram air with the blower off. */
    private fun year(freshH: Double, recircH: Double, m3h: Double, ramH: Double = 0.0, start: LocalDate = LocalDate.of(2026, 1, 1), days: Int = 365): Pair<FilterDust, Int?> {
        val f = FilterDust(); f.markBaseline("r")
        var ms = 0L; var full: Int? = null
        for (d in 0 until days) {
            val month = start.plusDays(d.toLong()).monthValue
            for ((h, flow) in listOf(freshH to m3h, recircH to FilterModel.RECIRC_SHARE * m3h, ramH to FilterModel.RAM_FLOW)) {
                if (h <= 0) continue
                f.update(flow, ms, month); ms += (h * hourMs).toLong(); f.update(0.0, ms, month)
            }
            if (full == null && f.since("r")!! >= FilterModel.END_OF_SERVICE_HOURS) full = d + 1
        }
        return f to full
    }

    @Test fun koreasMeanAirTakesAboutThirteenHundredLowSpeedHours() {
        // Blowing fresh air at 167 m³/h, 4 h a day, until the filter holds Wei's load.
        val (_, full) = year(4.0, 0.0, FilterModel.WEI_FLOW, days = 730)
        val hours = full!! * 4
        println("한국 평균 공기, 저속 외기: %,d시간에 가득(상하이 시험은 500시간)".format(hours))
        assertTrue(hours in 1_150..1_450)
    }

    @Test fun aCommuterIsReplacedByTheCalendarAndATaxiByTheDust() {
        val (commute, commuteFull) = year(0.6, 0.4, 200.0)
        val (taxi, taxiFull) = year(7.0, 3.0, 250.0)
        val c = commute.since("r")!! / FilterModel.END_OF_SERVICE_HOURS
        println("출퇴근(하루 1시간, 외기 60%%, 200 m³/h): 1년에 %.0f%%, 택시(하루 10시간, 외기 70%%, 250 m³/h): %d일에 가득".format(c * 100, taxiFull))
        assertNull(commuteFull)
        assertTrue(c in 0.12..0.5)
        assertTrue(taxiFull!! in 80..130)
        assertTrue(taxi.since("r")!! > commute.since("r")!! * 8)
    }

    @Test fun recirculationLoadsAboutAFifth() {
        val (fresh, _) = year(1.0, 0.0, 200.0)
        val (mostlyRecirc, _) = year(0.1, 0.9, 200.0)
        val ratio = fresh.since("r")!! / mostlyRecirc.since("r")!!
        println("외기만 대 내기 90%%: 먼지 %.1f배".format(ratio))
        assertEquals(1 / (0.1 + 0.9 * FilterModel.RECIRC_SHARE), ratio, 0.05)
    }

    @Test fun ramAirStillLoadsWithTheBlowerOff() {
        val (blowerOff, _) = year(0.0, 0.0, 200.0, ramH = 1.0)
        assertTrue(blowerOff.since("r")!! > 0)
        assertEquals(FilterModel.RAM_FLOW, FilterDust.m3h(false, false, null, 40.0), 0.0)
        assertEquals(0.0, FilterDust.m3h(false, false, null, 5.0), 0.0)
        assertEquals(0.0, FilterDust.m3h(false, true, null, 60.0), 0.0)
    }

    @Test fun theJudgmentFollowsTheLoad() {
        val today = LocalDate.of(2026, 11, 2)
        val record = ServiceRecord("r", Part.CABIN_FILTER, today.minusDays(85), 30_000, ServiceAction.REPLACE)
        val plan = RecommendedCare.plan(Part.CABIN_FILTER, "r")
        // 85 days of taxi use is just past nine tenths of the load.
        val (taxi, _) = year(7.0, 3.0, 250.0, days = 85)
        val a = CompositeJudge.assess(plan, record, null, today, SignalSnapshot(filter = taxi))
        assertEquals(DueStatus.SOON, a.status)
        assertTrue(a.drivenBy!!.startsWith("먼지 적재 추정"))
        val (commute, _) = year(0.6, 0.4, 200.0, days = 85)
        assertNull(CompositeJudge.assess(plan, record, null, today, SignalSnapshot(filter = commute)).drivenBy)
        val back = FilterDust.fromJson(org.json.JSONObject(taxi.toJson().toString()))
        assertEquals(taxi.since("r")!!, back.since("r")!!, 1e-9)
    }
}
