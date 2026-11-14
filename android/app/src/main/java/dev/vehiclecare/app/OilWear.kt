package dev.vehiclecare.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp

/**
 * Engine oil stress from an estimated oil temperature. The car gives a third-party app neither engine speed nor oil or
 * coolant temperature (CAR_ENGINE_DETAILED is signature|privileged; docs/vehicle-signal-access.md), so the oil
 * temperature is modelled from vehicle speed and outside air, and everything else follows the OEM oil-life monitors
 * that do have the sensors. Sources and grades: materials/04_research/oil/model-inputs.md.
 *
 * - Oil temperature: first-order lag to a target while the engine runs, as Honda's Maintenance Minder computes it
 *   (R&D Technical Review 2003, TP(n) = TP(n-1) + (TPOBJ - TP(n-1))·KTP). The time constant is 9 min in mixed driving
 *   (fits Ford 1.8 L city-loop measurements: 80 °C after 10 min at 31 °C and 14-16 min at -2 °C, SAE 2005-01-3896)
 *   and 20 min in slow traffic (TU Wien 1.33 L T-GDI bench, WLTC low phase). Parked, it cools toward the outside air
 *   with 110 min (Hyundai US 2021/0340892 A1: 90 °C to about 30 °C in 2 h at 0 °C).
 * - Stress: engine revolutions weighted by GM's oil-temperature penalty (US 4,742,476 Fig. 3, revolution trace):
 *   4 below 0 °C, 3 to 38 °C, 2 to 82 °C, 1 to 132 °C, 2 above. Revolutions come from a speed-based engine-speed
 *   proxy, so slow city driving costs more per kilometre, as it does on a revolution counter.
 * - Scale: Hyundai's own schedule. Its severe interval is half the normal one for repeated short trips (US manual:
 *   under 8 km above freezing), so repeated 5 km trips at 20 °C are fixed at exactly 2. Checked against GM's measured
 *   cold short-trip oil life (2,400-3,680 km, SAE 870403): 4 km trips at -10 °C give 3,800 km on a 10,000 km engine.
 * - Fuel dilution: two pools (light 70 %, heavy 30 %, E5 distillation). Fuel enters while the oil is under 60 °C and
 *   leaves only once it is warm; parameters were fitted so long warm driving settles at 1-2 % (Shayler, SAE
 *   2000-01-1235), repeated short trips reach about 3 % in summer and 9 % in winter by 11,000 km (Bergstra 1998),
 *   and one 10-minute trip adds 0.07 % (Zach 2022: 0.05-0.13 % per cold FTP start). 3 % is caution and 5 % a change,
 *   where 0W-20 loses about a fifth of its 40 °C viscosity (Materials 2024).
 * - Hybrids run the engine in short bursts and keep the oil cooler; the target is 80 °C instead of 100 °C (Infineum:
 *   20-40 °C lower over WLTC), which is the direction of Hyundai TSB 23-EE-007H on HEV winter dilution.
 */
object OilModel {
    /** The normal interval of the car the app counts for; stress is in kilometres of that normal use. */
    fun normalKm() = (CarModel.current.engineOil?.km ?: 10_000L).toDouble()

    // Hyundai's short-trip definition (US owner's manual, NX4 2025 and DN8 2024 scheduled maintenance).
    const val SHORT_KM_WARM = 8.0
    const val SHORT_KM_FREEZING = 16.0
    const val FREEZING_C = 0.0

    const val TAU_WARM_MIN = 9.0
    /** The slow end of the measured warm-up range (8-13 min); it gives the upper estimate. */
    const val TAU_WARM_SLOW_MIN = 13.0
    const val TAU_TRAFFIC_MIN = 20.0
    const val TAU_COOL_MIN = 110.0
    const val TARGET_C = 100.0
    const val TARGET_HYBRID_C = 80.0
    const val IDLE_RPM = 750.0

    /** Penalty units to normal-use kilometres; fixed by [calibrate] so repeated 5 km trips at 20 °C weigh exactly 2. */
    const val SCALE = 2.5008e-4

    // Fuel dilution, mass % of the oil.
    const val COLD_INFLOW = 0.008     // per minute while the oil is under 60 °C
    const val WARM_INFLOW = 0.002     // per minute once warm
    const val INFLOW_STOP_C = 60.0
    const val LIGHT_SHARE = 0.7
    const val CAUTION_PCT = 3.0
    const val CHANGE_PCT = 5.0

    enum class Kind { NORMAL, SHORT, SHORT_COLD }

