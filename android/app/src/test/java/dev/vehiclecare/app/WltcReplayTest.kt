package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WLTC class 3b speed trace (UN GTR 15; file from the European Commission JRC's wltp project, V_class3b.txt) replayed
 * through the app's own brake and tire models at the 10 Hz the car delivers. The Tucson's top speed puts it in class 3b.
 * The trace has no brake pedal: the pedal is taken as pressed whenever the car slows faster than drag and rolling
 * resistance explain (an upper bound on friction braking), or faster than that plus [ENGINE_BRAKING] (a lower bound).
 */
class WltcReplayTest {
    private val kph: List<Double> = javaClass.getResource("/wltc_class3b.txt")!!.readText().trim().split(Regex("\\s+")).map { it.toDouble() }
    /** Coasting in gear; a typical fuel-cut deceleration for a passenger car, taken as an assumption. */
    private val ENGINE_BRAKING = 0.4
    private val phases = listOf("Low" to (0 to 589), "Medium" to (589 to 1022), "High" to (1022 to 1477), "Extra High" to (1477 to 1800))

    private fun speedAt(t: Double): Double {
        val i = t.toInt().coerceAtMost(kph.size - 2)
        return (kph[i] + (kph[i + 1] - kph[i]) * (t - i)) / 3.6
    }

    private val car = BrakeWearModel.params()
    private fun roadLoad(v: Double) = car.roadLoadN(v) / car.massKg

    /** Brake work, distance and holds over [from, to) s, with the pedal pressed below -(road load + [extra]). */
    private fun brake(from: Int, to: Int, extra: Double): BrakeWork {
        val b = BrakeWork()
        var pressed = false
        for (k in from * 10..to * 10) {
            val t = k / 10.0
            val v = speedAt(t)
            val a = (speedAt(minOf(t + 0.1, 1800.0)) - v) / 0.1
            val press = v > 0.1 && a < -(roadLoad(v) + extra)
            val ms = (k * 100).toLong()
            if (press != pressed) { b.pedal(press, ms); pressed = press }
            b.speed(v, ms)
        }
        return b
    }

    @Test fun theTraceIsTheRegulationsClass3b() {
        assertEquals(1801, kph.size)
        assertEquals(131.3, kph.max(), 0.0)
        assertEquals(23.266, kph.zipWithNext { a, b -> (a + b) / 2 }.sum() / 3600, 0.001)
    }

    @Test fun replay() {
        val report = StringBuilder("구간 | 거리 km | 제동 kJ/km 상한 | 하한(엔진브레이크 ${ENGINE_BRAKING}) | 속도 유지 제동 | σ앞뒤 m/s²\n")
        val all = mutableListOf<Triple<Double, Double, Double>>()
        var ms = 0.0
        for ((name, range) in phases + listOf("전체" to (0 to 1800))) {
            val (a, b) = range
            val hi = brake(a, b, 0.0); val lo = brake(a, b, ENGINE_BRAKING)
            val load = TireLoad()
            for (k in a * 10..b * 10) load.speed(speedAt(k / 10.0), (k * 100).toLong())
            val km = hi.distanceM / 1000
            val sd = load.longitudinal.sd!!
            ms = load.longitudinal.meanSquare!!
            report.append(String.format("%s | %.2f | %.0f | %.0f | %d | %.2f\n", name, km, hi.workJ / hi.distanceM, lo.workJ / lo.distanceM, hi.holds.count + lo.holds.count, sd))
            all += Triple(hi.workJ / hi.distanceM, lo.workJ / lo.distanceM, sd)
            assertEquals(0, hi.holds.count + lo.holds.count)   // a level cycle never holds speed with the pedal down
        }
        println(report)
        val (hi, lo, sd) = all.last()
        assertTrue(lo <= hi)
        // The regulation's own cycle should sit near its normal band for overall longitudinal spread (0.35-0.55).
        assertTrue(sd in 0.3..0.7)
        // The brake reference (WLTP-Brake 79 J/(kg·km) for this car) brings a WLTC driver forward only if the cycle brakes harder.
        val ref = CompositeJudge.brakeReferenceKjPerKm()
        println(String.format("WLTC 운전자의 브레이크 환산 비율: %.2f~%.2f (1이면 거리 주기 그대로)", lo / ref, hi / ref))
        val stats = TireStats(Moments(23_266.0 * 3, 0.0, 23_266.0 * 3 * ms), Moments(), true)
        println(String.format("WLTC 앞뒤 RMS %.2f m/s² (UN 규정 117 보통 주행 %.2f), 타이어 마모 가중 %.2f", Math.sqrt(ms), TireModel.NORMAL_LONG, stats.factor))
        // A driver who drives like the regulation's own cycle stays within a tenth of normal on longitudinal data alone.
        assertTrue(stats.factor!! in 0.9..1.1)
    }
}
