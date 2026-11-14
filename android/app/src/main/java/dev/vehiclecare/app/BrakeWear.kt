package dev.vehiclecare.app

import org.json.JSONObject

/**
 * The car's numbers for brake work: mass, road load, how much braking the friction brakes do, and pad thickness.
 * Curb weights are Korean price lists (middle of the trim range); road load is EPA's 2026 coast-down target for the same
 * body (f0 N, f1 N/(km/h), f2 N/(km/h)²; for engines not sold in the US the same body's other engine); friction share
 * is GTR 24's value for each powertrain, narrowed by measurements. Sources and grades: materials/04_research/brake.
 */
data class BrakeParams(val curbKg: Double, val f0: Double, val f1: Double, val f2: Double, val frictionShare: Double, val frictionShareHigh: Double,
                       val padNewMm: Double = 11.0, val padLimitMm: Double = 2.0, val roadLoadGuessed: Boolean = false) {
    /** Driver on board and 3 % for rotating parts, as Green NCAP's brake energy procedure. */
    val massKg get() = (curbKg + 75) * 1.03
    fun roadLoadN(mps: Double) = (mps * 3.6).let { v -> f0 + f1 * v + f2 * v * v }

    companion object {
        private const val ICE = 1.0
        // HEV: GTR 24 assigns 0.52; INL measured 42-50 % friction on gentle stops. BEV: GTR 24 0.17; Green NCAP brake
        // energy of the Casper Electric (Inster) back-calculates to 0.11-0.13.
        private const val HEV = 0.40; private const val HEV_HIGH = 0.52
        private const val BEV = 0.13; private const val BEV_HIGH = 0.22

        fun of(spec: ModelSpec): BrakeParams {
            val (share, high) = when (spec.powertrain) { Powertrain.HYBRID -> HEV to HEV_HIGH; Powertrain.ELECTRIC -> BEV to BEV_HIGH; else -> ICE to ICE }
            fun p(kg: Double, f0: Double, f1: Double, f2: Double, guessed: Boolean = false) = BrakeParams(kg, f0, f1, f2, share, high, roadLoadGuessed = guessed)
            val hybrid = spec.powertrain == Powertrain.HYBRID
            return when (spec.code) {
                "NX4" -> if (hybrid) p(1_678.0, 162.9, 0.118, 0.04562) else p(1_583.0, 122.0, 0.700, 0.04120)
                "CN7" -> if (hybrid) p(1_375.0, 93.8, 1.111, 0.02714) else p(1_290.0, 125.5, -0.145, 0.03371)
                "DN8" -> if (hybrid) p(1_578.0, 134.2, -0.125, 0.03167) else p(1_508.0, 118.1, 0.630, 0.03035)
                "MX5" -> if (hybrid) p(1_945.0, 162.6, 0.218, 0.04359) else p(1_890.0, 136.0, 1.380, 0.03830)
                "NE" -> p(2_020.0, 128.0, 0.686, 0.03696)
                // No EPA data for Casper: rolling 0.8 % of weight and a CdA of 0.75 m² are guesses.
                "AX1" -> p(1_030.0, 0.008 * 1_105 * 9.81, 0.0, 0.0347, guessed = true)
                "AX1 EV" -> p(1_348.0, 0.008 * 1_423 * 9.81, 0.0, 0.0347, guessed = true)
                // Unknown car: the Tucson's body, the powertrain's friction share.
                else -> p(1_583.0, 122.0, 0.700, 0.04120, guessed = true)
            }
        }
    }
}

/**
 * Front brake pad wear from friction brake work, after Jensen, Santos & Corstens: wear height h = k * W, where W is the
 * vehicle's braking work (Wear 538-539, 205220 and Wear 552-553, 205433). The work is the kinetic energy lost while the
 * pedal is pressed minus what road load took, the method of Green NCAP and GM US 9,416,835; regenerative braking takes
 * its share first on hybrids and EVs. Engine drag is not removed (no Hyundai values), so an engine car's estimate errs
 * on the worn side.
 *
 * Wear speeds up when the pads are hot. A lumped front-disc model (the structure of GM US 9,416,835 and Hyundai
 * KR 10-2020-0058810, cooling fitted to measured WLTP-Brake disc temperatures) estimates the disc temperature and
 * scales wear above 200 °C. Everyday driving stays well under that (measured
 * WLTP-Brake 131-184 °C at most, Mamakos 2021), so the factor matters on long descents and repeated hard stops.
 */
