package dev.vehiclecare.app

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.random.Random

/**
 * Cold-start warm-up simulation used to choose the heating rules. A lumped engine (metal and coolant) heated by the
 * share of fuel energy that goes to coolant, a wax thermostat opening to the radiator above its regulating range, and a
 * heater core feeding the two-node cabin of CooldownSimulationTest (Brusey et al., arXiv:1704.07899, coupling as
 * calibrated there). Faults: a thermostat stuck open (part of the radiator flow always passes) and a heater that moves
 * less heat (low coolant, air in the core, a clogged core).
 *
 * Calibration (see [calibrate]): OBD II thermostat monitors expect a healthy engine to reach within 20 °F (11.1 °C) of
 * the regulating temperature within a manufacturer time interval (13 CCR 1968.2 (e)(10.2.1)); a monitor described in
 * the trade press used 300-800 s. The healthy engine here reaches it inside that window at -7 °C and 50 km/h.
 */
class HeatupSimulationTest {
    data class Day(val outside: Double, val engineStart: Double, val cabinStart: Double, val set: Double, val speed: Double,
                   val fan: Double, val recirc: Boolean, val solar: Double, val spread: Double, val delayS: Int)

    var engineCapacity = 40_000.0      // J/K warmed while the thermostat is shut: about 7 L of coolant and the metal around it
    val regulating = 90.0
    /**
     * Heat to coolant, W. At 50 km/h a 1.6 L petrol car burns about 2.5 L/h (22 kW of fuel), of which roughly a third goes to
     * coolant; idle about 3 kW.
     */
    fun engineHeat(kph: Double) = 3_000.0 + 140.0 * kph
    private fun engineLoss(kph: Double) = 5 + .2 * kph

    /** Cabin air every 10 s for [steps]; also returns coolant at the end. [stuck] is the radiator share always open, [heater] the core's remaining capacity. */
    fun trace(d: Day, stuck: Double, heater: Double, steps: Int = 74, coolantOut: DoubleArray? = null): DoubleArray {
        var engine = d.engineStart; var air = d.cabinStart; var inner = d.cabinStart
        val qIn = engineHeat(d.speed) * d.spread
        val radiator = 300.0 + 10.0 * d.speed
        val flow = 100.0 * d.fan             // W/K through the heater core at this blower share
        val leak = 15.0; val shell = 23.0 / d.spread
        // Run the engine alone until the driver turns the heater on.
        repeat(d.delayS / 2) { engine += 2 * (qIn - engineLoss(d.speed) * (engine - d.outside) - maxOf(stuck, ((engine - regulating + 5) / 10).coerceIn(0.0, 1.0)) * radiator * (engine - d.outside)) / engineCapacity }
        val out = DoubleArray(steps) { _ ->
            val now = air
            repeat(5) {
                val open = maxOf(stuck, ((engine - regulating + 5) / 10).coerceIn(0.0, 1.0))
                val intake = if (d.recirc) air else d.outside
                val demand = ((d.set + 1 - air) / 1.0).coerceIn(0.0, 1.0)
                val core = maxOf(0.0, 0.8 * heater * flow * (engine - intake)) * demand
                val vent = leak + if (d.recirc) 0.0 else flow
                engine += 2 * (qIn - engineLoss(d.speed) * (engine - d.outside) - open * radiator * (engine - d.outside) - core) / engineCapacity
                val dAir = (core + vent * (d.outside - air) + shell * (d.outside - air) + 800 * (inner - air) + d.solar * .3) / 24_000
                val dInner = (800 * (air - inner) + d.solar * .7) / 70_650
                air += 2 * dAir; inner += 2 * dInner
            }
            Math.round(now * 2) / 2.0
        }
        coolantOut?.set(0, engine)
        return out
    }

    @Test fun calibrate() {
        val d = Day(-7.0, -7.0, -7.0, 22.0, 50.0, 1.0, false, 0.0, 1.0, 0)
        // Seconds for the coolant to come within 11.1 °C of regulating, heater on.
        var t = 0; val c = DoubleArray(1)
        while (t < 1800) { trace(d.copy(delayS = 0), 0.0, 1.0, t / 10 + 1, c); if (c[0] >= regulating - 11.1) break; t += 30 }
        val cabin15 = trace(d, 0.0, 1.0, 91).last()
        val eq = { stuck: Double -> DoubleArray(1).also { trace(d, stuck, 1.0, 360, it) }[0] }
        println(String.format("CAL coolant to %.1f °C at %d s · cabin at 15 min %.1f °C", regulating - 11.1, t, cabin15))
        println("CAL stuck share -> coolant after 60 min: " + listOf(0.0, .02, .03, .04, .05, .06, .08, .1, .12, .15, .2).joinToString { String.format("%.2f:%.0f", it, eq(it)) })
        assertTrue(t in 300..800)
    }

