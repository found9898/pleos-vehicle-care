package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The signals and readings added after the six rounds: a gauge reading, the car's own understeer, the province's air. */
class SignalsAndReadingsTest {
    private val today = LocalDate.of(2026, 11, 3)

    /**
     * A Tucson whose real understeer is 4 deg/g, with a right rear tire 1 mm smaller in radius, corners left and right at
     * 50 km/h. The tick-based lateral is biased one way; the learned gradient must still land on 4 deg/g.
     */
    @Test fun theCarsOwnUndersteerIsLearnedThroughATireRadiusBias() {
        CarModel.current = VehicleCatalog.TUCSON_GASOLINE
        val p = TireParams.of(CarModel.current)
        val kTrue = 4.0 * Math.PI / 180 / 9.81
        val load = TireLoad()
        val v = 50 / 3.6
        var ms = 0L; var left = 0.0; var right = 0.0
        for (turn in 0 until 120) {
            val wheelDeg = (if (turn % 2 == 0) 1 else -1) * (40.0 + 5 * (turn % 5))
            val delta = Math.toRadians(wheelDeg / p.steeringRatio)
            val ay = v * v * delta / (p.wheelbaseM + kTrue * v * v)
            val yaw = ay / v
            repeat(41) {
                load.steering(wheelDeg, ms); load.wheels(left, right, false); load.speed(v, ms)
                left += (v - yaw * p.rearTrackM / 2) * 0.1
                right += (v + yaw * p.rearTrackM / 2) * 0.1 * (1 + 0.001 / 0.36)
                ms += 100
            }
            load.gap()
        }
        val learned = load.understeer() * 9.81 * 180 / Math.PI
        println("학습한 언더스티어 %.2f deg/g (참값 4.00, 기본값 3.00), 왼쪽 %.0f개·오른쪽 %.0f개 창".format(learned, load.kLeft.distanceM, load.kRight.distanceM))
        assertEquals(4.0, learned, 0.3)
        val back = TireLoad.fromJson(org.json.JSONObject(load.toJson().toString()))
        assertEquals(load.understeer(), back.understeer(), 1e-12)
    }

    @Test fun withoutEnoughCorneringTheDefaultStays() {
        assertEquals(TireModel.UNDERSTEER_RAD_PER_MPS2, TireLoad().understeer(), 0.0)
    }

    @Test fun aMeasuredTreadDepthRestartsTheEstimate() {
        val p = TireParams.of(VehicleCatalog.TUCSON_GASOLINE)
        val set = ServiceRecord("s", Part.TIRE, today.minusYears(2), 10_000, ServiceAction.REPLACE)
        val check = ServiceRecord("c", Part.TIRE, today.minusDays(30), 40_000, ServiceAction.INSPECT, measuredMm = 3.2)
        val t = TireTread.of(listOf(set, check), Mileage(41_000, today, "vehicle"), TireLoad(), p)!!
        // The worse axle was read at 3.2 mm; 1,000 km more at most takes a fraction of a millimetre off it.
        assertEquals(check, t.measured)
        assertTrue(t.central in 3.0..3.2)
        assertTrue(t.lowest in 2.9..t.central)
        // The same check without a reading leaves the model's own estimate, which thought there was more left.
        val unread = TireTread.of(listOf(set, check.copy(measuredMm = null)), Mileage(41_000, today, "vehicle"), TireLoad(), p)!!
        assertTrue(unread.central > t.central)
        assertEquals(3.2, CareStore.recordFromJson(CareStore.recordJson(check)).measuredMm!!, 0.0)
    }

    @Test fun aMeasuredPadThicknessRestartsTheEstimate() {
        val replaced = ServiceRecord("p", Part.BRAKE_PAD, today.minusYears(2), 10_000, ServiceAction.REPLACE)
        val check = ServiceRecord("c", Part.BRAKE_PAD, today.minusDays(10), 40_000, ServiceAction.INSPECT, measuredMm = 5.0)
        val brake = BrakeWork(0.0, 0.0, mutableMapOf("p" to BrakeTotals(), "c" to BrakeTotals()))
        val s = SignalSnapshot(brake = brake)
        assertEquals(5.0, CompositeJudge.padLeft(listOf(replaced, check), s)!!, 1e-9)
        assertEquals(check, CompositeJudge.padMeasured(listOf(replaced, check), s))
        // A newer pad change outranks the older reading.
        val again = ServiceRecord("n", Part.BRAKE_PAD, today.minusDays(2), 41_000, ServiceAction.REPLACE)
        brake.baselines["n"] = BrakeTotals()
        assertNull(CompositeJudge.padMeasured(listOf(replaced, check, again), s))
    }

    @Test fun lowBrakeFluidIsDueAtOnce() {
        val r = ServiceRecord("r", Part.BRAKE_PAD, today.minusMonths(1), 30_000, ServiceAction.INSPECT)
        val a = CompositeJudge.assess(RecommendedCare.plan(Part.BRAKE_PAD, "r"), r, Mileage(31_000, today, "vehicle"), today, SignalSnapshot(brakeFluid = "부족"))
        assertEquals(DueStatus.DUE, a.status); assertEquals("브레이크액 부족", a.drivenBy)
        assertEquals(DueStatus.UPCOMING, CompositeJudge.assess(RecommendedCare.plan(Part.BRAKE_PAD, "r"), r, Mileage(31_000, today, "vehicle"), today, SignalSnapshot(brakeFluid = "정상")).status)
    }

    @Test fun theProvincesAirChangesHowFastTheFilterFills() {
        assertEquals("서울", FilterModel.province(37.57 to 126.98)!!.first)
        assertEquals("제주", FilterModel.province(33.45 to 126.56)!!.first)
        assertNull(FilterModel.province(null))
        val incheon = FilterDust(); val jeju = FilterDust()
        for (f in listOf(incheon, jeju)) f.markBaseline("r")
        incheon.update(200.0, 0, 6, 37.46 to 126.71); incheon.update(0.0, 3_600_000, 6, 37.46 to 126.71)
        jeju.update(200.0, 0, 6, 33.49 to 126.50); jeju.update(0.0, 3_600_000, 6, 33.49 to 126.50)
        println("같은 한 시간: 인천 %.3f, 제주 %.3f 상하이 시간".format(incheon.since("r"), jeju.since("r")))
        assertEquals(18.9 / 12.3, incheon.since("r")!! / jeju.since("r")!!, 1e-9)
    }

    @Test fun drivingModeShareNeedsTenKilometres() {
        val d = DriveModes()
        d.add(6_000.0, ai.pleos.playground.vehicle.constant.control.DrivingModeType.NORMAL)
        assertNull(d.sportShare)
        d.add(4_000.0, ai.pleos.playground.vehicle.constant.control.DrivingModeType.SPORT)
        d.add(500.0, null)
        assertEquals(0.4, d.sportShare!!, 1e-9)
        assertEquals(0.4, DriveModes.fromJson(d.toJson()).sportShare!!, 1e-9)
    }
}