    fun kind(km: Double, outsideC: Double?) = when {
        outsideC != null && outsideC <= FREEZING_C -> if (km < SHORT_KM_FREEZING) Kind.SHORT_COLD else Kind.NORMAL
        km < SHORT_KM_WARM -> Kind.SHORT
        else -> Kind.NORMAL
    }

    /** GM's revolution-based oil temperature penalty. */
    fun penalty(oilC: Double) = when {
        oilC < 0 -> 4.0
        oilC < 38 -> 3.0
        oilC < 82 -> 2.0
        oilC <= 132 -> 1.0
        else -> 2.0
    }

    /** Warm-up time constant by speed: 20 min crawling, 9 min from 40 km/h. */
    fun tauWarm(kph: Double, fast: Double = TAU_WARM_MIN) = when {
        kph >= 40 -> fast
        kph < 15 -> TAU_TRAFFIC_MIN
        else -> TAU_TRAFFIC_MIN - (kph - 15) / 25 * (TAU_TRAFFIC_MIN - fast)
    }

    /** Engine speed proxy: idle, or a road-speed line through typical 4- to 6-speed gearing (2,260 rpm at 80 km/h). */
    fun rpm(kph: Double) = if (kph < 1) IDLE_RPM else 1_300 + 12 * kph

    /** Light ends leave above 30 °C, slowly until the oil is warm (Zach: no net removal in a 10-minute trip under 45 °C). */
    fun tauLight(oilC: Double) = when { oilC < 30 -> null; oilC < 50 -> 120.0; oilC < 70 -> 40.0; oilC < 90 -> 25.0; else -> 15.0 }
    /** Heavy ends take hours; above 90 °C about 40 h keeps steady hot running at 1-2 %. */
    fun tauHeavy(oilC: Double) = when { oilC < 30 -> null; oilC < 70 -> 20 * 60.0; oilC < 90 -> 80 * 60.0; else -> 40 * 60.0 }

    /** Oil temperature at the start of a trip after [parkedMin] parked. */
    fun soaked(lastC: Double?, outsideC: Double, parkedMin: Double?) =
        if (lastC == null || parkedMin == null) outsideC else outsideC + (lastC - outsideC) * exp(-parkedMin / TAU_COOL_MIN)

    /** Penalty units per kilometre for a run of identical trips; [calibrate] and the tests use it. */
    fun simulate(trips: Int, kph: Double, tripKm: Double, idleMin: Double, outsideC: Double, parkedMin: Double, hybrid: Boolean = false): OilEngine {
        val e = OilEngine(hybrid = hybrid)
        repeat(trips) {
            e.start(outsideC, parkedMin)
            val minutes = tripKm / kph * 60 + idleMin
            var t = 0.0
            while (t < minutes) {
                val v = if (t < idleMin / 2 || t > minutes - idleMin / 2) 0.0 else kph
                e.step(v, 0.1); t += 0.1
            }
            e.stop()
        }
        return e
    }

    /** The scale that makes repeated 5 km trips at 20 °C, 30 km/h, one minute idling, weigh 2 (Hyundai severe = normal / 2). */
    fun calibrate(): Double { val e = simulate(300, 30.0, 5.0, 1.0, 20.0, 600.0); return 2.0 / (e.penaltyUnits / e.km) }
}

/**
 * The running engine: estimated oil temperature, accumulated stress and fuel in the oil. One instance lives for the
 * whole oil period; [stop] keeps the last temperature so the next trip starts from what parking left.
 */
