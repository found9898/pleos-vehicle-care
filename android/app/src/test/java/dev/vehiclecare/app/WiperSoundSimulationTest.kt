package dev.vehiclecare.app

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Monte Carlo check of [WiperSound.shift] on simulated rain seasons. No study gives the size of the noise rise of a worn
 * blade, so the simulation asks the questions the detector must answer either way: how often it fires on a blade that has
 * not changed, and how fast it catches a sustained rise of a given size.
 *
 * Session model: about 120 wiping sessions a year (LOW 50 %, INTERMITTENT 35 %, HIGH 15 %), each a level offset plus
 * heavy-tailed scatter (Student t, 4 degrees of freedom) of σ dB, and 5 % of sessions masked by music or speech
 * (+3 to +10 dB). Water changes squeal frequency and amplitude (Reddyhoff et al., J. Vib. Acoust. 2015), which is what σ stands for.
 */
class WiperSoundSimulationTest {
    private val levels = listOf("LOW" to .5, "INTERMITTENT" to .35, "HIGH" to .15)
    private val offsets = mapOf("LOW" to 0.0, "INTERMITTENT" to -1.5, "HIGH" to 2.0)

    private fun t4(r: Random): Double {
        val z = { var s = 0.0; repeat(12) { s += r.nextDouble() }; s - 6 }
        val n = z(); var chi = 0.0; repeat(4) { val g = z(); chi += g * g }
        return n / sqrt(chi / 4) / sqrt(2.0)
    }

    /** Days until the detector first fires within [years], or null. The rise ramps to [rise] dB over 180 days from [onset]. */
    private fun run(r: Random, sigma: Double, rise: Double, onset: Int, years: Int = 2, dust: Boolean = false, warmup: Int = WiperSound.WARMUP): Int? {
        val w = WiperSound(warmup = warmup)
        w.markBaseline("r")
        var day = 0.0; var n = 0
        while (day < 365 * years) {
            day += -365.0 / 120 * kotlin.math.ln(1 - r.nextDouble())
            var u = r.nextDouble(); val level = levels.first { (_, p) -> (u - p).also { u = it } < 0 }.first
            val wear = if (day < onset) 0.0 else rise * minOf(1.0, (day - onset) / 180)
            val masked = if (r.nextDouble() < .05) 3 + 7 * r.nextDouble() else 0.0
            // A new blade's first strokes drag dust across the glass (Ford US10068388): +3 to +8 dB on its first two sessions.
            val dusty = if (dust && n++ < 2) 3 + 5 * r.nextDouble() else 0.0
            val db = 12 + offsets.getValue(level) + sigma * t4(r) + wear + masked + dusty
            w.add(SoundSession(0, 120, doubleArrayOf(0.0, 0.0, 0.0, 0.0, db), level))
            if (w.shift("r") != null) return day.toInt()
        }
        return null
    }

    @Test fun falseAlarmsAndDetectionDelay() {
        val r = Random(20261030)
        val trials = 2000
        val report = StringBuilder("σ dB | 2년 오경보 | +3 dB 탐지 · 중앙 지연(일) | +6 dB 탐지 · 중앙 지연(일)\n")
        // 4 dB: the switch level does not fix the rain (Hill 2019 found no clear link), so scatter may run wider than 3.
        val results = listOf(1.0, 2.0, 3.0, 4.0).associateWith { sigma ->
            val fa = (1..trials).count { run(r, sigma, 0.0, 0) != null } / trials.toDouble()
            val detect = listOf(3.0, 6.0).map { rise ->
                val days = (1..trials).mapNotNull { run(r, sigma, rise, 180)?.minus(180) }.filter { it >= 0 }.sorted()
                days.size / trials.toDouble() to (days.getOrNull(days.size / 2) ?: -1)
            }
            report.append(String.format("%.0f | %.1f%% | %.0f%% · %d | %.0f%% · %d\n", sigma, fa * 100,
                detect[0].first * 100, detect[0].second, detect[1].first * 100, detect[1].second))
            Triple(fa, detect[0], detect[1])
        }
        println(report)
        // A blade that has not changed must almost never be called noisy in two rainy seasons at typical scatter.
        assertTrue(results.getValue(2.0).first < 0.04)
        // A clear rise (+6 dB at 2 dB scatter) must be caught in most seasons.
        assertTrue(results.getValue(2.0).third.first > 0.9)
    }

