package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCounterTest {
    @Test fun countsOnlyWhileOn() {
        val u = UsageCounter()
        u.onState(true, 1_000); u.onState(false, 91_000)
        u.onState(false, 200_000)
        u.onState(true, 300_000); u.onState(false, 330_500)
        assertEquals(120, u.total)
        assertNull(u.runningSinceMs)
    }

    @Test fun repeatedOnDoesNotRestartInterval() {
        val u = UsageCounter()
        u.onState(true, 0); u.onState(true, 40_000); u.onState(false, 100_000)
        assertEquals(100, u.total)
    }

    @Test fun checkpointKeepsFractionsForLater() {
        val u = UsageCounter()
        u.onState(true, 0)
        u.checkpoint(59_700); assertEquals(59, u.total)
        u.checkpoint(60_400); assertEquals(60, u.total)
        u.onState(false, 61_000); assertEquals(61, u.total)
    }

    @Test fun clockGoingBackwardsAddsNothing() {
        val u = UsageCounter(); u.onState(true, 50_000); u.checkpoint(10_000)
        assertEquals(0, u.total)
    }

    @Test fun usageSinceReplacement() {
        val u = UsageCounter(total = 500)
        u.markBaseline("r1")
        u.onState(true, 0); u.onState(false, 30_000)
        assertEquals(30L to true, u.since("r1"))
    }

    @Test fun recordBeforeTrackingFallsBackToTrackingTotal() {
        val (seconds, sinceRecord) = UsageCounter(total = 420).since("older-record")
        assertEquals(420, seconds); assertFalse(sinceRecord)
        assertTrue(UsageCounter(baselines = mutableMapOf("r" to 0)).since("r").second)
    }
}

class ClimateTest {
    @Test fun acCountsOnlyWhenCarSaysOnAndPowerIsNotOff() {
        assertTrue(Climate(power = null, ac = true).acRunning)
        assertTrue(Climate(power = true, ac = true).acRunning)
        assertFalse(Climate(power = false, ac = true).acRunning)
        assertFalse(Climate(power = true, ac = null).acRunning)
    }
    @Test fun blowerNeedsAReportedFanSpeed() {
        assertTrue(Climate(power = true, fan = 3).blowing)
        assertFalse(Climate(power = true, fan = 0).blowing)
        assertFalse(Climate(power = true, fan = null).blowing)
        assertFalse(Climate(power = false, fan = 5).blowing)
    }
    @Test fun eachUsageInformsOnePart() {
        assertEquals(Usage.AC, Usage.of(Part.HVAC)); assertEquals(Usage.BLOWER, Usage.of(Part.CABIN_FILTER))
        assertEquals(null, Usage.of(Part.TIRE))
    }
}

class BrakePressTest {
    @Test fun countsOnlyReleasedToPressed() {
        val e = PressEdge()
        val seen = listOf(false, true, true, false, true, false, false, true).count { e.pressed(it) }
        assertEquals(3, seen)
    }
    @Test fun firstReadingIsNeverAPress() {
        assertFalse(PressEdge().pressed(true))
    }
    @Test fun unknownOrHiddenBreaksTheChain() {
        val e = PressEdge(); e.pressed(false); e.forget()
        assertFalse(e.pressed(true))
        e.pressed(false); e.pressed(null)
        assertFalse(e.pressed(true))
    }
    @Test fun pressesAccumulateAgainstBaseline() {
        val c = UsageCounter(); c.add(40); c.markBaseline("inspect"); c.add(12)
        assertEquals(12L to true, c.since("inspect")); assertTrue(Usage.BRAKE.counts)
    }
}