object BrakeWearModel {
    const val G = 9.81
    /** Front pad thickness lost per joule of vehicle friction brake work [mm/J]: every value fitted in both papers. */
    const val K_LOW = 0.75e-9
    const val K_MID = 1.22e-9
    const val K_HIGH = 1.74e-9
    /** Typical braking per kilogram and kilometre: WLTP-Brake 79 J/(kg·km) (Storch 2023); Jensen's mixed driving 73.5. */
    const val TYPICAL_J_PER_KG_KM = 79.0
    /** Jensen's mixed-driving average on the 1,580 kg car [J/m], kept for comparison on screen. */
    const val PAPER_RATIO_HIGH = 116.0

    // Front disc: 9 kg vented disc (Mamakos 2021; Tucson's 325 x 30 mm disc is 9-11 kg), 93 % of the heat into the
    // disc, 70 % of braking on the front axle (WLTP-Brake mass loss: rear 29 %).
    const val DISC_KG = 9.0
    const val DISC_HEAT_SHARE = 0.93
    const val FRONT_SHARE = 0.70
    /** Long press that kept the speed: a grade of about 5 %; counted in the upper estimate only, since the grade is unseen. */
    const val HOLD_GRADE = 0.05
    /** Pads at or under this are due for replacement; Hyundai and Mobis patents warn at 2 mm. */
    const val REPLACE_MM = 3.0
    const val SOON_MM = 4.0

    fun params() = BrakeParams.of(CarModel.current)

    /** Braking work over one speed segment while the pedal is pressed [J]; never negative. */
    fun segmentWork(v0: Double, v1: Double, seconds: Double, p: BrakeParams = params()): Double {
        val mean = (v0 + v1) / 2
        val kinetic = 0.5 * p.massKg * (v0 * v0 - v1 * v1)
        return (kinetic - p.roadLoadN(mean) * mean * seconds).coerceAtLeast(0.0)
    }
    fun wearMm(workJ: Double) = workJ * K_LOW to workJ * K_HIGH

    /** Cast iron heat capacity rises with temperature (IJPHM Fig. 12). */
    fun heatCapacity(c: Double) = when { c <= 20 -> 460.0; c <= 200 -> 460 + (c - 20) / 180 * 60; c <= 400 -> 520 + (c - 200) / 200 * 80; else -> minOf(720.0, 600 + (c - 400) / 200 * 120) }
    /**
     * Disc cooling per unit heat capacity [1/s]: convection h = 10 + 3·v W/m²K (≤ 100, Mamakos 2021's upper bound) over
     * 0.3 m². With WLTP-Brake's 303 stops this holds the disc 35-40 K over the start temperature, as measured (mean
     * 58-66 °C from 40 °C). GM's example coefficient (US 9,416,835) cools five times faster and gave 36 °C on the same
     * stops, so it is not used.
     */
    fun coolingNominal(mps: Double, c: Double) = minOf(100.0, 10 + 3 * mps) * 0.3 / (DISC_KG * heatCapacity(c))
    /** The hotter bound: 30 % less cooling (a shielded or dirty disc, a still day). */
    fun coolingSlow(mps: Double, c: Double) = 0.7 * coolingNominal(mps, c)

    /** Wear multiplier by pad-disc temperature, 1 up to 200 °C (Zhang 2009, Riefe and Yen, Dixcel OEM pads). */
    fun temperatureFactor(c: Double): Double {
        val pts = listOf(200.0 to 1.0, 250.0 to 1.3, 300.0 to 1.8, 350.0 to 2.5, 400.0 to 4.0, 500.0 to 5.0)
        if (c <= pts.first().first) return 1.0
        for (i in 1 until pts.size) if (c <= pts[i].first) {
            val (x0, y0) = pts[i - 1]; val (x1, y1) = pts[i]
            return y0 + (c - x0) / (x1 - x0) * (y1 - y0)
        }
        return 5.0
    }
}

/**
 * Pedal held while the car kept its speed: on a level road a 5 s press loses several m/s (drag and rolling resistance alone
 * take about 0.75 m/s from 50 km/h), so a press of [HOLD_SECONDS] or more that loses under [HOLD_DROP_MPS] above
 * [HOLD_MIN_MPS] was holding the car against a grade of roughly 5 % or more (or the brake was ridden). Kinetic energy
 * misses that work entirely, so these are counted beside the estimate rather than in it.
 */