    private fun gauss(r: Random): Double { var s = 0.0; repeat(12) { s += r.nextDouble() }; return s - 6 }

    /** A winter cold start after at least three hours parked. */
    private fun day(r: Random, habit: Double, fixedFan: Double, fixedRecirc: Boolean): Day {
        val outside = -12 + 22 * r.nextDouble()
        val fan = when { habit < .4 -> 1.0 - .125 * r.nextInt(2); habit < .7 -> fixedFan; else -> (3 + r.nextInt(6)) / 8.0 }
        val recirc = if (habit in .4..0.7) fixedRecirc else r.nextDouble() < .3
        return Day(outside, outside + 8 * r.nextDouble(), outside + 4 * r.nextDouble(), 21 + 4 * r.nextDouble(), 20 + 60 * r.nextDouble(),
            fan, recirc, 300 * r.nextDouble(), exp(.18 * gauss(r)), (90 * r.nextDouble()).toInt())
    }

    private fun session(t: ClimateTracker, d: Day, stuck: Double, heater: Double, startMs: Long, conditions: Boolean) {
        val steps = ((t.probeMinutes + 2) * 6).toInt() + 2
        trace(d, stuck, heater, steps).forEachIndexed { k, c ->
            t.update(true, c, d.set, d.outside, startMs + k * 10_000L, d.fan.takeIf { conditions }, d.recirc.takeIf { conditions }, d.speed.takeIf { conditions })
        }
        t.update(false, null, d.set, d.outside, startMs + (steps + 1) * 10_000L)
    }

    /** Two winters of 80 cold starts; the fault appears at start 40. */
    private fun season(r: Random, probe: Double, stuck: Double, heater: Double, conditions: Boolean = true): Pair<Int?, DueStatus?> {
        val t = ClimateTracker(heating = true, heatProbe = probe); t.markBaseline("r")
        val habit = r.nextDouble(); val fixedFan = (4 + r.nextInt(5)) / 8.0; val fixedRecirc = r.nextDouble() < .3
        for (i in 0 until 160) {
            val d = day(r, habit, fixedFan, fixedRecirc)
            if (i < 40) session(t, d, 0.0, 1.0, i * 1_000_000L, conditions) else session(t, d, stuck, heater, i * 1_000_000L, conditions)
            t.decline("r")?.let { return i to it.status }
        }
        return null to null
    }

    @Test fun heatingRulesOverTwoWinters() {
        val n = 400
        // Stuck shares chosen by where they hold the coolant at -7 °C and 50 km/h (see calibrate); all are OBD thermostat faults.
        val faults = listOf("정상" to (0.0 to 1.0), "서모스탯 고착 · 냉각수 68°C" to (.08 to 1.0), "서모스탯 고착 · 냉각수 56°C" to (.10 to 1.0),
            "서모스탯 고착 · 냉각수 40°C" to (.15 to 1.0), "히터 능력 50%" to (0.0 to .5), "히터 능력 25%" to (0.0 to .25), "히터 작동 안 함" to (0.0 to 0.0))
        val report = StringBuilder("측정 시간 | 상태 | 경보 | 그중 도래 | 감지까지 중앙 회차\n")
        val results = mutableMapOf<Pair<Double, String>, Pair<Double, Double>>()
        for (probe in listOf(5.0, 8.0, 15.0)) for ((name, f) in faults) {
            val r = Random(41)
            val runs = List(n) { season(r, probe, f.first, f.second) }
            val hits = runs.filter { it.first != null }
            val delay = hits.mapNotNull { it.first?.minus(40) }.filter { it >= 0 }.sorted()
            val rate = hits.size.toDouble() / n; val due = hits.count { it.second == DueStatus.DUE }.toDouble() / n
            results[probe to name] = rate to due
            report.append(String.format("%.0f분 | %s | %.1f%% | %.1f%% | %d\n", probe, name, rate * 100, due * 100, delay.getOrElse(delay.size / 2) { -1 }))
        }
        println(report)
        val probe = ClimateTracker.HEAT_PROBE_MINUTES
        assertTrue(results.getValue(probe to "정상").first < 0.05)
        assertTrue(results.getValue(probe to "정상").second < 0.005)
        assertTrue(results.getValue(probe to "히터 작동 안 함").second > 0.9)   // a heater that gives nothing is called due
        assertTrue(results.getValue(probe to "히터 능력 50%").first > 0.5)
    }

