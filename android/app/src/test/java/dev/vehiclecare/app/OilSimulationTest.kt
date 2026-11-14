package dev.vehiclecare.app

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The oil model against every published number it was not fitted to, and the fitted ones checked back. Each scenario
 * repeats one kind of trip with a ten-hour park between, the way the literature's fleets were run.
 * Sources: materials/04_research/oil/model-inputs.md. Prints a table so the docs can quote it.
 */
class OilSimulationTest {
    private fun factor(kph: Double, km: Double, outside: Double, hybrid: Boolean = false, idle: Double = 1.0): Double {
        val e = OilModel.simulate(300, kph, km, idle, outside, 600.0, hybrid)
        return e.penaltyUnits * OilModel.SCALE / e.km
    }
    /** Fuel in the oil after [interval] km of the same trip. */
    private fun dilution(kph: Double, km: Double, outside: Double, interval: Double = 11_000.0, hybrid: Boolean = false) =
        OilModel.simulate((interval / km).toInt(), kph, km, 1.0, outside, 600.0, hybrid).dilution

    @Test fun hyundaisSevereIntervalSetsTheScale() {
        val f = factor(30.0, 5.0, 20.0)
        println("5 km at 20 °C: x%.2f".format(f))
        assertTrue(f in 1.99..2.01)
    }

    @Test fun coldShortTripsLandNearGmsMeasuredOilLife() {
        // GM (US 4,742,476 Fig. 2, SAE 870403): cold short trips averaging 4 km gave 2,400-3,680 km of oil life.
        val life = 10_000 / factor(30.0, 4.0, -10.0)
        println("4 km at -10 °C on a 10,000 km engine: %.0f km (GM 2,400-3,680)".format(life))
        assertTrue(life in 3_000.0..4_200.0)
    }

    @Test fun longerAndWarmerTripsWeighLess() {
        val rows = listOf(Triple(80.0, 60.0, 20.0), Triple(40.0, 15.0, 20.0), Triple(30.0, 8.0, 20.0), Triple(30.0, 5.0, 20.0),
            Triple(35.0, 12.0, 5.0), Triple(30.0, 4.0, 0.0), Triple(30.0, 4.0, -10.0))
        val f = rows.map { (v, km, t) -> factor(v, km, t).also { println("%4.0f km/h %4.0f km at %3.0f °C: x%.2f, turbo life %,.0f km".format(v, km, t, it, 10_000 / it)) } }
        assertTrue(f[0] < 1.0)                         // long warm trips: the app never extends the manual, but they weigh less
        assertTrue(f[1] in 1.0..1.4)                   // a 15 km commute is near normal
        assertTrue(f[2] > f[1] && f[3] > f[2])         // shorter is heavier
        assertTrue(f[5] > f[3] && f[6] > f[5])         // colder is heavier
    }

    @Test fun dilutionMatchesTheFieldData() {
        val longWarm = dilution(80.0, 60.0, 20.0)
        val summer = dilution(30.0, 5.0, 25.0)
        val winter = dilution(30.0, 4.0, -5.0)
        println("fuel in oil after 11,000 km: long warm %.2f %% (Shayler 1-2), summer short %.2f %% (Bergstra 3-5), winter short %.2f %% (8-11)".format(longWarm, summer, winter))
        assertTrue(longWarm in 1.0..2.2)
        assertTrue(summer in 2.5..5.0)
        assertTrue(winter in 7.5..11.5)
    }

    @Test fun oneTenMinuteTripAddsWhatZachMeasured() {
        // Zach 2022 (1.33 L T-GDI): 0.05-0.13 % per cold FTP start; under 45 °C no net removal.
        val e = OilModel.simulate(1, 30.0, 4.5, 1.0, 20.0, 600.0)
        println("one 10-minute trip: +%.3f %%, oil peak %.0f °C".format(e.dilution, e.maxC))
        assertTrue(e.dilution in 0.04..0.15)
    }

    @Test fun hybridsCollectMoreFuelInWinter() {
        // Hyundai TSB 23-EE-007H: HEV winter dilution; Infineum: HEV oil runs 20-40 °C cooler over WLTC.
        val ice = dilution(30.0, 5.0, -5.0); val hev = dilution(30.0, 5.0, -5.0, hybrid = true)
        println("winter short trips: engine %.2f %%, hybrid %.2f %%".format(ice, hev))
        assertTrue(hev > ice * 1.2)
    }

    @Test fun theWarmUpRangeMovesTheEstimateLessThanTheManualsOwnHalving() {
        // The slow end of the measured warm-up (13 min instead of 9) is the upper estimate the app shows.
        val e = OilModel.simulate(300, 30.0, 5.0, 1.0, 5.0, 600.0)
        val spread = e.penaltySlow / e.penaltyUnits
        println("upper/nominal stress at 5 km, 5 °C: %.2f".format(spread))
        assertTrue(spread in 1.0..1.3)
    }
}
