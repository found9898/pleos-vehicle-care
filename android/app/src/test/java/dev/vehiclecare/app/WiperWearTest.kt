package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class WiperCyclesTest {
    @Test fun countsAtTheRateOfTheLevelThatWasRunning() {
        val c = WiperCycles()
        c.update(WiperModel.Level.LOW, 0); c.update(WiperModel.Level.HIGH, 60_000); c.update(null, 120_000)
        assertEquals(39.0 + 57.0, c.low, 1e-9); assertEquals(55.0 + 79.0, c.high, 1e-9)
        // Stopped: time passing adds nothing.
        c.update(null, 600_000)
        assertEquals(96.0, c.low, 1e-9)
    }

    @Test fun belowFreezingOnlyTheUpperCountDoublesAndTheLastUseIsKept() {
        val c = WiperCycles()
        c.outsideC = -3.0
        c.update(WiperModel.Level.LOW, 0, 1_000L); c.update(null, 60_000, 61_000L)
        assertEquals(39.0, c.low, 1e-9); assertEquals(110.0, c.high, 1e-9)
        assertEquals(61_000L, c.lastWipedAt)
        assertEquals(61_000L, WiperCycles.fromJson(c.toJson()).lastWipedAt)
    }

    @Test fun onlyWipingBeyondTheTypicalPaceAddsBladeAge() {
        // A year at the typical 52,000 adds nothing; 252,000 adds a year.
        assertEquals(0.0, WiperModel.excessDays(52_000.0, 365.0), 1e-9)
        assertEquals(365.0, WiperModel.excessDays(252_000.0, 365.0), 1e-9)
    }

    @Test fun baselineAndJsonRoundTrip() {
        val c = WiperCycles(1_000.0, 2_000.0)
        c.markBaseline("r"); c.low += 10; c.high += 20
        val back = WiperCycles.fromJson(c.toJson())
        assertEquals(Triple(10.0, 20.0, true), back.since("r"))
        assertEquals(Triple(1_010.0, 2_020.0, false), back.since("x"))
    }
}

class WiperAcousticsTest {
    private val rate = 44_100
    private fun tone(hz: Double, amp: Double, noise: Double, seed: Int) = Random(seed).let { r ->
        ShortArray(WiperAcoustics.FRAME) { (amp * sin(2 * PI * hz * it / rate) + noise * (r.nextDouble() - 0.5)).toInt().toShort() }
    }

    @Test fun toneLandsInItsBand() {
        val db = WiperAcoustics.bandDb(tone(2_500.0, 8_000.0, 0.0, 1), rate)
        // 2.5 kHz sits in the 2-4 kHz band and dominates the 1-2 kHz band by far.
        assertTrue(db[1] - db[0] > 30)
        assertEquals(db[1], db[4], 0.1)
    }

    @Test fun strokesOverFloorGiveExcessOnlyInTheirBand() {
        // Alternating frames: a 2.5 kHz squeal over noise, then noise alone.
        val frames = (0 until 400).map { i -> WiperAcoustics.bandDb(tone(2_500.0, if (i % 3 == 0) 6_000.0 else 0.0, 400.0, i), rate) }
        val ex = WiperAcoustics.excess(frames)!!
        assertTrue("squeal band ${ex[1]}", ex[1] > 15)
        assertTrue("quiet band ${ex[3]}", ex[3] < 3)
    }

    @Test fun steadyNoiseHasNoExcessAndShortSessionsAreDropped() {
        val frames = (0 until 400).map { WiperAcoustics.bandDb(tone(0.0, 0.0, 1_000.0, it), rate) }
        assertTrue(WiperAcoustics.excess(frames)!!.all { it < 3 })
        assertNull(WiperAcoustics.excess(frames.take(WiperAcoustics.MIN_FRAMES - 1)))
    }
}

class WiperSoundTest {
    private fun s(total: Double) = SoundSession(0, 30, doubleArrayOf(0.0, 0.0, 0.0, 0.0, total))

    @Test fun changeComparesLatestWithFirstSessionsAfterReplacement() {
        val w = WiperSound()
        w.add(s(50.0)); w.markBaseline("r")
        // The new blade's first sessions are dusty and left out.
        repeat(WiperSound.WARMUP) { w.add(s(30.0)) }
        (1..8).forEach { w.add(s(10.0 + it % 2)) }
        assertEquals(null to 8, w.change("r"))
        (1..7).forEach { w.add(s(13.0 + it)) }
        // Reference 10.5 (eight alternating 10 and 11); latest six 15..20 average 17.5.
        assertEquals(7.0, w.change("r").first!!, 1e-9)
    }

    @Test fun trimmingKeepsMarksPointingAtTheSameSessions() {
        val w = WiperSound()
        repeat(WiperSound.MAX) { w.add(s(1.0)) }
        w.markBaseline("r")
        repeat(4) { w.add(s(5.0)) }
        assertEquals(WiperSound.MAX - 4, w.marks["r"])
        assertEquals(4 - WiperSound.WARMUP, w.change("r").second)
        val back = WiperSound.fromJson(w.toJson())
        assertEquals(w.sessions.size, back.sessions.size); assertEquals(w.marks, back.marks)
    }
}
