package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WiperJudgmentTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun s(db: Double, level: String = "LOW") = SoundSession(0, 120, doubleArrayOf(0.0, 0.0, 0.0, 0.0, db), level)
    /** A new blade's first sessions are loud with dust; they must not raise the reference. */
    private fun sound(base: List<Double>, recent: List<Double>, level: String = "LOW") = WiperSound().apply {
        markBaseline("r"); repeat(WiperSound.WARMUP) { add(s(20.0, level)) }; base.forEach { add(s(it, level)) }; recent.forEach { add(s(it, level)) }
    }
    private val base = listOf(11.0, 12.5, 11.8, 12.2, 11.5, 12.9, 12.0, 11.2)

    @Test fun aRiseCountsOnlyWhenEveryRecentSessionIsLouder() {
        assertNull(sound(base, listOf(15.0, 16.0, 15.5, 12.8, 16.2, 15.9)).shift("r"))
        val shift = sound(base, listOf(15.0, 16.0, 15.5, 13.1, 16.2, 15.9)).shift("r")!!
        assertEquals(15.7 - 11.9, shift.db, 1e-9)
    }

    @Test fun sessionsAtAnotherSwitchLevelAreNotCompared() {
        val w = WiperSound()
        w.markBaseline("r")
        repeat(WiperSound.WARMUP) { w.add(s(12.0, "LOW")) }
        base.forEach { w.add(s(it, "LOW")) }
        // High speed is louder by itself; it has no reference of its own yet.
        repeat(6) { w.add(s(18.0, "HIGH")) }
        assertNull(w.shift("r"))
    }

    @Test fun aNormalYearAgesExactlyOneYear() {
        val from = LocalDate.of(2025, 1, 1)
        assertEquals(365.0, RubberAging.equivalentDays(from, from.plusDays(365), OutsideDays(), RubberAging.EA_LOW), 1e-6)
        assertEquals(365.0, RubberAging.equivalentDays(from, from.plusDays(365), OutsideDays(), RubberAging.EA_HIGH), 1e-6)
    }

    @Test fun aHotSummerAgesFasterAndTheHighEnergyMore() {
        val from = LocalDate.of(2026, 1, 1)
        val hot = OutsideDays()
        // July and August afternoons read at 35 °C, about 5 °C above the normal at 15:00.
        var d = LocalDate.of(2026, 7, 1)
        while (d.isBefore(LocalDate.of(2026, 9, 1))) { hot.add(d.atTime(15, 0), 35.0); d = d.plusDays(1) }
        val low = RubberAging.equivalentDays(from, LocalDate.of(2027, 1, 1), hot, RubberAging.EA_LOW)
        val high = RubberAging.equivalentDays(from, LocalDate.of(2027, 1, 1), hot, RubberAging.EA_HIGH)
        assertTrue(low > 380); assertTrue(high > low)
    }

    @Test fun ageingAtNormalTemperaturesNeverOutrunsTheCalendar() {
        val plan = RecommendedCare.plan(Part.WIPER, "r")
        val record = ServiceRecord("r", Part.WIPER, today.minusDays(340), null, ServiceAction.REPLACE)
        val a = CompositeJudge.assess(plan, record, null, today, SignalSnapshot())
        assertEquals(DueStatus.SOON, a.status); assertNull(a.drivenBy)
    }

    @Test fun heatBringsTheBladeForward() {
        val plan = RecommendedCare.plan(Part.WIPER, "r")
        val record = ServiceRecord("r", Part.WIPER, LocalDate.of(2025, 12, 1), null, ServiceAction.REPLACE)
        val hot = OutsideDays()
        // One July of afternoons read at 34 °C, about 5 °C over the normal at 15:00.
        var d = LocalDate.of(2026, 7, 1)
        while (d.isBefore(LocalDate.of(2026, 8, 1))) { hot.add(d.atTime(15, 0), 34.0); d = d.plusDays(1) }
        val a = CompositeJudge.assess(plan, record, null, today, SignalSnapshot(outside = hot))
        assertEquals(DueStatus.UPCOMING, a.schedule.status)
        assertEquals(DueStatus.SOON, a.status)
        assertTrue(a.drivenBy!!.startsWith("블레이드 노화 환산"))
    }

    @Test fun aLouderBladeIsBroughtToInspection() {
        val plan = RecommendedCare.plan(Part.WIPER, "r")
        val record = ServiceRecord("r", Part.WIPER, today.minusMonths(5), null, ServiceAction.REPLACE)
        val loud = sound(base, listOf(15.0, 16.0, 15.5, 13.1, 16.2, 15.9))
        val a = CompositeJudge.assess(plan, record, null, today, SignalSnapshot(sound = loud))
        assertEquals(DueStatus.SOON, a.status)
        assertEquals("작동 소리 +3.8 dB", a.drivenBy)
    }

    @Test fun theNormalDayRunsFromTheMinimumAtSixToTheMaximumAtThree() {
        assertEquals(21.8, RubberAging.normalAt(8, 6.0), 1e-9)
        assertEquals(29.9, RubberAging.normalAt(8, 15.0), 1e-9)
        assertEquals(RubberAging.normalAt(8, 0.0), RubberAging.normalAt(8, 24.0), 1e-9)
        assertTrue(RubberAging.normalAt(8, 8.0) < RubberAging.normalAt(8, 12.0))
    }

    @Test fun aMorningCommuteAtTheNormalForItsHourIsNotACoolDay() {
        val from = LocalDate.of(2026, 7, 1)
        val days = OutsideDays()
        var d = from
        // Every morning read at exactly the normal for 08:00, well under the daily maximum.
        while (d.isBefore(LocalDate.of(2026, 8, 1))) { days.add(d.atTime(8, 0), RubberAging.normalAt(7, 8.0)); d = d.plusDays(1) }
        assertEquals(31.0, RubberAging.equivalentDays(from, LocalDate.of(2026, 8, 1), days, RubberAging.EA_LOW), 1e-6)
    }

    @Test fun aSunSoakedStartAddsHeatAndAShadedOneDoesNot() {
        val from = LocalDate.of(2026, 7, 1); val to = LocalDate.of(2026, 7, 2)
        val shade = OutsideDays().apply { soak(from, 32.0, 29.0) }
        assertEquals(1.0, RubberAging.equivalentDays(from, to, shade, RubberAging.EA_LOW), 1e-9)
        val sun = OutsideDays().apply { soak(from, 50.0, 31.0); soak(from, 45.0, 31.0) }
        assertEquals(50.0, sun.day(from)!!.soakC!!, 0.0)
        // A sun-soaked day is a weight on the day, not the cabin temperature, so both activation energies agree.
        assertEquals(RubberAging.SUN_DAY, RubberAging.equivalentDays(from, to, sun, RubberAging.EA_LOW), 1e-9)
        assertEquals(RubberAging.SUN_DAY, RubberAging.equivalentDays(from, to, sun, RubberAging.EA_HIGH), 1e-9)
        val back = OutsideDays.fromJson(org.json.JSONObject(sun.toJson().toString()))
        assertEquals(sun.day(from), back.day(from))
    }

    @Test fun aRiseThatStaysAfterCleaningIsConfirmedAndDue() {
        val w = sound(base, listOf(15.0, 16.0, 15.5, 13.1, 16.2, 15.9))
        assertTrue(!w.shift("r")!!.confirmed)
        w.markCleaned("r", "LOW")
        // Until the cleaned level has run again the rise is set aside.
        w.add(s(15.4)); w.add(s(16.1))
        assertNull(w.shift("r")); assertEquals(2, w.sinceCleaning("r"))
        w.add(s(15.8))
        val shift = w.shift("r")!!
        assertTrue(shift.confirmed); assertEquals(15.8 - 11.9, shift.db, 1e-9)
        assertNull(w.sinceCleaning("r"))
        val plan = RecommendedCare.plan(Part.WIPER, "r")
        val record = ServiceRecord("r", Part.WIPER, today.minusMonths(5), null, ServiceAction.REPLACE)
        val a = CompositeJudge.assess(plan, record, null, today, SignalSnapshot(sound = w))
        assertEquals(DueStatus.DUE, a.status)
        assertEquals("닦은 뒤에도 작동 소리 +3.9 dB", a.drivenBy)
    }

    @Test fun aRiseThatGoesWithCleaningWasDirtAndStartsOver() {
        val w = sound(base, listOf(15.0, 16.0, 15.5, 13.1, 16.2, 15.9))
        w.markCleaned("r", "LOW")
        listOf(12.1, 15.9, 16.0).forEach { w.add(s(it)) }
        assertNull(w.shift("r")); assertNull(w.sinceCleaning("r"))
        // Loud again for six sessions after the cleaning: shown again, still unconfirmed, and cleanable again.
        listOf(15.0, 16.0, 15.5, 15.2).forEach { w.add(s(it)) }
        assertTrue(!w.shift("r")!!.confirmed)
    }

    @Test fun onlyTheLevelThatRoseCanConfirmIt() {
        val w = sound(base, listOf(15.0, 16.0, 15.5, 13.1, 16.2, 15.9))
        base.forEach { w.add(s(it, "HIGH")) }
        w.markCleaned("r", "LOW")
        repeat(3) { w.add(s(16.0, "HIGH")) }
        assertNull(w.shift("r")); assertEquals(0, w.sinceCleaning("r"))
        val back = WiperSound.fromJson(org.json.JSONObject(w.toJson().toString()))
        assertEquals(0, back.sinceCleaning("r"))
    }
}
