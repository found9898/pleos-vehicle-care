package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The tire model against published figures it was not fitted to, then a fleet of tires that wear faster or slower than
 * the model's centre to count missed and false alerts. Sources: materials/04_research/tire/model-inputs.md.
 */
class TireSimulationTest {
    private val tucson = TireParams.of(VehicleCatalog.TUCSON_GASOLINE)
    private val today = LocalDate.of(2026, 11, 2)

    @Test fun theRegulationsNormalDrivingWeighsOne() {
        val d = 1e6
        val stats = TireStats(Moments(d, 0.0, d * 0.45 * 0.45), Moments(d, 0.0, d * 0.93 * 0.93), true)
        assertEquals(1.0, stats.factor!!, 1e-9)
        // City driving in the convoy ran about twice the motorway's lateral spread; the square law makes that 1.9-2.6 times.
        val city = TireStats(Moments(d, 0.0, d * 0.6 * 0.6), Moments(d, 0.0, d * 1.3 * 1.3), true).factor!!
        val motorway = TireStats(Moments(d, 0.0, d * 0.25 * 0.25), Moments(d, 0.0, d * 0.6 * 0.6), true).factor!!
        println("마모 가중: 도심 %.2f, 고속도로 %.2f, 비 %.1f".format(city, motorway, city / motorway))
        assertTrue(city / motorway in 3.0..6.0)
    }

    /** Steady cornering at [kph] with the wheel turned [deg]; returns the RMS lateral acceleration the model records. */
    private fun steady(kph: Double, deg: Double): Double {
        CarModel.current = VehicleCatalog.TUCSON_GASOLINE
        val load = TireLoad()
        var ms = 0L
        repeat(301) { load.steering(deg, ms); load.speed(kph / 3.6, ms); ms += 100 }
        return Math.sqrt(load.lateral.meanSquare!!)
    }

    @Test fun steeringGivesTheBicycleModelsLateralAcceleration() {
        // a = v²δ / (L + K v²): 30° of wheel at 50 km/h is 1.95 m/s² on a Tucson (13.7:1, 2.755 m, K 3°/g).
        assertEquals(1.95, steady(50.0, 30.0), 0.02)
        // A parking turn at 5 km/h is not cornering load.
        assertEquals(0.0, steady(5.0, 300.0), 0.0)
    }

    @Test fun rearWheelTicksMisreadOneMillimetreOfRadiusAsCornering() {
        // The reason steering comes first: a 1 mm smaller right rear (0.36 m) reads as 1.3 m/s² on a straight motorway.
        CarModel.current = VehicleCatalog.TUCSON_GASOLINE
        val load = TireLoad()
        val v = 100 / 3.6
        var ms = 0L; var left = 0.0; var right = 0.0
        repeat(301) { load.wheels(left, right, false); load.speed(v, ms); left += v * 0.1; right += v * 0.1 * (1 + 0.001 / 0.36); ms += 100 }
        val a = Math.sqrt(load.lateral.meanSquare!!)
        println("반경 1 mm 차이로 생기는 가짜 횡가속 %.2f m/s²".format(a))
        assertEquals(1.32, a, 0.05)
    }

    private fun record(id: String, km: Long, action: ServiceAction) = ServiceRecord(id, Part.TIRE, today.minusDays(1_000 - km / 100), km, action)

    /** Centre and upper tread after [km] on a new set, rotated every [every] km (0 = never recorded). */
    private fun tread(km: Long, every: Long, p: TireParams = tucson): TireTread {
        val rotations = if (every == 0L) emptyList() else (1..(km / every)).map { record("r$it", it * every, ServiceAction.ROTATE) }.filter { it.odometerKm!! < km }
        return TireTread.of(listOf(record("s", 0, ServiceAction.REPLACE)) + rotations, Mileage(km, today, "vehicle"), TireLoad(), p)!!
    }

    /** First distance at which [hit] holds, in 500 km steps. */
    private fun firstKm(hit: (Long) -> Boolean) = (500L..200_000L step 500).first(hit)