data class BrakeHolds(val count: Int = 0, val seconds: Double = 0.0, val distanceM: Double = 0.0) {
    operator fun minus(o: BrakeHolds) = BrakeHolds(count - o.count, seconds - o.seconds, distanceM - o.distanceM)
    fun toJson(): JSONObject = JSONObject().put("n", count).put("s", seconds).put("d", distanceM)
    companion object {
        const val HOLD_SECONDS = 5.0
        const val HOLD_DROP_MPS = 1.0
        const val HOLD_MIN_MPS = 5.0
        fun fromJson(o: JSONObject?) = o?.let { BrakeHolds(it.optInt("n"), it.optDouble("s", 0.0), it.optDouble("d", 0.0)) } ?: BrakeHolds()
    }
}

/** Totals at one moment, so any record's share is the difference from its baseline. */
data class BrakeTotals(val workJ: Double = 0.0, val distanceM: Double = 0.0, val wearJ: Double = 0.0, val wearHighJ: Double = 0.0) {
    operator fun minus(o: BrakeTotals) = BrakeTotals(workJ - o.workJ, distanceM - o.distanceM, wearJ - o.wearJ, wearHighJ - o.wearHighJ)
    fun toJson(): JSONObject = JSONObject().put("work", workJ).put("distance", distanceM).put("wear", wearJ).put("wearHigh", wearHighJ)
    companion object {
        // Totals saved before the wear model have no wear figures; their braking counts at the engine-car share.
        fun fromJson(o: JSONObject) = BrakeTotals(o.getDouble("work"), o.getDouble("distance"), o.optDouble("wear", o.getDouble("work")), o.optDouble("wearHigh", o.getDouble("work")))
    }
}

/**
 * Brake work and distance the app saw. A segment counts as braking when the pedal was pressed at its start.
 * [wearJ] is friction work after regeneration, scaled by the nominal disc temperature; [wearHighJ] uses the larger
 * friction share, the slower-cooling disc and the work of held descents. Pad wear is k times these.
 */
