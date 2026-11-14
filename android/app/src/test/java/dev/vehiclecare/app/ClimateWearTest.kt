package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RefrigerantModelTest {
    @Test fun theFirstYearLosesUpToTheEuFirstYearRate() {
        val (low, high) = RefrigerantModel.lost(LocalDate.of(2025, 10, 29), LocalDate.of(2026, 10, 29))!!
        assertEquals(1 - Math.exp(-0.035), low, 0.001); assertEquals(0.105, high, 0.001)
    }
    @Test fun theRangeHoldsTheKoreanScrappedCarsAndFutureDatesAreRejected() {
        // Korean cars kept 55.6 % of their charge after 12.4 years.
        val (low, high) = RefrigerantModel.lost(LocalDate.of(2014, 4, 1), LocalDate.of(2026, 8, 25))!!
        assertTrue(0.444 in low..high)
        assertNull(RefrigerantModel.lost(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 10, 29)))
        assertEquals(550, RefrigerantModel.chargeG(VehicleCatalog.TUCSON_GASOLINE))
    }
}

class ClimateIntakeTest {
    @Test fun unknownIntakeCountsAsOutsideAir() {
        assertTrue(Climate(power = true, fan = 3).freshBlowing)
        assertFalse(Climate(power = true, fan = 3, recirc = true).freshBlowing)
        assertTrue(Climate(power = true, fan = 3, recirc = true).recircBlowing)
        assertFalse(Climate(power = false, fan = 3, recirc = false).freshBlowing)
    }
}

class ClimateTrackerTest {
    private val min = 60_000L

    @Test fun timesFiveMinutesFromAWarmCabin() {
        val t = ClimateTracker()
        assertNull(t.update(true, 32.0, 22.0, 30.0, 0))
        assertNull(t.update(true, 29.0, 22.0, 30.0, 3 * min))
        val c = t.update(true, 26.0, 22.0, 30.0, 5 * min)
        assertNotNull(c); assertEquals(1.2, c!!.ratePerMin, 1e-9); assertEquals(30.0, c.outsideC!!, 0.0)
    }

    @Test fun reachingTheSetPointEndsEarly() {
        val t = ClimateTracker()
        t.update(true, 27.0, 22.0, null, 0)
        assertNull(t.update(true, 22.4, 22.0, null, min))           // under the 2-minute minimum
        assertEquals(4.6 / 2.5, t.update(true, 22.4, 22.0, null, (2.5 * min).toLong())!!.ratePerMin, 1e-9)
    }

    @Test fun noSessionWithoutAGapOrWhenInterrupted() {
        val t = ClimateTracker()
        t.update(true, 24.0, 22.0, null, 0)
        assertNull(t.update(true, 20.0, 22.0, null, 6 * min))
        t.update(true, 30.0, 22.0, null, 10 * min)
        t.update(false, 28.0, 22.0, null, 12 * min)                 // A/C off: session dropped
        assertNull(t.update(true, 26.0, 22.0, null, 16 * min))
        assertEquals(0, t.sessions.size)
    }

    @Test fun changeUsesSimilarOutsideTemperaturesOnly() {
        val t = ClimateTracker()
        t.add(ClimateSession(0, 9.0, 40.0, 22.0, 35.0)); t.markBaseline("r")
        listOf(1.0, 1.2, 1.1, 1.0, 1.2, 1.1, 1.0, 1.2).forEach { t.add(ClimateSession(0, it, 32.0, 22.0, 30.0)) }
        assertNull(t.summary("r").second)
        t.add(ClimateSession(0, 0.3, 32.0, 22.0, 5.0))                     // winter run, not comparable
        t.add(ClimateSession(0, 0.88, 32.0, 22.0, 31.0))
        val (latest, change, n) = t.summary("r")
        assertEquals(0.88, latest!!, 0.0); assertEquals(10, n)
        assertEquals((0.88 / 1.1 - 1) * 100, change!!, 1e-9)
        val back = ClimateTracker.fromJson(t.toJson())
        assertEquals(t.summary("r"), back.summary("r"))
    }

    @Test fun theShareClosedInThreeMinutesIsRecordedAndKept() {
        val t = ClimateTracker()
        var ms = 0L
        // 34 °C cabin against 22 °C, falling 1.5 °C a minute: 4.5 of 12 closed after three minutes.
        for (i in 0..30) { t.update(true, 34.0 - 1.5 * i / 6, 22.0, 30.0, ms); ms += 10_000 }
        val s = t.sessions.single()
        assertEquals(4.5 / 12, s.closed!!, 1e-9)
        assertEquals(s.closed, ClimateTracker.fromJson(t.toJson()).sessions.single().closed)
    }

