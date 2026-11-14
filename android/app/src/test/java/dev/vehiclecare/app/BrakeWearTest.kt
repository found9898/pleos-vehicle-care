package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrakeWearTest {
    @Test fun eachModelBringsItsMassRoadLoadAndRegenerativeShare() {
        val tucson = BrakeParams.of(VehicleCatalog.TUCSON_GASOLINE)
        val hybrid = BrakeParams.of(VehicleCatalog.match(VehicleIdentity("Hyundai", "TUCSON", 2026, Powertrain.HYBRID, null, null))!!)
        val ioniq = BrakeParams.of(VehicleCatalog.match(VehicleIdentity("Hyundai", "IONIQ 5", 2026, Powertrain.ELECTRIC, null, null))!!)
        assertEquals(1.0, tucson.frictionShare, 0.0); assertEquals(0.40, hybrid.frictionShare, 0.0); assertEquals(0.13, ioniq.frictionShare, 0.0)
        assertTrue(ioniq.massKg > hybrid.massKg && hybrid.massKg > tucson.massKg)
        // EPA coast-down for the Tucson body at 50 km/h is about 250 N.
        assertEquals(260.0, tucson.roadLoadN(50 / 3.6), 1.0)
    }

    @Test fun hotPadsWearFaster() {
        assertEquals(1.0, BrakeWearModel.temperatureFactor(150.0), 0.0)
        assertEquals(1.8, BrakeWearModel.temperatureFactor(300.0), 1e-9)
        assertEquals(4.0, BrakeWearModel.temperatureFactor(400.0), 1e-9)
        assertEquals(5.0, BrakeWearModel.temperatureFactor(700.0), 0.0)
    }

    private val p = BrakeWearModel.params()
    private val m = p.massKg

    @Test fun stopFromSpeedIsKineticEnergyLessResistance() {
        // 100 km/h to rest in 5 s.
        val v = 100 / 3.6
        val work = BrakeWearModel.segmentWork(v, 0.0, 5.0)
        val kinetic = 0.5 * m * v * v
        val resistance = p.roadLoadN(v / 2) * (v / 2) * 5
        assertEquals(kinetic - resistance, work, 1e-6)
        assertTrue(work in 620_000.0..660_000.0)   // Tucson 1,583 kg + driver, 3 % rotating, EPA road load
    }

    @Test fun coastingDecelerationIsNotBrakeWork() {
        // Losing 0.2 km/h over a second at 50 km/h is about what drag and rolling resistance take on their own.
        assertEquals(0.0, BrakeWearModel.segmentWork(50 / 3.6, 49.8 / 3.6, 1.0), 0.0)
    }

    @Test fun onlySegmentsStartedWithPedalPressedCount() {
        val w = BrakeWork()
        w.speed(20.0, 0); w.speed(18.0, 1000)
        assertEquals(0.0, w.workJ, 0.0)
        w.pedal(true, 1000); w.speed(10.0, 3000); w.pedal(false, 3000); w.speed(8.0, 4000)
        assertEquals(BrakeWearModel.segmentWork(18.0, 10.0, 2.0), w.workJ, 1e-6)
        assertEquals(19.0 + 28.0 + 9.0, w.distanceM, 1e-9)
    }

    @Test fun pressWithoutNewSpeedSampleClosesSegmentAtLastSpeed() {
        val w = BrakeWork()
        w.speed(15.0, 0); w.pedal(true, 2000)
        // The 2 s before the press were cruising and add distance only.
        assertEquals(30.0, w.distanceM, 1e-9); assertEquals(0.0, w.workJ, 0.0)
        w.speed(5.0, 4000)
        assertEquals(BrakeWearModel.segmentWork(15.0, 5.0, 2.0), w.workJ, 1e-6)
    }

    @Test fun gapDropsUnseenDriving() {
        val w = BrakeWork()
        w.pedal(true, 0); w.speed(30.0, 0); w.gap()
        w.speed(0.0, 60_000)
        assertEquals(0.0, w.workJ, 0.0); assertEquals(0.0, w.distanceM, 0.0)
    }

    @Test fun reverseSpeedIsMagnitude() {
        val w = BrakeWork()
        w.pedal(true, 0); w.speed(-3.0, 0); w.speed(0.0, 1000)
        assertEquals(BrakeWearModel.segmentWork(3.0, 0.0, 1.0), w.workJ, 1e-6)
    }

    @Test fun baselineAndJsonRoundTrip() {
        val w = BrakeWork(2.0e6, 50_000.0)
        w.markBaseline("r1"); w.workJ += 1.0e6; w.distanceM += 10_000.0
        val back = BrakeWork.fromJson(w.toJson())
        val (work, distance, since) = back.since("r1")
        assertEquals(1.0e6, work, 1e-6); assertEquals(10_000.0, distance, 1e-9); assertTrue(since)
        assertEquals(Triple(3.0e6, 60_000.0, false), back.since("unknown"))
    }

    @Test fun paperScaleWearIsReproduced() {
        // 600 MJ over the 1,200 kg car's 10,200 km gave about 0.62 mm after break-in at k = 1.22e-9 mm/J (Wear 552-553).
        val (low, high) = BrakeWearModel.wearMm(538e6)
        assertTrue(538e6 * BrakeWearModel.K_MID in low..high)
        assertEquals(0.656, 538e6 * BrakeWearModel.K_MID, 0.001)
    }

    @Test fun aLongPressThatKeepsTheSpeedIsAHoldOnAGrade() {
        val b = BrakeWork()
        // 15 m/s held for 8 s with the pedal down, as on a steep descent.
        b.speed(15.0, 0); b.pedal(true, 0)
        (1..80).forEach { b.speed(15.0 - it * 0.005, it * 100L) }
        b.pedal(false, 8_000)
        assertEquals(1, b.holds.count); assertEquals(8.0, b.holds.seconds, 1e-9); assertEquals(118.4, b.holds.distanceM, 0.5)
        // A normal stop from the same speed is braking, not a hold.
        b.pedal(true, 9_000)
        (1..80).forEach { b.speed(maxOf(0.0, 15.0 - it * 0.19), 9_000 + it * 100L) }
        b.pedal(false, 17_000)
        assertEquals(1, b.holds.count)
        // A short press does not count either.
        b.speed(15.0, 18_000); b.pedal(true, 18_000); b.speed(15.0, 21_000); b.pedal(false, 21_000)
        assertEquals(1, b.holds.count)
    }

    @Test fun holdsCountFromTheRecordAndSurviveStorage() {
        val b = BrakeWork()
        b.speed(12.0, 0); b.pedal(true, 0); b.speed(12.0, 6_000); b.pedal(false, 6_000)
        b.markBaseline("r")
        b.speed(12.0, 10_000); b.pedal(true, 10_000); b.speed(12.5, 17_000); b.pedal(false, 17_000)
        val back = BrakeWork.fromJson(org.json.JSONObject(b.toJson().toString()))
        assertEquals(2, back.holds.count)
        assertEquals(1, back.holdsSince("r").count); assertEquals(7.0, back.holdsSince("r").seconds, 1e-9)
    }
}