class BrakeWork(var workJ: Double = 0.0, var distanceM: Double = 0.0, val baselines: MutableMap<String, BrakeTotals> = mutableMapOf(),
                var holds: BrakeHolds = BrakeHolds(), val holdBaselines: MutableMap<String, BrakeHolds> = mutableMapOf(),
                var wearJ: Double = 0.0, var wearHighJ: Double = 0.0, var discC: Double? = null, var discSlowC: Double? = null) {
    private var lastSpeed: Double? = null
    private var lastMs = 0L
    private var pedal = false
    private var pressSpeed: Double? = null
    private var pressMs = 0L
    private var pressM = 0.0
    /** Outside air for disc cooling; the feed sets it when the car reports it. */
    var outsideC = 15.0
    val totals get() = BrakeTotals(workJ, distanceM, wearJ, wearHighJ)

    fun speed(mps: Double, nowMs: Long, p: BrakeParams = BrakeWearModel.params()) {
        val v = kotlin.math.abs(mps)
        lastSpeed?.let { v0 ->
            val seconds = (nowMs - lastMs) / 1000.0
            if (seconds > 0) {
                val mean = (v0 + v) / 2
                distanceM += mean * seconds
                val work = if (pedal) BrakeWearModel.segmentWork(v0, v, seconds, p) else 0.0
                if (pedal) { workJ += work; pressM += mean * seconds }
                heat(work * p.frictionShare, work * p.frictionShareHigh, mean, seconds)
            }
        }
        lastSpeed = v; lastMs = nowMs
    }

    /** Front-disc temperature step and the wear it implies; the disc cools whenever the car is seen. */
    private fun heat(friction: Double, frictionHigh: Double, mps: Double, seconds: Double) {
        val t = discC ?: outsideC; val s = discSlowC ?: outsideC
        val perDisc = BrakeWearModel.DISC_HEAT_SHARE * BrakeWearModel.FRONT_SHARE / 2
        val nt = t + perDisc * friction / (BrakeWearModel.DISC_KG * BrakeWearModel.heatCapacity(t)) - BrakeWearModel.coolingNominal(mps, t) * (t - outsideC) * seconds
        val ns = s + perDisc * frictionHigh / (BrakeWearModel.DISC_KG * BrakeWearModel.heatCapacity(s)) - BrakeWearModel.coolingSlow(mps, s) * (s - outsideC) * seconds
        discC = nt.coerceAtLeast(outsideC); discSlowC = ns.coerceAtLeast(outsideC)
        wearJ += friction * BrakeWearModel.temperatureFactor(nt)
        wearHighJ += frictionHigh * BrakeWearModel.temperatureFactor(ns)
    }

    /** Closes the running segment at the last known speed so it is attributed to the pedal state it had. */
    fun pedal(pressed: Boolean, nowMs: Long, p: BrakeParams = BrakeWearModel.params()) {
        lastSpeed?.let { speed(it, nowMs, p) }
        if (pressed && !pedal) { pressSpeed = lastSpeed; pressMs = nowMs; pressM = 0.0 }
        if (!pressed && pedal) {
            val start = pressSpeed; val end = lastSpeed; val seconds = (nowMs - pressMs) / 1000.0
            if (start != null && end != null && seconds >= BrakeHolds.HOLD_SECONDS && start >= BrakeHolds.HOLD_MIN_MPS && end >= start - BrakeHolds.HOLD_DROP_MPS) {
                holds = BrakeHolds(holds.count + 1, holds.seconds + seconds, holds.distanceM + pressM)
                // A held descent: gravity on a 5 % grade less road load did the work kinetic energy did not show.
                val held = ((p.massKg * BrakeWearModel.G * BrakeWearModel.HOLD_GRADE) - p.roadLoadN((start + end) / 2)).coerceAtLeast(0.0) * pressM
                wearHighJ += held * p.frictionShareHigh * BrakeWearModel.temperatureFactor(discSlowC ?: outsideC)
            }
            pressSpeed = null
        }
        pedal = pressed
    }
    /** Forgets the open segment; driving while hidden was not seen and must not be interpolated. */
    fun gap() { lastSpeed = null; pedal = false; pressSpeed = null }
    fun markBaseline(recordId: String) { baselines[recordId] = totals; holdBaselines[recordId] = holds }
    /** Work and distance since the record, or since tracking began when the record predates tracking. */
    fun since(recordId: String?): Triple<Double, Double, Boolean> = totalsSince(recordId).let { Triple(it.workJ.coerceAtLeast(0.0), it.distanceM.coerceAtLeast(0.0), recordId != null && baselines.containsKey(recordId)) }
    fun totalsSince(recordId: String?): BrakeTotals = baselines[recordId]?.let { totals - it } ?: totals
    fun holdsSince(recordId: String?): BrakeHolds = holdBaselines[recordId]?.let { holds - it } ?: holds

    /** Front pad thickness worn since the record, nominal and upper [mm]. */
    fun padWornMm(recordId: String?) = totalsSince(recordId).let { it.wearJ * BrakeWearModel.K_MID to it.wearHighJ * BrakeWearModel.K_HIGH }

    fun toJson(): JSONObject = JSONObject().put("work", workJ).put("distance", distanceM).put("wear", wearJ).put("wearHigh", wearHighJ)
        .apply { discC?.let { put("disc", it) }; discSlowC?.let { put("discSlow", it) } }
        .put("baselines", JSONObject().apply { baselines.forEach { (id, v) -> put(id, v.toJson()) } })
        .put("holds", holds.toJson())
        .put("holdBaselines", JSONObject().apply { holdBaselines.forEach { (id, v) -> put(id, v.toJson()) } })
    companion object {
        fun fromJson(o: JSONObject) = BrakeWork(o.optDouble("work", 0.0), o.optDouble("distance", 0.0), mutableMapOf<String, BrakeTotals>().apply {
            o.optJSONObject("baselines")?.let { b -> b.keys().forEach { id -> put(id, BrakeTotals.fromJson(b.getJSONObject(id))) } }
        }, BrakeHolds.fromJson(o.optJSONObject("holds")), mutableMapOf<String, BrakeHolds>().apply {
            o.optJSONObject("holdBaselines")?.let { b -> b.keys().forEach { id -> put(id, BrakeHolds.fromJson(b.getJSONObject(id))) } }
        }, o.optDouble("wear", o.optDouble("work", 0.0)), o.optDouble("wearHigh", o.optDouble("work", 0.0)),
            if (o.has("disc")) o.getDouble("disc") else null, if (o.has("discSlow")) o.getDouble("discSlow") else null)
    }
}
