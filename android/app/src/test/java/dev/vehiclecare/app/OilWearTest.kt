package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OilModelTest {
    @Test fun shortTripsFollowHyundaisDefinition() {
        // US owner's manual: under 8 km above freezing, under 16 km below it.
        assertEquals(OilModel.Kind.SHORT, OilModel.kind(7.9, 12.0))
        assertEquals(OilModel.Kind.NORMAL, OilModel.kind(8.0, 12.0))
        assertEquals(OilModel.Kind.SHORT_COLD, OilModel.kind(12.0, -3.0))
        assertEquals(OilModel.Kind.NORMAL, OilModel.kind(16.0, -3.0))
        assertEquals(OilModel.Kind.SHORT, OilModel.kind(3.0, null))           // unknown weather counts as the milder case
    }

    @Test fun penaltiesAreGmsRevolutionTrace() {
        assertEquals(4.0, OilModel.penalty(-5.0), 0.0); assertEquals(3.0, OilModel.penalty(20.0), 0.0)
        assertEquals(2.0, OilModel.penalty(60.0), 0.0); assertEquals(1.0, OilModel.penalty(100.0), 0.0); assertEquals(2.0, OilModel.penalty(140.0), 0.0)
    }

    @Test fun parkedOilCoolsTowardTheOutsideAir() {
        // Hyundai US 2021/0340892 A1: 90 °C to about 30 °C after two hours at 0 °C.
        assertEquals(30.0, OilModel.soaked(90.0, 0.0, 120.0), 1.0)
        assertEquals(5.0, OilModel.soaked(null, 5.0, null), 0.0)
    }

    @Test fun theScaleIsTheOneTheCalibrationGives() {
        assertEquals(OilModel.calibrate(), OilModel.SCALE, OilModel.SCALE * 0.002)
    }
}

class OilTripsTest {
    private val s = 1_000L

    /** Drives at [kph] for [seconds] after a [idle]-second wait, sampling at 10 Hz. */
    private fun OilTrips.drive(kph: Double, seconds: Int, idle: Int = 0, outside: Double? = 15.0, from: Long = 0): OilTrip? {
        gear(false, from, outside, from)
        var t = from
        repeat(idle * 10) { t += 100; speed(0.0, t) }
        repeat(seconds * 10) { t += 100; speed(kph / 3.6, t) }
        return gear(true, t, outside, t)
    }

    @Test fun tripLengthAndIdleComeFromSpeed() {
        val trip = OilTrips().drive(36.0, 300, idle = 60)!!   // 10 m/s for 5 minutes = 3 km after a minute idling
        assertEquals(3.0, trip.km, 0.02); assertEquals(1.0, trip.idleMinutes, 0.01); assertEquals(6.0, trip.minutes, 0.01)
        assertEquals(OilModel.Kind.SHORT, trip.kind)
    }

    @Test fun parkedWithoutMovingAndLostTripsAreNotRecorded() {
        val t = OilTrips()
        assertNull(t.drive(0.0, 30))
        t.gear(false, 0, null, 0); t.speed(20.0, 100); t.abort()
        assertNull(t.gear(true, 200, null, 200))
        assertEquals(0, t.trips.size)
    }

    @Test fun samplingGapsAreNotCountedAsDistance() {
        val t = OilTrips()
        t.gear(false, 0, null, 0); t.speed(10.0, 100)
        t.speed(10.0, 60 * s)                                   // app frozen for a minute: no samples
        assertNull(t.gear(true, 60 * s + 100, null, 60 * s))   // 1.5 m seen, under the 50 m minimum
    }

    @Test fun aColdShortTripWeighsMoreThanAWarmLongOneAndUnseenDistanceCountsAsNormal() {
        val t = OilTrips()
        val cold = t.drive(30.0, 600, outside = -5.0)!!                      // 5 km from a cold soak
        val long = t.drive(80.0, 2_700, outside = 15.0, from = 1_000 * s)!!  // 60 km right after, already warm
        assertTrue(cold.stress / cold.km > 2.0)
        assertTrue(long.stress / long.km < 1.0)
        assertTrue(cold.stressHigh >= cold.stress)                          // the slow warm-up is the upper estimate
        val sum = t.summary(null, odometerKm = 75.0)
        assertEquals(10.0, sum.unseenKm, 0.2)
        assertEquals(10.0 + cold.stress + long.stress, sum.effectiveKm.first, 0.01)
        assertTrue(sum.dilution!! > 0)
    }

    @Test fun tripsFromBeforeTheTemperatureModelKeepTheirOldWeights() {
        val t = OilTrips(mutableListOf(OilTrip(0, 4.0, 10.0, 1.0, 10.0), OilTrip(0, 4.0, 10.0, 1.0, -5.0), OilTrip(0, 20.0, 30.0, 1.0, 10.0)))
        assertEquals(4 * 2.0 + 4 * 3.5 + 20.0, t.summary(null, null).effectiveKm.first, 1e-9)
    }

    @Test fun anOilChangeEmptiesTheFuelAndEverythingSurvivesJson() {
        val t = OilTrips()
        t.drive(30.0, 600, outside = -5.0)
        assertTrue(t.engine.dilution > 0)
        t.markBaseline("r")
        assertEquals(0.0, t.engine.dilution, 0.0)
        t.drive(72.0, 1_000, from = 10_000 * s)
        val back = OilTrips.fromJson(t.toJson())
        assertEquals(1, back.summary("r", null).trips)
        assertEquals(t.summary("r", null), back.summary("r", null))
        assertEquals(t.engine.oilC, back.engine.oilC)
        assertEquals(2, back.summary(null, null).trips)
    }
}
