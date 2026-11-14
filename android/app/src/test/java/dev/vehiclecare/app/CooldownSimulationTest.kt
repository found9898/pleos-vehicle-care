package dev.vehiclecare.app

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.random.Random

/**
 * Cabin pull-down simulation used to choose the A/C decline rules. Two-node cabin (air and interior) on the parameters
 * Brusey et al. validated in a climate tunnel (arXiv:1704.07899): air 24 kJ/K, interior 70.65 kJ/K, shell 23 W/K. The
 * air-to-interior coupling and the 5 kW capacity are calibrated to ORNL's pull-down times (see calibrate), and capacity
 * is scaled by what is left. Solar load, soak temperature and a
 * session-to-session spread on capacity and losses stand for what the app cannot see; ORNL measured 1.9× spread in
 * pull-down time under similar conditions (ORNL/TM-2016/275).
 */
class CooldownSimulationTest {
    data class Day(val outside: Double, val startCabin: Double, val set: Double, val solar: Double, val spread: Double, val recirc: Boolean, val fan: Double = 1.0,
                   val speed: Double = 50.0)

    var spreadSigma = 0.18
    private fun day(r: Random): Day {
        val outside = 22 + 13 * r.nextDouble()
        return Day(outside, outside + 4 + 18 * r.nextDouble(), 21 + 3 * r.nextDouble(), 600 * r.nextDouble(),
            exp(spreadSigma * gauss(r)), r.nextDouble() < .6)
    }
    private fun gauss(r: Random): Double { var s = 0.0; repeat(12) { s += r.nextDouble() }; return s - 6 }

    /** Feeds one pull-down through the production tracker, sampled every 10 s at 0.5 °C resolution. */
    var reportConditions = false
    var reportSpeed = false
    fun session(t: ClimateTracker, d: Day, capacity: Double, startMs: Long) {
        trace(d, capacity).forEachIndexed { k, c -> t.update(true, c, d.set, d.outside, startMs + k * 10_000L,
            d.fan.takeIf { reportConditions }, d.recirc.takeIf { reportConditions }, d.speed.takeIf { reportSpeed }) }
        t.update(false, null, d.set, d.outside, startMs + 49 * 10_000L)
    }

    /** Cabin air temperature every 10 s for 8 minutes. */
    var coupling = 800.0
    var qFull = 5000.0
    /** Capacity against blower share; 0 leaves the blower out, as the earlier runs did. */
    var fanExponent = 0.0
    fun trace(d: Day, capacity: Double, steps: Int = 49): DoubleArray {
        var air = d.startCabin; var inner = d.startCabin + 3
        val qMax = qFull * capacity * d.spread * Math.pow(d.fan, fanExponent) * speedFactor(d.speed)
        val vent = if (d.recirc) 15.0 else 60.0
        return DoubleArray(steps) { _ ->
            val now = air
            repeat(10) {
                val q = if (air > d.set + 1) qMax * (0.75 + 0.25 * minOf(1.0, (air - 10) / 30)) else 0.0
                val dAir = (23 * (d.outside - air) / d.spread + vent * (d.outside - air) + coupling * (inner - air) + d.solar * 0.3 - q) / 24_000
                val dInner = (coupling * (air - inner) + d.solar * 0.7) / 70_650
                air += dAir; inner += dInner
            }
            Math.round(now * 2) / 2.0
        }
    }

    /**
     * Capacity against vehicle speed, from KATECH 2011 (40 to 24 °C at 20 °C outside: 9.2 min parked, 6.0 at 50 km/h, 9.6
     * at 100 km/h): parked, the condenser gets no ram air.
     */
    fun speedFactor(kph: Double) = when { kph < 5 -> 0.65; kph < 40 -> 0.9; kph < 80 -> 1.0; else -> 0.95 }

    /** ORNL highway runs: 128-134 °F soak to 85 °F mostly in 8-12 minutes at maximum A/C and recirculation. */
    @Test fun calibrate() {
        // Brusey's 81 W/K air-to-interior coupling cooled this soak in under 3 minutes; 800 W/K with 5 kW lands inside ORNL's range.
        val ornl = Day(35.0, 54.0, 18.0, 600.0, 1.0, true)
        val minutes = trace(ornl, 1.0, 6 * 30).indexOfFirst { it <= 29.4 } / 6.0
        println(String.format("CAL h %.0f W/K q %.0f W -> 85°F at %.1f min", coupling, qFull, minutes))
        assertTrue(minutes in 8.0..12.0)
    }

    /** Two summers of about 80 pull-downs each through the production tracker; capacity steps to [after] at session 40. */
    private fun season(r: Random, after: Double): Pair<Int?, DueStatus?> {
        val t = ClimateTracker(); t.markBaseline("r")
        for (i in 0 until 160) {
            session(t, day(r), if (i < 40) 1.0 else after, i * 1_000_000L)
            t.decline("r")?.let { return i to it.status }
        }
        return null to null
    }

