package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssessmentTest {
    private val today = LocalDate.of(2026, 10, 29)
    private fun record(part: Part, date: LocalDate, km: Long?) = ServiceRecord("r", part, date, km, part.defaultAction)
    private fun plan(part: Part) = RecommendedCare.plan(part, "r")
    private fun mileage(km: Long) = Mileage(km, today, "vehicle")

    @Test fun shortTripsBringTheOilChangeForward() {
        val oil = OilTrips()
        // 4,700 km of 3 km trips counted twice, against 4,700 km on the odometer: half the schedule, all of the estimate.
        repeat(1_000) { oil.add(OilTrip(0, 4.7, 10.0, 1.0, 15.0)) }
        oil.marks["r"] = 0
        val a = CompositeJudge.assess(plan(Part.ENGINE_OIL), record(Part.ENGINE_OIL, today.minusMonths(3), 30_000), mileage(34_700), today, SignalSnapshot(oil = oil))
        assertEquals(DueStatus.UPCOMING, a.schedule.status)
        assertEquals(DueStatus.SOON, a.status)
        assertTrue(a.drivenBy!!.startsWith("운행 조건 환산"))
        assertEquals(.94f, a.fraction!!, .001f)
    }

    @Test fun gentleUseNeverPushesTheScheduleBack() {
        val wiper = WiperCycles(1_000.0, 2_000.0, mutableMapOf("r" to (0.0 to 0.0)))
        val a = CompositeJudge.assess(plan(Part.WIPER), record(Part.WIPER, today.minusMonths(12).plusDays(5), null), null, today, SignalSnapshot(wiper = wiper))
        assertEquals(DueStatus.SOON, a.status); assertNull(a.drivenBy)
    }

    @Test fun onlyWipingBeyondTheTypicalPaceAgesTheBlade() {
        // Two months at the typical 52,000 a year: the calendar alone.
        val typical = WiperCycles(8_700.0, 20_000.0, mutableMapOf("r" to (0.0 to 0.0)))
        val calm = CompositeJudge.assess(plan(Part.WIPER), record(Part.WIPER, today.minusDays(61), null), null, today, SignalSnapshot(wiper = typical))
        assertEquals(DueStatus.UPCOMING, calm.status); assertNull(calm.drivenBy)
        // 181,000 on the lower count in 61 days: 172,310 beyond the pace is 314 more days, a year's age in two months.
        val worn = WiperCycles(181_000.0, 240_000.0, mutableMapOf("r" to (0.0 to 0.0)))
        val a = CompositeJudge.assess(plan(Part.WIPER), record(Part.WIPER, today.minusDays(61), null), null, today, SignalSnapshot(wiper = worn))
        assertEquals(DueStatus.DUE, a.status); assertEquals("블레이드 노화 환산 375일", a.drivenBy)
    }

    @Test fun heavyBrakingCountsAsMoreDistance() {
        // 6,000 km at twice this Tucson's typical brake work is the wear of 12,000 typical kilometres.
        val heavy = 6_000 * 2 * CompositeJudge.brakeReferenceKjPerKm() * 1_000
        val brake = BrakeWork(heavy, 6.0e6, mutableMapOf("r" to BrakeTotals()), wearJ = heavy, wearHighJ = heavy)
        val a = CompositeJudge.assess(plan(Part.BRAKE_PAD), record(Part.BRAKE_PAD, today.minusMonths(5), 30_000), mileage(36_000), today, SignalSnapshot(brake = brake))
        assertEquals(DueStatus.DUE, a.status); assertEquals("제동 에너지 환산 12,000 km", a.drivenBy)
        // Light braking leaves the schedule in charge.
        val light = BrakeWork(6_000 * 60_000.0, 6.0e6, mutableMapOf("r" to BrakeTotals()), wearJ = 6_000 * 60_000.0, wearHighJ = 6_000 * 60_000.0)
        assertNull(CompositeJudge.assess(plan(Part.BRAKE_PAD), record(Part.BRAKE_PAD, today.minusMonths(5), 30_000), mileage(36_000), today, SignalSnapshot(brake = light)).drivenBy)
    }

    @Test fun filterDueAtWeisFiveHundredHoursOfLoad() {
        val dust = FilterDust(500.0, mutableMapOf("r" to 0.0))
        val a = CompositeJudge.assess(plan(Part.CABIN_FILTER), record(Part.CABIN_FILTER, today.minusMonths(4), 30_000), mileage(33_000), today, SignalSnapshot(filter = dust))
        assertEquals(DueStatus.DUE, a.status); assertEquals("먼지 적재 추정 100%", a.drivenBy)
    }

    @Test fun lowOilLevelIsDueAtOnceAndPausingDoesNotChangeTheJudgment() {
        val p = plan(Part.ENGINE_OIL)
        val r = record(Part.ENGINE_OIL, today.minusMonths(1), 30_000)
        val live = CompositeJudge.assess(p, r, mileage(31_000), today, SignalSnapshot(oilLevel = "부족"))
        val paused = CompositeJudge.assess(p.copy(paused = true), r, mileage(31_000), today, SignalSnapshot(oilLevel = "부족"))
        assertEquals(DueStatus.DUE, live.status)
        // Pausing only silences the reminder; the screen must show the same state, share and reason.
        assertEquals(live.status, paused.status); assertEquals(live.fraction, paused.fraction); assertEquals(live.drivenBy, paused.drivenBy)
    }

    @Test fun pausingKeepsASignalEstimateAndItsShare() {
        val p = plan(Part.CABIN_FILTER)
        val r = record(Part.CABIN_FILTER, today.minusMonths(2), null)
        val filter = FilterDust(FilterModel.END_OF_SERVICE_HOURS * 0.95, mutableMapOf("r" to 0.0))
        val signals = SignalSnapshot(filter = filter)
        val live = CompositeJudge.assess(p, r, null, today, signals)
        val paused = CompositeJudge.assess(p.copy(paused = true), r, null, today, signals)
        assertEquals(live.status, paused.status); assertEquals(live.fraction, paused.fraction); assertEquals(live.drivenBy, paused.drivenBy)
    }

    @Test fun estimatesWaitForAServiceRecordAndDoNotHideMissingInformation() {
        val wiper = WiperCycles(150_000.0, 200_000.0)
        val a = CompositeJudge.assess(plan(Part.WIPER), null, null, today, SignalSnapshot(wiper = wiper))
        assertEquals(DueStatus.NEEDS_INFO, a.status)
    }

    @Test fun aSlowerCoolingSystemIsBroughtToInspection() {
        val r = record(Part.HVAC, today.minusMonths(6), null)
        fun tracker(vararg shares: Double) = ClimateTracker().apply { shares.forEach { add(ClimateSession(0, 2.0, 32.0, 22.0, 29.0, it)) }; marks["r"] = 0 }
        val base = doubleArrayOf(.55, .6, .52, .58, .5, .62, .57, .54)
        val slow = CompositeJudge.assess(plan(Part.HVAC), r, null, today, SignalSnapshot(cooldown = tracker(*base, .45, .4, .47, .42, .44, .46)))
        assertEquals(DueStatus.SOON, slow.status)
        assertEquals("냉방 회복 점검 직후 대비 79%", slow.drivenBy)
        val lost = CompositeJudge.assess(plan(Part.HVAC), r, null, today, SignalSnapshot(cooldown = tracker(*base, .55, .05, -.02)))
        assertEquals(DueStatus.DUE, lost.status)
        // One slow day among normal ones is not a decline.
        assertEquals(DueStatus.UPCOMING, CompositeJudge.assess(plan(Part.HVAC), r, null, today, SignalSnapshot(cooldown = tracker(*base, .45, .4, .47, .56, .44, .46))).status)
    }

    @Test fun aSevereConditionHalvesTheOilIntervalAndShortTripsAreNotCountedTwiceForIt() {
        val severe = RecommendedCare.plan(Part.ENGINE_OIL, "r", severe = true)
        assertEquals(6, severe.intervalMonths); assertEquals(5_000L, severe.intervalKm)
        assertEquals(10_000L, RecommendedCare.plan(Part.BRAKE_PAD, "r", severe = true).intervalKm)
        val oil = OilTrips()
        // 4,000 km of warm short trips: 8,000 km against the normal 10,000, below the severe plan's own 5,000 km.
        repeat(1_000) { oil.add(OilTrip(0, 4.0, 10.0, 1.0, 15.0)) }
        oil.marks["r"] = 0
        val a = CompositeJudge.assess(severe, record(Part.ENGINE_OIL, today.minusMonths(2), 30_000), mileage(34_000), today, SignalSnapshot(oil = oil))
        assertEquals(DueStatus.SOON, a.schedule.status)
        assertEquals(DueStatus.SOON, a.status); assertNull(a.drivenBy)
        assertTrue(SevereUse.checks(Part.CABIN_FILTER, setOf(2))); assertTrue(!SevereUse.checks(Part.CABIN_FILTER, setOf(1, 3)))
        assertTrue(SevereUse.checks(Part.BRAKE_PAD, setOf(6)))
    }

    @Test fun aWeakerAirflowTheOwnerReportsMakesTheFilterDue() {
        val r = record(Part.CABIN_FILTER, today.minusMonths(4), 30_000)
        val weak = CompositeJudge.assess(plan(Part.CABIN_FILTER), r, mileage(33_000), today, SignalSnapshot(), listOf(r), OwnerReport.WEAK_AIRFLOW)
        assertEquals(DueStatus.DUE, weak.status); assertEquals("풍량 약해짐 신고", weak.drivenBy)
        val odor = CompositeJudge.assess(plan(Part.CABIN_FILTER), r, mileage(33_000), today, SignalSnapshot(), listOf(r), OwnerReport.ODOR)
        assertEquals(DueStatus.SOON, odor.status)
        // Without a record there is nothing to bring forward.
        assertEquals(DueStatus.NEEDS_INFO, CompositeJudge.assess(plan(Part.CABIN_FILTER), null, mileage(33_000), today, SignalSnapshot(), emptyList(), OwnerReport.ODOR).status)
    }
}
