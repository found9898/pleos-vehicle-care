package dev.vehiclecare.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TireWearTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun record(id: String, date: LocalDate, km: Long, action: ServiceAction) = ServiceRecord(id, Part.TIRE, date, km, action)
    private fun mileage(km: Long) = Mileage(km, today, "vehicle")

    /** Feeds 10 Hz speed and matching rear-wheel travel; [yaw] in rad/s turns the car. */
    private fun drive(load: TireLoad, seconds: Int, speedAt: (Double) -> Double, yaw: (Double) -> Double = { 0.0 }, startMs: Long = 0,
                      wheels: Boolean = true): Long {
        var left = 0.0; var right = 0.0
        var ms = startMs
        repeat(seconds * 10 + 1) { i ->
            val t = i / 10.0
            val v = speedAt(t)
            val half = yaw(t) * TireModel.REAR_TRACK_M / 2
            if (i > 0) { left += (v - half * 1) * 0.1; right += (v + half * 1) * 0.1 }
            if (wheels) load.wheels(left, right, false)
            load.speed(v, ms)
            ms += 100
        }
        return ms
    }

    @Test fun momentsGiveTheDistanceWeightedStandardDeviation() {
        val m = Moments().add(10.0, 1.0).add(10.0, -1.0).add(20.0, 0.0)
        assertEquals(Math.sqrt(0.5), m.sd!!, 1e-9)
        assertNull(Moments().sd)
    }

    @Test fun alternatingAccelerationShowsInTheLongitudinalSpread() {
        val load = TireLoad()
        // One second at +1 m/s², the next at −1 m/s², around 15 m/s.
        drive(load, 120, { t -> 15 + if ((t.toInt() % 2) == 0) t % 1 else 1 - t % 1 })
        assertEquals(1.0, load.longitudinal.sd!!, 0.05)
        assertEquals(1860.0, load.longitudinal.distanceM, 30.0)
    }

    @Test fun rearWheelDifferenceGivesLateralAcceleration() {
        val load = TireLoad()
        // 10 m/s through alternating 50 m bends: ω = 0.2 rad/s, a = v·ω = 2 m/s² either way.
        drive(load, 200, { 10.0 }, { t -> if ((t / 10).toInt() % 2 == 0) 0.2 else -0.2 })
        assertEquals(2.0, load.lateral.sd!!, 0.1)
        assertEquals(0.0, load.longitudinal.sd!!, 1e-6)
        assertTrue(load.ticksAgreed!!)
    }

    @Test fun ticksThatDoNotMoveAreIgnored() {
        val load = TireLoad()
        load.wheels(0.1, 0.2, false)
        // The emulator reports fixed tick counts; lateral must stay unmeasured rather than read as a straight road.
        drive(load, 60, { 12.0 }, wheels = false)
        assertEquals(0.0, load.lateral.distanceM, 0.0)
        assertFalse(load.ticksAgreed!!)
        assertTrue(load.longitudinal.distanceM > 700)
    }

    @Test fun aJumpInTheSpeedSignalIsNotDriving() {
        val load = TireLoad()
        load.speed(8.0, 0); load.speed(8.0, 1_000)
        // 8 to 22 m/s within a second is beyond any tire's grip.
        load.speed(22.0, 2_000); load.speed(22.0, 3_000)
        assertEquals(0.0, load.longitudinal.sd!!, 1e-9)
        assertEquals(30.0, load.longitudinal.distanceM, 1e-9)
    }

    @Test fun aGapForgetsTheOpenWindow() {
        val load = TireLoad()
        load.speed(0.0, 0); load.speed(5.0, 500)
        load.gap()
        // Picking up at 30 m/s must not read as 0 to 30 m/s in one window.
        load.speed(30.0, 600_000); load.speed(30.0, 601_000)
        assertEquals(0.0, load.longitudinal.sd!!, 1e-9)
    }

    @Test fun survivesStorageWithBaselines() {
        val load = TireLoad()
        drive(load, 30, { t -> 10 + t / 10 })
        load.markBaseline("r")
        drive(load, 30, { 13.0 }, startMs = 100_000)
        val back = TireLoad.fromJson(JSONObject(load.toJson().toString()))
        assertEquals(load.longitudinal, back.longitudinal)
        assertEquals(load.since("r").longitudinal, back.since("r").longitudinal)
        assertTrue(back.since("r").since)
    }

    @Test fun inspectionsDoNotRestartTheRotationInterval() {
        val records = listOf(record("a", today.minusMonths(8), 30_000, ServiceAction.ROTATE), record("b", today.minusDays(5), 38_900, ServiceAction.INSPECT))
        assertEquals("a", Rotation.baseline(records)!!.id)
        val newer = records + record("c", today.minusDays(2), 39_000, ServiceAction.REPLACE)
        assertEquals("c", Rotation.baseline(newer)!!.id)
    }

    @Test fun rotationIsDueByDistanceBesideTheMonthlyCheck() {
        val records = listOf(record("r", today.minusMonths(6), 30_000, ServiceAction.ROTATE), record("i", today.minusDays(3), 39_000, ServiceAction.INSPECT))
        val plan = RecommendedCare.plan(Part.TIRE, "i")
        val a = CompositeJudge.assess(plan, records[1], mileage(39_100), today, SignalSnapshot(), records)
        assertEquals(DueStatus.UPCOMING, a.schedule.status)
        assertEquals(DueStatus.SOON, a.status)
        assertEquals(ServiceAction.ROTATE, a.task)
        assertEquals("위치 교환 후 9,100 km", a.drivenBy)
    }

    @Test fun hardDrivingBringsTheRotationForward() {
        val records = listOf(record("r", today.minusMonths(5), 30_000, ServiceAction.ROTATE), record("i", today.minusDays(3), 36_900, ServiceAction.INSPECT))
        // 7,000 km seen with a factor of 1.4: (1.4 - 0.1) / 0.9 of the regulation's mean square, split evenly.
        val ms = (1.4 - TireModel.BASE_SHARE) / (1 - TireModel.BASE_SHARE) * TireModel.REFERENCE_MS / 2
        val load = TireLoad(Moments(7e6, 0.0, 7e6 * ms), Moments(7e6, 0.0, 7e6 * ms), mutableMapOf("r" to (Moments() to Moments())))
        val a = CompositeJudge.assess(RecommendedCare.plan(Part.TIRE, "i"), records[1], mileage(37_000), today, SignalSnapshot(tire = load), records)
        assertEquals(DueStatus.UPCOMING, a.schedule.status)
        assertEquals(DueStatus.SOON, a.status)
        assertEquals("운전 습관 환산 9,800 km", a.drivenBy)
    }

    @Test fun theMonthlyCheckIsSoonOnlyInItsLastWeek() {
        val plan = RecommendedCare.plan(Part.TIRE, "i")
        assertEquals(DueStatus.UPCOMING, ScheduleCalculator.calculate(plan, record("i", today.minusDays(3), 39_000, ServiceAction.INSPECT), null, today).status)
        assertEquals(DueStatus.SOON, ScheduleCalculator.calculate(plan, record("i", today.minusDays(25), 39_000, ServiceAction.INSPECT), null, today).status)
    }

    @Test fun gentleDrivingNeverPushesTheRotationBack() {
        val records = listOf(record("r", today.minusMonths(6), 30_000, ServiceAction.ROTATE))
        val load = TireLoad(Moments(9e6, 0.0, 9e6 * 0.04), Moments(9e6, 0.0, 9e6 * 0.09), mutableMapOf("r" to (Moments() to Moments())))
        val rotation = Rotation.of(records, mileage(39_200), load)!!
        assertTrue(rotation.stats.factor!! < 1)
        assertEquals(.92, rotation.share, 1e-9)
        assertFalse(rotation.broughtForward)
    }

    @Test fun longitudinalAloneIsUsedWhenTheCarHasNoWheelTicks() {
        val load = TireLoad(Moments(1e5, 0.0, 1e5 * 0.36), Moments(), mutableMapOf("r" to (Moments() to Moments())))
        val stats = load.since("r")
        assertNull(stats.lateralSd)
        // The regulation's own lateral 0.93 m/s² stands in for the missing side.
        assertEquals(0.1 + 0.9 * (0.36 + 0.93 * 0.93) / TireModel.REFERENCE_MS, stats.factor!!, 1e-9)
    }

    @Test fun withoutARotationRecordOnlyTheMonthlyCheckCounts() {
        val inspect = record("i", today.minusDays(40), 39_000, ServiceAction.INSPECT)
        val a = CompositeJudge.assess(RecommendedCare.plan(Part.TIRE, "i"), inspect, mileage(49_500), today, SignalSnapshot(), listOf(inspect))
        assertEquals(DueStatus.DUE, a.status)
        assertNull(a.task); assertNull(a.drivenBy)
    }

    @Test fun withLongitudinalAloneOnlyHarderThanNormalDrivingBringsTheRotationForward() {
        val records = listOf(record("r", today.minusMonths(5), 30_000, ServiceAction.ROTATE))
        // 9,000 km seen at 0.70 m/s² RMS front-to-back and no usable wheel ticks; lateral assumed at the regulation's 0.93.
        val load = TireLoad(Moments(9e6, 0.0, 9e6 * 0.49), Moments(), mutableMapOf("r" to (Moments() to Moments())))
        val rotation = Rotation.of(records, mileage(39_000), load)!!
        assertEquals(9_000 * (0.1 + 0.9 * (0.49 + 0.93 * 0.93) / TireModel.REFERENCE_MS), rotation.equivalentKm, 1.0)
        assertTrue(rotation.broughtForward)
        // At the WLTC's 0.45 nothing moves.
        val normal = TireLoad(Moments(9e6, 0.0, 9e6 * 0.45 * 0.45), Moments(), mutableMapOf("r" to (Moments() to Moments())))
        assertFalse(Rotation.of(records, mileage(39_000), normal)!!.broughtForward)
    }

    @Test fun anAgingSetAsksForItsDateThenForReplacement() {
        val plan = RecommendedCare.plan(Part.TIRE, "i")
        val inspect = record("i", today.minusDays(3), 70_000, ServiceAction.INSPECT)
        val set = { years: Long -> record("s", today.minusYears(years).minusDays(10), 20_000, ServiceAction.REPLACE) }
        val young = CompositeJudge.assess(plan, inspect, mileage(25_000), today, SignalSnapshot(), listOf(set(3), inspect))
        assertNull(young.task)
        val five = CompositeJudge.assess(plan, inspect, mileage(25_000), today, SignalSnapshot(), listOf(set(5), inspect))
        assertEquals(DueStatus.SOON, five.status); assertEquals(ServiceAction.REPLACE, five.task); assertEquals("장착 후 5년", five.drivenBy)
        val six = CompositeJudge.assess(plan, inspect, mileage(25_000), today, SignalSnapshot(), listOf(set(6), inspect))
        assertEquals(DueStatus.DUE, six.status); assertEquals(ServiceAction.REPLACE, six.task)
    }

    @Test fun replacementWinsATieWithRotation() {
        val plan = RecommendedCare.plan(Part.TIRE, "i")
        val set = record("s", today.minusYears(6).minusDays(1), 20_000, ServiceAction.REPLACE)
        val rot = record("r", today.minusMonths(8), 60_000, ServiceAction.ROTATE)
        val inspect = record("i", today.minusDays(3), 70_500, ServiceAction.INSPECT)
        val a = CompositeJudge.assess(plan, inspect, mileage(70_500), today, SignalSnapshot(), listOf(set, rot, inspect))
        assertEquals(DueStatus.DUE, a.status); assertEquals(ServiceAction.REPLACE, a.task)
    }
}