    @Test fun aWinterRunDoesNotCountAgainstASummerReference() {
        val t = ClimateTracker(); t.markBaseline("r")
        repeat(8) { t.add(ClimateSession(0, 2.0, 32.0, 22.0, 30.0, .55)) }
        repeat(6) { t.add(ClimateSession(0, .5, 26.0, 22.0, 8.0, .1)) }
        assertNull(t.decline("r"))
    }

    @Test fun aSessionIsOnlyHeldAgainstReferenceRunsWithTheSameBlowerAndIntake() {
        val t = ClimateTracker(); t.markBaseline("r")
        repeat(8) { t.add(ClimateSession(0, 2.0, 32.0, 22.0, 30.0, .55, fan = 1.0, recirc = true)) }
        // A driver who later runs the blower at half, on fresh air, cools slower without any loss of charge.
        repeat(6) { t.add(ClimateSession(0, 1.2, 32.0, 22.0, 30.0, .35, fan = .5, recirc = false)) }
        assertNull(t.decline("r"))
        assertNull(t.recovery("r").first)
        // The same slower runs at the reference blower and intake are a decline.
        repeat(6) { t.add(ClimateSession(0, 1.2, 32.0, 22.0, 30.0, .35, fan = .9, recirc = true)) }
        assertEquals(DueStatus.SOON, t.decline("r")!!.status)
        assertEquals(.35 / .55, t.recovery("r").first!!, 1e-9)
    }

    @Test fun blowerAndIntakeAreSampledThroughTheProbeAndKept() {
        val t = ClimateTracker()
        t.update(true, 34.0, 22.0, 30.0, 0, .5, true)
        t.update(true, 31.0, 22.0, 30.0, 60_000, 1.0, true)
        t.update(true, 28.0, 22.0, 30.0, 180_000, 1.0, false)
        t.update(true, 27.0, 22.0, 30.0, 240_000, .25, false)          // after the probe; not counted
        val s = t.update(true, 26.0, 22.0, 30.0, 300_000, .25, false)!!
        assertEquals(2.5 / 3, s.fan!!, 1e-9)
        assertEquals(true, s.recirc)
        val back = ClimateTracker.fromJson(t.toJson()).sessions.single()
        assertEquals(s.fan, back.fan); assertEquals(s.recirc, back.recirc)
    }

    @Test fun aWarmUpIsTimedTowardTheSetPointFromBelowAndOnlyFromAColdStart() {
        val t = ClimateTracker(heating = true)
        // Outside the cold-start window nothing opens.
        assertNull(t.update(true, 4.0, 22.0, 0.0, 0, 1.0, false, 40.0, canStart = false))
        t.update(true, 4.0, 22.0, 0.0, 60_000, 1.0, false, 40.0, canStart = false)
        assertTrue(t.sessions.isEmpty())
        t.update(true, 4.0, 22.0, 0.0, 120_000, 1.0, false, 40.0, canStart = true)
        t.update(true, 9.0, 22.0, 0.0, 120_000 + 4 * 60_000L, 1.0, false, 60.0, canStart = false)
        t.update(true, 13.0, 22.0, 0.0, 120_000 + 8 * 60_000L, 1.0, false, 50.0, canStart = false)
        val s = t.update(true, 15.0, 22.0, 0.0, 120_000 + 10 * 60_000L, 1.0, false, 50.0, canStart = false)!!
        assertEquals(9.0 / 18, s.closed!!, 1e-9)
        assertEquals(50.0, s.speed!!, 1e-9)
        assertEquals(1.1, s.ratePerMin, 1e-9)
        val back = ClimateTracker.fromJson(t.toJson(), heating = true)
        assertTrue(back.heating); assertEquals(s.speed, back.sessions.single().speed)
    }

    @Test fun aSlowerHeaterIsBroughtToInspection() {
        val heat = ClimateTracker(heating = true); heat.markBaseline("r")
        repeat(8) { heat.add(ClimateSession(0, 2.0, 4.0, 22.0, 0.0, .6, 1.0, false, 45.0)) }
        // Driven much slower: no reference run is like it, so it says nothing.
        repeat(6) { heat.add(ClimateSession(0, 1.0, 4.0, 22.0, 0.0, .3, 1.0, false, 10.0)) }
        assertNull(heat.decline("r"))
        repeat(6) { heat.add(ClimateSession(0, 1.0, 4.0, 22.0, 0.0, .3, 1.0, false, 50.0)) }
        val record = ServiceRecord("r", Part.HVAC, LocalDate.of(2026, 1, 5), null, ServiceAction.INSPECT)
        val a = CompositeJudge.assess(RecommendedCare.plan(Part.HVAC, "r"), record, null, LocalDate.of(2026, 2, 20), SignalSnapshot(heatup = heat))
        assertEquals(DueStatus.SOON, a.status)
        assertEquals("난방 회복 점검 직후 대비 50%", a.drivenBy)
    }
}