    @Test fun declineRulesOverTwoSummers() {
        val r = Random(20261030)
        val n = 1000
        val report = StringBuilder("남은 냉방 능력 | 경보 비율(전체) | 그중 도래 | 감지까지 중앙 회차\n")
        val rows = listOf(1.0, 0.9, 0.8, 0.7, 0.5, 0.25, 0.0).associateWith { cap ->
            val runs = List(n) { season(r, cap) }
            val hits = runs.filter { it.first != null }
            val due = hits.count { it.second == DueStatus.DUE }
            val delay = hits.mapNotNull { it.first?.minus(40) }.filter { it >= 0 }.sorted()
            report.append(String.format("%.0f%% | %.1f%% | %.1f%% | %d\n", cap * 100, hits.size * 100.0 / n, due * 100.0 / n, delay.getOrElse(delay.size / 2) { -1 }))
            Triple(hits.size.toDouble() / n, due.toDouble() / n, delay)
        }
        println(report)
        assertTrue(rows.getValue(1.0).first < 0.05)          // an unchanged system is rarely called down in two summers
        assertTrue(rows.getValue(1.0).second < 0.005)        // and almost never called due
        assertTrue(rows.getValue(0.0).second > 0.95)         // a lost charge is called due
        assertTrue(rows.getValue(0.5).first > 0.9)           // half the capacity is caught
    }

    /** Twice the unexplained spread: the rules must still stay quiet on an unchanged system. */
    @Test fun widerSpread() {
        spreadSigma = 0.36
        val r = Random(11)
        val n = 1000
        for (cap in listOf(1.0, 0.5, 0.0)) {
            val runs = List(n) { season(r, cap) }
            println(String.format("WIDE capacity %.0f%% · alarm %.1f%% · due %.1f%%", cap * 100,
                runs.count { it.first != null } * 100.0 / n, runs.count { it.second == DueStatus.DUE } * 100.0 / n))
            if (cap == 1.0) assertTrue(runs.count { it.first != null } < n * 0.05)
            if (cap == 0.0) assertTrue(runs.count { it.second == DueStatus.DUE } > n * 0.95)
        }
    }

    /**
     * Blower habits. Published effects of the evaporator air flow on capacity disagree: a van test rig rose from 1.46 to 4.23 kW
     * between 3 and 10 m/s (exponent near 0.9), a bench study called the effect marginal; both ends are run. Drivers: 40 %
     * automatic (blower near full while pulling down), 30 % with one fixed step, 30 % picking any step from 3 to 8 of 8 each
     * time. [drift] moves manual drivers two steps lower after the reference, a habit change with no loss of capacity.
     */
    private fun habitSeason(r: Random, after: Double, drift: Boolean): Pair<Int?, DueStatus?> {
        val t = ClimateTracker(); t.markBaseline("r")
        val habit = r.nextDouble()
        val fixed = (4 + r.nextInt(5)) / 8.0
        val fixedRecirc = r.nextDouble() < .6
        for (i in 0 until 160) {
            val base = day(r)
            var fan = when {
                habit < .4 -> (1.0 - .125 * r.nextInt(2))
                habit < .7 -> fixed
                else -> (3 + r.nextInt(6)) / 8.0
            }
            if (drift && habit >= .4 && i >= 40) fan = maxOf(.25, fan - .25)
            val recirc = if (habit >= .4 && habit < .7) fixedRecirc else base.recirc
            session(t, base.copy(fan = fan, recirc = recirc), if (i < 40) 1.0 else after, i * 1_000_000L)
            t.decline("r")?.let { return i to it.status }
        }
        return null to null
    }

    @Test fun blowerHabits() {
        val n = 1000
        val report = StringBuilder("지수 | 조건 저장 | 무변화 경보 | 습관 변화 경보 | 70% 감지 | 50% 감지 | 0% 도래\n")
        val out = mutableMapOf<Pair<Double, Boolean>, List<Double>>()
        for (exp in listOf(0.3, 0.9)) for (cond in listOf(false, true)) {
            fanExponent = exp; reportConditions = cond
            val r = Random(7)
            val rate = { after: Double, drift: Boolean, due: Boolean -> List(n) { habitSeason(r, after, drift) }.count { if (due) it.second == DueStatus.DUE else it.first != null } * 1.0 / n }
            val row = listOf(rate(1.0, false, false), rate(1.0, true, false), rate(.7, false, false), rate(.5, false, false), rate(0.0, false, true))
            out[exp to cond] = row
            report.append(String.format("%.1f | %s | %.1f%% | %.1f%% | %.1f%% | %.1f%% | %.1f%%\n", exp, if (cond) "예" else "아니오", row[0] * 100, row[1] * 100, row[2] * 100, row[3] * 100, row[4] * 100))
        }
        println(report)
        fanExponent = 0.0; reportConditions = false
        for (exp in listOf(0.3, 0.9)) {
            val with = out.getValue(exp to true)
            assertTrue(with[0] < 0.05)       // an unchanged system stays quiet
            assertTrue(with[1] < 0.05)       // and so does a driver who turned the blower down
            assertTrue(with[4] > 0.95)       // a lost charge is still called due
        }
    }