    @Test fun leavingOutTheDustyFirstSessionsKeepsTheReferenceHonest() {
        val r = Random(4)
        val trials = 2000
        val rows = listOf(0, WiperSound.WARMUP).associateWith { warmup ->
            val fa = (1..trials).count { run(r, 2.0, 0.0, 0, dust = true, warmup = warmup) != null } / trials.toDouble()
            val caught = (1..trials).count { run(r, 2.0, 3.0, 180, dust = true, warmup = warmup)?.let { it >= 180 } == true } / trials.toDouble()
            fa to caught
        }
        println(rows.entries.joinToString("\n", "새 블레이드 먼지(첫 2회 +3~8 dB), σ 2 dB\n제외 회수 | 2년 오경보 | +3 dB 탐지\n") { (w, v) ->
            String.format("%d | %.1f%% | %.0f%%", w, v.first * 100, v.second * 100) })
        assertTrue(rows.getValue(WiperSound.WARMUP).second > rows.getValue(0).second)
        assertTrue(rows.getValue(WiperSound.WARMUP).first < 0.04)
    }

    /**
     * The owner cleans at the first rise. A worn edge stays loud; a film on the glass is gone and builds up again over
     * [refilmDays]. Returns whether the blade was confirmed within two years.
     */
    private fun cleaned(r: Random, sigma: Double, rise: Double, film: Boolean, refilmDays: Double = 180.0): Boolean? {
        val w = WiperSound(); w.markBaseline("r")
        var day = 0.0; var cleanedOn: Double? = null
        while (day < 730) {
            day += -365.0 / 120 * kotlin.math.ln(1 - r.nextDouble())
            var u = r.nextDouble(); val level = levels.first { (_, p) -> (u - p).also { u = it } < 0 }.first
            val onset = 90.0
            var extra = if (day < onset) 0.0 else rise * minOf(1.0, (day - onset) / 90)
            if (film) cleanedOn?.let { c -> extra = rise * minOf(1.0, (day - c) / refilmDays) }
            val masked = if (r.nextDouble() < .05) 3 + 7 * r.nextDouble() else 0.0
            w.add(SoundSession(0, 120, doubleArrayOf(0.0, 0.0, 0.0, 0.0, 12 + offsets.getValue(level) + sigma * t4(r) + extra + masked), level))
            val shift = w.shift("r") ?: continue
            if (shift.confirmed) return true
            if (w.sinceCleaning("r") == null) { w.markCleaned("r", shift.level); cleanedOn = day }
        }
        return if (cleanedOn == null) null else false
    }

    @Test fun cleaningSeparatesDirtFromAWornEdge() {
        val r = Random(31)
        val n = 2000
        val report = StringBuilder("원인 | 첫 경보 후 닦음 | 2년 안에 교체 확정\n")
        val rates = mutableMapOf<String, Double>()
        for ((name, rise, film) in listOf(Triple("변화 없음", 0.0, false), Triple("유막 +6 dB", 6.0, true), Triple("마모 +6 dB", 6.0, false), Triple("마모 +3 dB", 3.0, false))) {
            val runs = List(n) { cleaned(r, 2.0, rise, film) }
            val alarmed = runs.count { it != null }
            val confirmed = runs.count { it == true }
            rates[name] = confirmed.toDouble() / n
            report.append(String.format("%s | %.1f%% | %.1f%%\n", name, alarmed * 100.0 / n, confirmed * 100.0 / n))
        }
        println(report)
        assertTrue(rates.getValue("변화 없음") < 0.005)   // chance alone almost never confirms a blade
        assertTrue(rates.getValue("마모 +6 dB") > 0.9)     // a clear rise that survives cleaning is confirmed
        assertTrue(rates.getValue("유막 +6 dB") < rates.getValue("마모 +6 dB") / 2)
    }
}