    @Test fun tireLifeLandsBetweenEuropeanTestsAndUsOeWarranties() {
        // To the 1.6 mm legal line at the regulation's normal driving. Rotated: US OE UTQG 500-540 is 80,000-87,000 km,
        // ADAC's European summer tests 40,000 ± 10,000 km on smaller, more heavily loaded tires.
        val rotated = firstKm { tread(it, 10_000).central <= 1.6 }
        val rotatedHigh = firstKm { tread(it, 10_000).lowest <= 1.6 }
        println("투싼 1.6 mm까지: 위치 교환 시 중앙 %,d km, 상단 %,d km".format(rotated, rotatedHigh))
        assertTrue(rotated in 65_000L..90_000L)
        assertTrue(rotatedHigh in 45_000L..65_000L)
    }

    @Test fun rearWheelDriveWearsTheRearFirst() {
        val ioniq = TireParams.of(VehicleCatalog.MODEL_NE_2026_ELECTRIC_EV)
        val t = tread(9_000, 0, ioniq)
        assertTrue(t.rear < t.front)
        val tucsonT = tread(9_000, 0)
        assertTrue(tucsonT.front < tucsonT.rear)
    }

    /**
     * A fleet whose tires wear 0.61 to 1.36 times the model's centre (the convoy's spread, 0.9-2.0 against 1.47 mm per
     * 10,000 km on the front). Each owner checks the tread whenever the app asks and replaces at 3 mm, as the makers
     * advise; otherwise the check is recorded and narrows the estimate. A miss is a tire reaching 2.4 mm while the app
     * is quiet; "early" is how far before 3 mm the first ask came; "asks" counts looks before the set was replaced. The
     * slowest tires get the most asks, each about 6,000 km apart, still far fewer than the manual's monthly check.
     */
    @Test fun aFleetOfFasterAndSlowerTiresCountsMissesAndAsks() {
        for (every in listOf(10_000L, 0L)) {
            var missed = 0; var asksSum = 0; var maxAsks = 0; var earlySum = 0.0
            val fleet = (0..75).map { 0.61 + it * 0.01 }
            for (u in fleet) {
                val rotations = if (every == 0L) emptyList() else (1..20).map { record("r$it", it * every, ServiceAction.ROTATE) }
                // The truth: each axle at u times its rate; a set never rotated keeps the front rate on the front.
                fun truth(km: Long): Double = if (every == 0L) tucson.newMm - u * tucson.frontPer10k * km / 10_000
                    else tucson.newMm - u * (tucson.newMm - tread(km, every).central)
                val checks = mutableListOf<ServiceRecord>()
                var asks = 0; var first: Long? = null
                for (km in 500L..200_000L step 500) {
                    val t = TireTread.of(listOf(record("s", 0, ServiceAction.REPLACE)) + rotations.filter { it.odometerKm!! < km } + checks,
                        Mileage(km, today, "vehicle"), TireLoad(), tucson)!!
                    val ask = t.central <= TireModel.MOTORWAY_TREAD_MM || t.lowest <= t.askAt
                    if (!ask && truth(km) <= TireModel.MOTORWAY_TREAD_MM) { missed++; break }
                    if (ask) {
                        asks++; if (first == null) first = km
                        if (truth(km) <= TireModel.TREAD_SOON_MM) break
                        checks += record("c$km", km, ServiceAction.INSPECT)
                    }
                }
                asksSum += asks; maxAsks = maxOf(maxAsks, asks)
                earlySum += ((firstKm { truth(it) <= TireModel.TREAD_SOON_MM }) - (first ?: 0L)).toDouble()
            }
            println("위치 교환 %s: 2.4 mm까지 조용함 %d/%d, 교체까지 확인 요청 평균 %.1f회(최대 %d), 첫 요청이 3 mm보다 평균 %,.0f km 앞섬".format(
                if (every == 0L) "기록 없음(실제로 안 함)" else "1만 km마다", missed, fleet.size, asksSum.toDouble() / fleet.size, maxAsks, earlySum / fleet.size))
            assertEquals(0, missed)
            assertTrue(maxAsks <= 8)
        }
    }
}