    /** Commuting speeds: a third of pull-downs start parked or creeping, most in town, some on the motorway. */
    private fun speedOf(r: Random) = r.nextDouble().let { u -> when { u < .3 -> 0.0; u < .7 -> 30.0; u < .9 -> 60.0; else -> 100.0 } }

    @Test fun speedMustBeStoredWithEachPullDown() {
        val n = 1000
        val report = StringBuilder("차속 저장 | 무변화 경보 | 70% 감지 | 50% 감지 | 0% 도래\n")
        val rows = listOf(false, true).associateWith { stored ->
            reportSpeed = stored
            val r = Random(23)
            val rate = { cap: Double, due: Boolean -> List(n) {
                val t = ClimateTracker(); t.markBaseline("r")
                var hit: Pair<Int?, DueStatus?> = null to null
                for (i in 0 until 160) {
                    session(t, day(r).copy(speed = speedOf(r)), if (i < 40) 1.0 else cap, i * 1_000_000L)
                    t.decline("r")?.let { hit = i to it.status }
                    if (hit.first != null) break
                }
                hit
            }.count { if (due) it.second == DueStatus.DUE else it.first != null } * 1.0 / n }
            listOf(rate(1.0, false), rate(.7, false), rate(.5, false), rate(0.0, true)).also {
                report.append(String.format("%s | %.1f%% | %.1f%% | %.1f%% | %.1f%%\n", if (stored) "예" else "아니오", it[0] * 100, it[1] * 100, it[2] * 100, it[3] * 100))
            }
        }
        reportSpeed = false
        println(report)
        assertTrue(rows.getValue(true)[0] < 0.05)
        assertTrue(rows.getValue(true)[0] <= rows.getValue(false)[0])
        assertTrue(rows.getValue(true)[3] > 0.95)
    }

    /**
     * Capacity against the charge left: flat while the receiver holds spare refrigerant, then falling to nothing where
     * the low-pressure switch stops the compressor (Doowon 1998: -14 % charge cost 12 %, -29 % cost 21 %; CARB and EU
     * reports saw little loss until about half; cut-out at 100-200 g).
     */
    private fun capacity(left: Double, knee: Double = 0.7, cut: Double = 150.0 / 550) =
        when { left >= knee -> 1.0; left <= cut -> 0.0; else -> (left - cut) / (knee - cut) }

    /**
     * Five summers of a fleet without a recharge. Each car leaks at its own rate (lognormal, median 4.25 %/year, σ 0.65,
     * the EU spread), 10.5 % in the first year; 2.2 % of cars a year are opened by a crash or stone, 62 % of those losing
     * everything. Slow leakers should rarely be called down in five years; the opened ones should be found.
     */
    @Test fun aFleetOfSlowLeaksAndCrashesOverFiveYears() {
        val r = Random(55)
        val n = 1500
        var slow = 0; var slowAlarm = 0; var slowLow = 0; var intact = 0; var intactAlarm = 0
        var crash = 0; var crashCaught = 0; var lost = 0; var lostDue = 0
        repeat(n) {
            val lambda = 0.0425 * exp(0.65 * gauss(r))
            val crashAt = (0 until 5).firstOrNull { r.nextDouble() < 0.022 }?.let { y -> y + r.nextDouble() }
            val crashLeft = if (r.nextDouble() < 0.62) 0.0 else 0.01 + 0.59 * r.nextDouble()
            val t = ClimateTracker(); t.markBaseline("r")
            var alarm: DueStatus? = null; var minCap = 1.0
            for (i in 0 until 400) {
                val years = i / 80.0 + r.nextDouble() / 80
                var left = if (years <= 1) Math.pow(0.895, years) else 0.895 * exp(-lambda * (years - 1))
                if (crashAt != null && years >= crashAt) left *= crashLeft
                val cap = capacity(left); minCap = minOf(minCap, cap)
                session(t, day(r), cap, i * 1_000_000L)
                t.decline("r")?.let { alarm = it.status }
                if (alarm != null) break
            }
            if (crashAt == null) { slow++; if (alarm != null) slowAlarm++; if (minCap < 0.5) slowLow++
                if (minCap >= 0.9) { intact++; if (alarm != null) intactAlarm++ } }
            else { crash++; if (alarm != null) crashCaught++; if (crashLeft == 0.0) { lost++; if (alarm == DueStatus.DUE) lostDue++ } }
        }
        println(String.format("5년 무충전: 서서히 새는 차 %d대 중 경보 %.1f%%(능력 50%% 아래 %.1f%%), 그중 능력 90%% 이상 유지한 %d대의 경보 %.1f%%, 사고 손실 %d대 중 경보 %.0f%%, 전손 %d대 중 도래 %.0f%%",
            slow, slowAlarm * 100.0 / slow, slowLow * 100.0 / slow, intact, intactAlarm * 100.0 / intact, crash, crashCaught * 100.0 / maxOf(1, crash), lost, lostDue * 100.0 / maxOf(1, lost)))
        assertTrue(slowAlarm.toDouble() / slow < 0.08)
        assertTrue(intactAlarm.toDouble() / intact < 0.05)
        assertTrue(lost == 0 || lostDue.toDouble() / lost > 0.9)
    }
}
