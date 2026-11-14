package dev.vehiclecare.app

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brake model against published numbers: WLTP-Brake disc temperatures and pad life, a held mountain descent, a run of
 * hard stops, and how regeneration stretches pad life. 10 Hz samples, Tucson 1.6T unless stated.
 * Sources: materials/04_research/brake/model-inputs.md.
 */
class BrakeSimulationTest {
    private val tucson = BrakeParams.of(VehicleCatalog.TUCSON_GASOLINE)

    /** Cruises at [kph] for [cruise] s, then stops at [decel] m/s² with the pedal, then waits [wait] s; repeated [n] times. */
    private fun stops(n: Int, kph: Double, decel: Double, cruise: Double, wait: Double, p: BrakeParams = tucson): BrakeWork {
        val b = BrakeWork(); b.outsideC = 20.0
        var t = 0L
        repeat(n) {
            var v = kph / 3.6
            repeat((cruise * 10).toInt()) { t += 100; b.speed(v, t, p) }
            b.pedal(true, t, p)
            while (v > 0) { v = maxOf(0.0, v - decel * 0.1); t += 100; b.speed(v, t, p) }
            b.pedal(false, t, p)
            repeat((wait * 10).toInt()) { t += 100; b.speed(0.0, t, p) }
        }
        return b
    }

    @Test fun wltpBrakeStopsHoldTheDiscWhereItWasMeasured() {
        // WLTP-Brake: 303 stops over 192 km, 15,184 J per kg (about 83 kJ a stop on this car), 1.1 stops a minute;
        // Mamakos 2021 measured the disc at 58-66 °C on average from a 40 °C start. Same energy: stops from 35 km/h.
        val b = BrakeWork(); b.outsideC = 25.0; b.discC = 40.0; b.discSlowC = 40.0
        var t = 0L; var sum = 0.0; var n = 0; var peak = 0.0
        repeat(300) {
            var v = 35 / 3.6
            repeat(450) { t += 100; b.speed(v, t); sum += b.discC!!; n++ }
            b.pedal(true, t)
            while (v > 0) { v = maxOf(0.0, v - 1.0 * 0.1); t += 100; b.speed(v, t); sum += b.discC!!; n++; peak = maxOf(peak, b.discC!!) }
            b.pedal(false, t)
            repeat(50) { t += 100; b.speed(0.0, t); sum += b.discC!!; n++ }
        }
        println("WLTP-Brake-like stops: disc mean %.0f °C, peak %.0f °C (measured mean 58-66, peak 131-184 with harder stops); wear factor %.3f".format(sum / n, peak, b.wearJ / b.workJ))
        assertTrue(sum / n in 50.0..75.0)
        assertTrue(b.wearJ / b.workJ < 1.01)
    }

    @Test fun repeatedHardStopsHeatThePadsAndWearFaster() {
        // Ten stops from 100 km/h at 0.8 g every 30 s, a fade-test pattern.
        val b = stops(10, 100.0, 7.8, 20.0, 10.0)
        println("hard stops: disc %.0f °C nominal, %.0f °C slow-cooling; wear factor %.2f to %.2f".format(b.discC, b.discSlowC, b.wearJ / b.workJ, b.wearHighJ / b.workJ))
        assertTrue(b.discSlowC!! > 250)
        assertTrue(b.wearHighJ > b.workJ * 1.05)
    }

    @Test fun aHeldDescentAddsWearOnlyToTheUpperEstimate() {
        // 6 km down at 60 km/h holding the pedal 20 s at a time, speed kept within 0.5 m/s.
        val b = BrakeWork(); b.outsideC = 20.0
        var t = 0L; val v = 60 / 3.6
        repeat(18) {
            b.pedal(true, t)
            repeat(200) { i -> t += 100; b.speed(v - 0.002 * i, t) }
            b.pedal(false, t)
            repeat(100) { t += 100; b.speed(v, t) }
        }
        println("held descent: %d holds, nominal wear %.0f kJ, upper %.0f kJ".format(b.holds.count, b.wearJ / 1_000, b.wearHighJ / 1_000))
        assertTrue(b.holds.count >= 17)   // the first press has no speed before it
        assertTrue(b.wearHighJ > b.wearJ + 1.0e6)
    }

    @Test fun padLifeAtTypicalBrakingMatchesThePublishedRange() {
        // 79 J/(kg·km) for this car, k 0.75-1.74e-9 mm/J, 9 mm usable (11 to 2 mm).
        val perKm = BrakeWearModel.TYPICAL_J_PER_KG_KM * tucson.massKg
        val life = { k: Double -> (tucson.padNewMm - tucson.padLimitMm) / (k * perKm) }
        println("Tucson pad life at WLTP-Brake braking: %,.0f km (k mid), %,.0f-%,.0f km (k range); published 45,000-103,000".format(life(BrakeWearModel.K_MID), life(BrakeWearModel.K_HIGH), life(BrakeWearModel.K_LOW)))
        assertTrue(life(BrakeWearModel.K_MID) in 45_000.0..103_000.0)
        // About 1-2 mm in the 10,000 km inspection interval, so the interval is an inspection, not a wear limit.
        val tenK = BrakeWearModel.K_MID * perKm * 10_000
        assertTrue(tenK in 1.0..2.0)
    }

    @Test fun regenerationStretchesPadLifeByPowertrain() {
        val hybrid = BrakeParams.of(VehicleCatalog.match(VehicleIdentity("Hyundai", "TUCSON", 2026, Powertrain.HYBRID, null, null))!!)
        val ev = BrakeParams.of(VehicleCatalog.match(VehicleIdentity("Hyundai", "IONIQ 5", 2026, Powertrain.ELECTRIC, null, null))!!)
        val ice = stops(100, 50.0, 1.5, 50.0, 10.0); val hev = stops(100, 50.0, 1.5, 50.0, 10.0, hybrid); val bev = stops(100, 50.0, 1.5, 50.0, 10.0, ev)
        val perKm = { b: BrakeWork -> b.wearJ / b.distanceM }
        println("friction wear per metre: engine %.1f, hybrid %.1f, EV %.1f J/m (EV studies: pads last about 2/3 longer to several times)".format(perKm(ice), perKm(hev), perKm(bev)))
        assertTrue(perKm(hev) < perKm(ice) * 0.6)
        assertTrue(perKm(bev) < perKm(ice) * 0.3)
    }
}
