package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QuickSetupTest {
    private val today = LocalDate.of(2026, 11, 5)
    private fun date(part: Part, label: String) = QuickSetup.dates(part).first { it.label == label }
    private fun km(label: String, part: Part = Part.ENGINE_OIL) = QuickSetup.distances(part).first { it.label == label }

    @Test fun aRoughDateAndDistanceBecomeABaseline() {
        val r = QuickSetup.record("r", Part.ENGINE_OIL, today, 39_540, date(Part.ENGINE_OIL, "6개월 전쯤"), km("5,000 km쯤"))!!
        assertEquals(today.minusDays(182), r.date); assertEquals(34_540L, r.odometerKm); assertEquals(ServiceAction.REPLACE, r.action)
        // Counted from that baseline the oil is half way: 5,000 of 10,000 km, six of twelve months.
        CarModel.current = VehicleCatalog.TUCSON_GASOLINE
        val due = ScheduleCalculator.calculate(RecommendedCare.plan(Part.ENGINE_OIL, "r"), r, Mileage(39_540, today, "vehicle"), today)
        assertEquals(DueStatus.UPCOMING, due.status); assertEquals(5_000L, due.remainingKm)
    }

    @Test fun notKnowingEitherLeavesThePartUnrecorded() {
        assertNull(QuickSetup.record("r", Part.ENGINE_OIL, today, 39_540, date(Part.ENGINE_OIL, "모르겠어요"), km("모르겠어요")))
        assertNull(QuickSetup.record("r", Part.WIPER, today, 39_540, date(Part.WIPER, "모르겠어요"), null))
    }

    @Test fun theBrakeIsCountedInDistanceOnlyAndNeverBelowZero() {
        val r = QuickSetup.record("r", Part.BRAKE_PAD, today, 2_000, null, km("8,000 km쯤"))!!
        assertNull(r.date); assertEquals(0L, r.odometerKm); assertEquals(ServiceAction.INSPECT, r.action)
        // Without the car's reading a distance answer has nothing to count back from.
        assertNull(QuickSetup.record("r", Part.BRAKE_PAD, today, null, null, km("8,000 km쯤")))
    }

    @Test fun theTireAnswerIsTheSetOnTheCarSoEveryTireJobHasABaseline() {
        CarModel.current = VehicleCatalog.TUCSON_GASOLINE
        val r = QuickSetup.record("r", Part.TIRE, today, 39_540, date(Part.TIRE, "2년 전쯤"), km("2만 km쯤", Part.TIRE))!!
        assertEquals(ServiceAction.REPLACE, r.action); assertEquals(today.minusDays(731), r.date); assertEquals(19_540L, r.odometerKm)
        val mileage = Mileage(39_540, today, "vehicle")
        // Rotation, the six-year line and the tread estimate all count from it; an inspection date fed none of them.
        assertEquals(20_000.0, Rotation.of(listOf(r), mileage, TireLoad())!!.sinceKm, 0.1)
        assertEquals(2.0, TireAge.of(listOf(r), today)!!.years, 0.01)
        assertNotNull(TireTread.of(listOf(r), mileage, TireLoad()))
    }

    @Test fun aSetThatCameWithTheCarStartsAtZeroWithoutTheReading() {
        val r = QuickSetup.record("r", Part.TIRE, today, null, date(Part.TIRE, "3년 전쯤"), km("새 차 때부터", Part.TIRE))!!
        assertEquals(0L, r.odometerKm)
        assertEquals("3년 전쯤 · 새 차 때부터", QuickSetup.summary(date(Part.TIRE, "3년 전쯤"), km("새 차 때부터", Part.TIRE)))
    }

    @Test fun aMonthlyCheckCountedFromATireChangeSaysNoCheckIsOnRecord() {
        CarModel.current = VehicleCatalog.TUCSON_GASOLINE
        val r = QuickSetup.record("r", Part.TIRE, today, 39_540, date(Part.TIRE, "1년 안"), km("1만 km 안", Part.TIRE))!!
        val plan = RecommendedCare.plan(Part.TIRE, "r")
        val mileage = Mileage(39_540, today, "vehicle")
        val a = CompositeJudge.assess(plan, r, mileage, today, SignalSnapshot(), listOf(r))
        val factors = CareGuide.build(Part.TIRE, plan, r, a, SignalSnapshot(), listOf(r), mileage, null, today).factors
        // Not "점검 152일 지남": nothing says the owner skipped five months of checks, only that none is on record.
        assertEquals("점검 기록 없음", factors.first { it.title == "공기압·마모 점검" }.value)
        assertEquals("지금 점검", CareGuide.label(plan, a, factors))
    }

    @Test fun aRotationPastItsIntervalSaysWhereItCountsFrom() {
        CarModel.current = VehicleCatalog.TUCSON_GASOLINE
        val set = QuickSetup.record("set", Part.TIRE, today, 39_540, date(Part.TIRE, "2년 전쯤"), km("2만 km쯤", Part.TIRE))!!
        val plan = RecommendedCare.plan(Part.TIRE, "set")
        val mileage = Mileage(39_540, today, "vehicle")
        fun row(records: List<ServiceRecord>): String {
            val a = CompositeJudge.assess(plan, set, mileage, today, SignalSnapshot(), records)
            assertEquals(ServiceAction.ROTATE, a.task)
            return CareGuide.build(Part.TIRE, plan, set, a, SignalSnapshot(), records, mileage, null, today).factors.first { it.title == "위치 교환" }.value
        }
        // From the tire change nothing says a rotation was skipped, only that none was entered; never "0 km 남음".
        assertEquals("기록 없음", row(listOf(set)))
        val turned = ServiceRecord("turn", Part.TIRE, today.minusDays(400), 26_540, ServiceAction.ROTATE)
        assertEquals("3,000 km 지남", row(listOf(set, turned)))
    }
}