    /**
     * Real stops are mixed: errands of 3-6 hours leave a third of the coolant's heat (0.34-0.42 after 3 hours, a 3.1 h
     * time constant), overnight stops leave almost none, and the cabin air cools within half an hour. A warm start heats
     * faster, so a reference full of overnight starts and later errands could hide a weak heater; one full of errands
     * could raise alarms on cold mornings. Runs both ways: parked hours stored (warm-ups compared within 3-6, 6-12 and
     * 12+ hour bands) and not.
     */
    private fun soakDay(r: Random, habit: Double, fixedFan: Double, fixedRecirc: Boolean, errandsLater: Boolean, i: Int): Pair<Day, Double> {
        val base = day(r, habit, fixedFan, fixedRecirc)
        val errand = if (errandsLater) i >= 40 && r.nextDouble() < .7 else r.nextDouble() < .4
        val hours = if (errand) 3 + 3 * r.nextDouble() else if (r.nextDouble() < .3) 6 + 6 * r.nextDouble() else 12 + 4 * r.nextDouble()
        val engine = base.outside + (88 - base.outside) * exp(-hours / 3.1)
        return base.copy(engineStart = engine) to hours
    }

    private fun soakSeason(r: Random, stuck: Double, heater: Double, banded: Boolean, errandsLater: Boolean, minHours: Double = 3.0): Pair<Int?, DueStatus?> {
        val t = ClimateTracker(heating = true, heatProbe = ClimateTracker.HEAT_PROBE_MINUTES, soakBands = banded); t.markBaseline("r")
        val habit = r.nextDouble(); val fixedFan = (4 + r.nextInt(5)) / 8.0; val fixedRecirc = r.nextDouble() < .3
        for (i in 0 until 160) {
            val (d, hours) = soakDay(r, habit, fixedFan, fixedRecirc, errandsLater, i)
            if (hours < minHours) continue
            val steps = ((t.probeMinutes + 2) * 6).toInt() + 2
            val f = if (i < 40) 0.0 to 1.0 else stuck to heater
            trace(d, f.first, f.second, steps).forEachIndexed { k, c ->
                t.update(true, c, d.set, d.outside, i * 1_000_000L + k * 10_000L, d.fan, d.recirc, d.speed, parkedHours = hours)
            }
            t.update(false, null, d.set, d.outside, i * 1_000_000L + (steps + 1) * 10_000L)
            t.decline("r")?.let { return i to it.status }
        }
        return null to null
    }

    @Test fun parkedHoursBands() {
        val n = 400
        val report = StringBuilder("방식 | 나중에 짧은 정차가 많아짐 | 정상 경보 | 히터 50% 경보 | 히터 작동 안 함 도래\n")
        val rows = mutableMapOf<Pair<String, Boolean>, List<Double>>()
        val modes = listOf(Triple("3시간 이상, 구분 없음", false, 3.0), Triple("3~6·6~12·12시간+ 구간끼리", true, 3.0), Triple("6시간 이상만", false, 6.0))
        for (later in listOf(false, true)) for ((name, banded, min) in modes) {
            val r = Random(77)
            val rate = { heater: Double, due: Boolean -> List(n) { soakSeason(r, 0.0, heater, banded, later, min) }
                .count { if (due) it.second == DueStatus.DUE else it.first != null } * 1.0 / n }
            val row = listOf(rate(1.0, false), rate(.5, false), rate(0.0, true))
            rows[name to later] = row
            report.append(String.format("%s | %s | %.1f%% | %.1f%% | %.1f%%\n", name, if (later) "예" else "아니오", row[0] * 100, row[1] * 100, row[2] * 100))
        }
        println(report)
        // The app keeps the first: bands thin the reference and lose more than mixing costs.
        listOf(false, true).forEach { later ->
            val kept = rows.getValue("3시간 이상, 구분 없음" to later)
            assertTrue(kept[0] < 0.05 && kept[2] > 0.9)
            assertTrue(kept[2] >= rows.getValue("3~6·6~12·12시간+ 구간끼리" to later)[2])
        }
    }
}