class OilEngine(
    var oilC: Double? = null, var slowC: Double? = null, var stoppedAtMs: Long? = null,
    var light: Double = 0.0, var heavy: Double = 0.0, private val hybrid: Boolean = CarModel.current.powertrain == Powertrain.HYBRID,
) {
    var penaltyUnits = 0.0; var penaltySlow = 0.0; var km = 0.0
    var maxC = 0.0
        private set
    val dilution get() = light + heavy

    fun start(outsideC: Double, parkedMin: Double?) {
        oilC = OilModel.soaked(oilC, outsideC, parkedMin); slowC = OilModel.soaked(slowC, outsideC, parkedMin)
        penaltyUnits = 0.0; penaltySlow = 0.0; km = 0.0; maxC = oilC!!
    }

    /** One sample: [kph] held for [minutes]. */
    fun step(kph: Double, minutes: Double) {
        val target = if (hybrid) OilModel.TARGET_HYBRID_C else OilModel.TARGET_C
        val t = oilC!! + (target - oilC!!) * minutes / OilModel.tauWarm(kph)
        val s = slowC!! + (target - slowC!!) * minutes / OilModel.tauWarm(kph, OilModel.TAU_WARM_SLOW_MIN)
        oilC = t; slowC = s; maxC = maxOf(maxC, t)
        val revs = OilModel.rpm(kph) * minutes
        penaltyUnits += revs * OilModel.penalty(t); penaltySlow += revs * OilModel.penalty(s)
        km += kph * minutes / 60
        val inflow = if (t < OilModel.INFLOW_STOP_C) OilModel.COLD_INFLOW else OilModel.WARM_INFLOW
        light += OilModel.LIGHT_SHARE * inflow * minutes; heavy += (1 - OilModel.LIGHT_SHARE) * inflow * minutes
        OilModel.tauLight(t)?.let { light -= light * minutes / it }
        OilModel.tauHeavy(t)?.let { heavy -= heavy * minutes / it }
    }

    fun stop(atMs: Long? = null) { stoppedAtMs = atMs }

    /** Stress of the current trip in normal-use kilometres, at the nominal and the slow warm-up. */
    val stressKm get() = penaltyUnits * OilModel.SCALE
    val stressSlowKm get() = penaltySlow * OilModel.SCALE

    fun reset() { light = 0.0; heavy = 0.0 }

    fun toJson(): JSONObject = JSONObject().put("light", light).put("heavy", heavy)
        .apply { oilC?.let { put("oil", it) }; slowC?.let { put("slow", it) }; stoppedAtMs?.let { put("stopped", it) } }
    companion object {
        fun fromJson(o: JSONObject?) = OilEngine(o?.takeIf { it.has("oil") }?.getDouble("oil"), o?.takeIf { it.has("slow") }?.getDouble("slow"),
            o?.takeIf { it.has("stopped") }?.getLong("stopped"), o?.optDouble("light", 0.0) ?: 0.0, o?.optDouble("heavy", 0.0) ?: 0.0)
    }
}

/**
 * One drive from leaving Park to returning to it, as seen by the app. [stressKm] and [stressSlowKm] are its oil stress
 * in normal-use kilometres at the nominal and slow warm-up; [dilution] the estimated fuel in the oil after it;
 * [peakOilC] the highest estimated oil temperature. Trips stored before the temperature model have none of these and
 * are weighted by their kind.
 */
data class OilTrip(val atMs: Long, val km: Double, val minutes: Double, val idleMinutes: Double, val outsideC: Double?,
                   val stressKm: Double? = null, val stressSlowKm: Double? = null, val dilution: Double? = null, val peakOilC: Double? = null) {
    val kind get() = OilModel.kind(km, outsideC)
    /** Stress for trips from before the temperature model: the old trip weights. */
    private val legacy get() = km * when (kind) { OilModel.Kind.NORMAL -> 1.0; OilModel.Kind.SHORT -> 2.0; OilModel.Kind.SHORT_COLD -> 3.5 }
    val stress get() = stressKm ?: legacy
    val stressHigh get() = stressSlowKm ?: legacy
}

data class OilSummary(
    val trips: Int, val short: Int, val shortCold: Int, val observedKm: Double, val unseenKm: Double,
    val idleMinutes: Double, val driveMinutes: Double, val effectiveKm: Pair<Double, Double>,
    /** Estimated fuel in the oil after the latest trip, mass %; null before any modelled trip. */
    val dilution: Double? = null,
) {
    val load get() = effectiveKm.first / OilModel.normalKm() to effectiveKm.second / OilModel.normalKm()
    /** Kilometres the driving conditions added on top of the odometer. */
    val addedKm get() = (effectiveKm.first - observedKm - unseenKm).coerceAtLeast(0.0)
}

/**
 * Segments trips by the gear selector, integrates distance from vehicle speed and runs the oil model on every sample.
 * A trip the app lost sight of part-way is dropped, since its length is unknown; that distance is still counted at the
 * normal rate from the odometer.
 */
class OilTrips(val trips: MutableList<OilTrip> = mutableListOf(), val marks: MutableMap<String, Int> = mutableMapOf(),
               val engine: OilEngine = OilEngine()) {
    private var startMs: Long? = null
    private var lastMs = 0L
    private var lastMps = 0.0
    private var meters = 0.0
    private var idleMs = 0L
    private var outsideC: Double? = null

    val driving get() = startMs != null

    /** Gear selector update; returns the finished trip when the car returns to Park. [wallMs] times the parking soak. */
    fun gear(parked: Boolean, nowMs: Long, outside: Double?, wallMs: Long = System.currentTimeMillis()): OilTrip? {
        val start = startMs
        if (!parked) {
            if (start == null) {
                startMs = nowMs; lastMs = nowMs; lastMps = 0.0; meters = 0.0; idleMs = 0; outsideC = outside
                val parkedMin = engine.stoppedAtMs?.let { (wallMs - it) / 60_000.0 }?.takeIf { it >= 0 }
                engine.start(outside ?: DEFAULT_OUTSIDE_C, parkedMin)
            }
            return null
        }
        if (start == null) return null
        speed(lastMps, nowMs)
        startMs = null
        engine.stop(wallMs)
        if (meters < MIN_METERS) return null
        return OilTrip(wallMs, meters / 1000, (nowMs - start) / 60_000.0, idleMs / 60_000.0, outsideC,
            engine.stressKm, engine.stressSlowKm, engine.dilution, engine.maxC).also { add(it) }
    }

    fun speed(mps: Double, nowMs: Long) {
        if (startMs == null) return
        val v = kotlin.math.abs(mps)
        val dt = (nowMs - lastMs).coerceAtLeast(0)
        // Trapezoid over the gap; a stale gap (no samples for a while) is not driving we saw.
        if (dt <= MAX_GAP_MS) {
            meters += (lastMps + v) / 2 * dt / 1000
            if (lastMps < IDLE_MPS && v < IDLE_MPS) idleMs += dt
            if (dt > 0) engine.step((lastMps + v) / 2 * 3.6, dt / 60_000.0)
        }
        lastMs = nowMs; lastMps = v
    }

    /** The app stopped seeing the car mid-trip; the trip's true length is unknown. */
    fun abort() { if (startMs != null) engine.stop(System.currentTimeMillis()); startMs = null }

    fun add(t: OilTrip) {
        trips += t
        if (trips.size > MAX) { val drop = trips.size - MAX; repeat(drop) { trips.removeAt(0) }; marks.replaceAll { _, v -> (v - drop).coerceAtLeast(0) } }
    }
    /** An oil change: fresh oil holds no fuel, the engine's temperature carries on. */
    fun markBaseline(recordId: String) { abort(); engine.reset(); marks[recordId] = trips.size }

    /** Trips since the record; [odometerKm] is distance since the record when the odometer knows it. */
    fun summary(recordId: String?, odometerKm: Double?): OilSummary {
        val list = trips.drop(marks[recordId] ?: 0)
        val seen = list.sumOf { it.km }
        val unseen = odometerKm?.let { (it - seen).coerceAtLeast(0.0) } ?: 0.0
        return OilSummary(list.size, list.count { it.kind == OilModel.Kind.SHORT }, list.count { it.kind == OilModel.Kind.SHORT_COLD },
            seen, unseen, list.sumOf { it.idleMinutes }, list.sumOf { it.minutes },
            unseen + list.sumOf { it.stress } to unseen + list.sumOf { it.stressHigh }, list.lastOrNull { it.dilution != null }?.dilution)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("trips", JSONArray().apply { trips.forEach { t -> put(JSONObject().put("at", t.atMs).put("km", t.km).put("min", t.minutes).put("idle", t.idleMinutes).apply {
            t.outsideC?.let { put("out", it) }; t.stressKm?.let { put("s", it) }; t.stressSlowKm?.let { put("s2", it) }
            t.dilution?.let { put("f", it) }; t.peakOilC?.let { put("peak", it) } }) } })
        .put("marks", JSONObject(marks as Map<*, *>))
        .put("engine", engine.toJson())

    companion object {
        const val MIN_METERS = 50.0
        const val IDLE_MPS = 0.5
        const val MAX_GAP_MS = 5_000L
        /** Used when the car gives no outside temperature: a mild day, so a cold start is not invented. */
        const val DEFAULT_OUTSIDE_C = 15.0
        // A year of driving at about five trips a day, so one oil interval never loses its oldest trips.
        const val MAX = 2_000
        private fun JSONObject.num(k: String) = if (has(k)) getDouble(k) else null
        fun fromJson(o: JSONObject) = OilTrips(
            mutableListOf<OilTrip>().apply {
                o.optJSONArray("trips")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { t ->
                    add(OilTrip(t.getLong("at"), t.getDouble("km"), t.getDouble("min"), t.getDouble("idle"), t.num("out"),
                        t.num("s"), t.num("s2"), t.num("f"), t.num("peak")))
                } }
            },
            mutableMapOf<String, Int>().apply { o.optJSONObject("marks")?.let { m -> m.keys().forEach { put(it, m.getInt(it)) } } },
            OilEngine.fromJson(o.optJSONObject("engine")))
    }
}
