package dev.vehiclecare.app

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tire rotation and tread wear from driving dynamics. The Tucson 2026 maintenance table rotates tires every 10,000 km to
 * prevent uneven wear. Tread wear follows frictional energy (Schallamach, Grosch), which grows with the square of the
 * force (Pohrt 2019 review), so wear scales with the mean square of acceleration, E[a_long²] + E[a_lat²]. Refitting
 * Giechaskiel et al. (Lubricants 12, 338, 2024; two FWD SUVs) the square reproduces their urban/motorway wear ratio
 * (measured 5.9 and 2.0; square 2.2 and 2.0, linear 1.5 and 1.4), though on six points. The reference is the centre of
 * UN Regulation 117's normal driving for its abrasion test as consolidated in 2025 (Annex 10 1.6.13.3.1: σ_long 0.45 and
 * σ_lat 0.93 m/s², each ±10 %). Sources and grades: materials/04_research/tire/model-inputs.md.
 */
object TireModel {
    const val ROTATION_KM = 10_000L
    /** 235/60 R18 rear track, Tucson 2026 owner's manual; yaw comes from the two rear (undriven) wheels. */
    const val REAR_TRACK_M = 1.622
    const val NORMAL_LONG = 0.45
    const val NORMAL_LAT = 0.93
    /** The regulation's normal driving as a mean square: 0.45² + 0.93² = 1.067 (m/s²)². */
    const val REFERENCE_MS = NORMAL_LONG * NORMAL_LONG + NORMAL_LAT * NORMAL_LAT
    /** Wear that remains at zero acceleration (rolling, steady drive force): about 10 % (Giechaskiel intercept, weak). */
    const val BASE_SHARE = 0.1
    /** Understeer gradient on the steering-wheel angle, 3 deg/g (2-4 measured, Vilela & Barbosa 2011, OSU 2009). */
    const val UNDERSTEER_RAD_PER_MPS2 = 3.0 * Math.PI / 180 / 9.81
    /** Below about 7 km/h steering says parking, not cornering load. */
    const val STEER_MIN_MPS = 2.0
    /** Understeer learning: cornering windows needed per side, and the lowest speed used (30 km/h). */
    const val LEARN_MIN = 30.0
    const val LEARN_MIN_MPS = 30 / 3.6
    /** Warmer roads wear more: 2.7 % per °C between 9 and 29 °C (Vehicles 7-91 convoy); reference 18.5 °C. */
    const val TEMPERATURE_PER_C = 0.027
    const val REFERENCE_C = 18.5
    /** Hyundai manual and Korean tire makers: about 3 mm a good time to change, 2.4 mm on motorways, 1.6 mm the limit. */
    const val TREAD_SOON_MM = 3.0
    /** The upper end of the measured FWD front wear range against its centre (2.0 against 1.47 mm per 10,000 km). */
    const val WEAR_HIGH = 1.36
    /**
     * A stretch this long without a recorded rotation most likely had one nobody entered; it wears both sets at the
     * axle average. The upper estimate (1.36x) still covers the unrotated front at its centre rate.
     */
    const val ROTATION_UNKNOWN_KM = 15_000.0
    /** Tucson 2026 manual: replace at 2.4 mm left on motorways, and six years after manufacture even if unused. */
    const val MOTORWAY_TREAD_MM = 2.4
    const val AGE_REPLACE_YEARS = 6.0
    /** The app knows when tires were fitted, not made (DOT week and year on the sidewall); a year before six it asks for that date. */
    const val AGE_CHECK_YEARS = 5.0
    /** Below this much observed driving a standard deviation says more about one trip than about the driver. */
    const val MIN_OBSERVED_M = 50_000.0
    /** The regulation derives acceleration from 1 Hz GNSS speed; a 1 s window over the 10 Hz feed matches it. */
    const val WINDOW_MS = 1_000L
    /** Tires cannot transmit much beyond 1 g; a larger figure is a jump in the signal, not driving, and is dropped. */
    const val MAX_ACCEL = 9.81

    /** Rear-wheel ticks in a window agree with the speed signal; static or stale tick data does not. */
    fun ticksAgree(wheelM: Double, speedM: Double) = speedM > 1.0 && abs(wheelM - speedM) <= 0.2 * speedM + 0.5
}

/** Distance-weighted acceleration sums; the standard deviation needs only these, so a year of driving fits in a few numbers. */
data class Moments(val distanceM: Double = 0.0, val sum: Double = 0.0, val squares: Double = 0.0) {
    fun add(d: Double, a: Double) = Moments(distanceM + d, sum + d * a, squares + d * a * a)
    operator fun minus(o: Moments) = Moments(distanceM - o.distanceM, sum - o.sum, squares - o.squares)
    val sd: Double? get() = if (distanceM <= 0) null else (squares / distanceM - (sum / distanceM).let { it * it }).coerceAtLeast(0.0).let(::sqrt)
    /** E[a²] about zero, the quantity frictional energy follows; equals sd² when the mean is zero. */
    val meanSquare: Double? get() = if (distanceM <= 0) null else squares / distanceM
    val mean: Double? get() = if (distanceM <= 0) null else sum / distanceM
    fun toJson(): JSONObject = JSONObject().put("d", distanceM).put("s", sum).put("q", squares)
    companion object { fun fromJson(o: JSONObject?) = o?.let { Moments(it.optDouble("d", 0.0), it.optDouble("s", 0.0), it.optDouble("q", 0.0)) } ?: Moments() }
}

/** Driving dynamics since a rotation or a new set of tires. */
data class TireStats(val longitudinal: Moments, val lateral: Moments, val since: Boolean) {
    val distanceM get() = longitudinal.distanceM
    /** Lateral only once enough of it was measured; a car without usable wheel ticks reports longitudinal alone. */
    val lateralSd: Double? get() = if (lateral.distanceM >= TireModel.MIN_OBSERVED_M) lateral.sd else null
    /**
     * Wear relative to the regulation's normal driving: 0.1 + 0.9 × (E[a_long²] + E[a_lat²]) / 1.067. Lateral carries
     * over 80 % of the signal; without it the regulation's own 0.93 m/s² stands in, so only the longitudinal part moves.
     */
    val factor: Double? get() = if (distanceM < TireModel.MIN_OBSERVED_M) null else longitudinal.meanSquare?.let { lon ->
        val lat = if (lateralSd != null) lateral.meanSquare!! else TireModel.NORMAL_LAT * TireModel.NORMAL_LAT
        TireModel.BASE_SHARE + (1 - TireModel.BASE_SHARE) * (lon + lat) / TireModel.REFERENCE_MS
    }
}

/**
 * Longitudinal acceleration from the vehicle speed over 1 s windows, lateral from the rear-wheel speed difference
 * (a = v·ω, ω = Δv / track), each weighted by the distance of its window as the regulation does. Read-only.
 */
class TireLoad(var longitudinal: Moments = Moments(), var lateral: Moments = Moments(),
               val baselines: MutableMap<String, Pair<Moments, Moments>> = mutableMapOf(),
               /** Distance-weighted outside temperature (°C in the [Moments.sum] slot), for the warm-road factor. */
               var warmth: Moments = Moments(), val warmthBaselines: MutableMap<String, Moments> = mutableMapOf()) {
    /**
     * This car's understeer gradient, learned from windows that carry both steering and rear-wheel ticks: at 30 km/h or
     * more and 1-4 m/s² of cornering, where the linear bicycle model holds. Left and right turns are kept apart and
     * averaged, because a rear tire slightly smaller than the other reads as extra cornering one way and less the other,
     * and the average cancels it. Used once each side has [TireModel.LEARN_MIN] windows, held to 1-5 deg/g.
     */
    var kLeft = Moments(); var kRight = Moments()
    private fun learn(delta: Double, side: Double, v: Double, wheelbase: Double) {
        // Magnitudes only: the car's steering sign convention is not documented, the ticks say which way it turned.
        if (v < TireModel.LEARN_MIN_MPS || abs(side) !in 1.0..4.0 || abs(delta) < 1e-4) return
        val k = abs(delta) / abs(side) - wheelbase / (v * v)
        if (side > 0) kLeft = kLeft.add(1.0, k) else kRight = kRight.add(1.0, k)
    }
    /** Learned gradient in rad per m/s², or the 3 deg/g default until both sides have enough windows. */
    fun understeer(): Double {
        if (kLeft.distanceM < TireModel.LEARN_MIN || kRight.distanceM < TireModel.LEARN_MIN) return TireModel.UNDERSTEER_RAD_PER_MPS2
        return ((kLeft.mean!! + kRight.mean!!) / 2).coerceIn(TireModel.UNDERSTEER_RAD_PER_MPS2 / 3, TireModel.UNDERSTEER_RAD_PER_MPS2 * 5 / 3)
    }
    /** Latest steering-wheel angle [deg] and when; lateral comes from it when it is fresh. */
    private var steeringDeg: Double? = null
    private var steeringMs = 0L
    var outsideC: Double? = null
    fun steering(deg: Double, nowMs: Long) { steeringDeg = deg; steeringMs = nowMs }
    private var windowStartMs: Long? = null
    private var windowStartSpeed = 0.0
    private var lastSpeed: Double? = null
    private var lastMs = 0L
    private var windowM = 0.0
    private var left: Double? = null
    private var right: Double? = null
    private var windowLeft: Double? = null
    private var windowRight: Double? = null
    /** Whether the last window's ticks matched the speed; false on a car (or emulator) whose ticks do not move. */
    var ticksAgreed: Boolean? = null
        private set

    fun speed(mps: Double, nowMs: Long) {
        val v = abs(mps)
        lastSpeed?.let { v0 -> if (nowMs > lastMs) windowM += (v0 + v) / 2 * (nowMs - lastMs) / 1000.0 }
        lastSpeed = v; lastMs = nowMs
        val start = windowStartMs ?: run { open(v, nowMs); return }
        val seconds = (nowMs - start) / 1000.0
        if (nowMs - start < TireModel.WINDOW_MS) return
        val accel = (v - windowStartSpeed) / seconds
        if (windowM > 0 && abs(accel) <= TireModel.MAX_ACCEL) {
            longitudinal = longitudinal.add(windowM, accel)
            outsideC?.let { warmth = warmth.add(windowM, it) }
            val l = left; val r = right; val l0 = windowLeft; val r0 = windowRight
            val mean = windowM / seconds
            val steer = steeringDeg?.takeIf { nowMs - steeringMs <= 2 * TireModel.WINDOW_MS }
            // Steering gives lateral without the wheel-radius bias of rear-wheel ticks (1 mm of radius difference
            // reads as 1.3 m/s² at 100 km/h): a_y = v²δ / (L + K v²), δ = steering-wheel angle / steering ratio.
            val p = TireParams.of(CarModel.current)
            // Lateral from the rear-wheel ticks, when they move with the car.
            val tickSide = if (l != null && r != null && l0 != null && r0 != null) {
                val dl = l - l0; val dr = r - r0
                ticksAgreed = TireModel.ticksAgree((dl + dr) / 2, windowM)
                if (ticksAgreed == true) windowM / seconds * (dr - dl) / p.rearTrackM / seconds else null
            } else null
            if (steer != null) {
                val delta = Math.toRadians(steer / p.steeringRatio)
                val side = if (mean < TireModel.STEER_MIN_MPS) 0.0 else mean * mean * delta / (p.wheelbaseM + understeer() * mean * mean)
                if (abs(side) <= TireModel.MAX_ACCEL) lateral = lateral.add(windowM, side)
                // With both, a steady cornering window teaches this car's own understeer: K = δ/a_y − L/v².
                if (tickSide != null) learn(delta, tickSide, mean, p.wheelbaseM)
            } else if (tickSide != null && abs(tickSide) <= TireModel.MAX_ACCEL) lateral = lateral.add(windowM, tickSide)
        }
        open(v, nowMs)
    }

    /** Cumulative rear-wheel travel in metres; a reset of the car's tick counters starts over without a jump. */
    fun wheels(leftM: Double, rightM: Double, reset: Boolean) {
        if (reset) { windowLeft = null; windowRight = null }
        left = leftM; right = rightM
        if (windowLeft == null) { windowLeft = leftM; windowRight = rightM }
    }

    private fun open(v: Double, nowMs: Long) {
        windowStartMs = nowMs; windowStartSpeed = v; windowM = 0.0
        windowLeft = left; windowRight = right
    }

    /** Forgets the open window; driving while nothing collected was not seen and must not be interpolated. */
    fun gap() { windowStartMs = null; lastSpeed = null; windowM = 0.0; left = null; right = null; windowLeft = null; windowRight = null }
    fun markBaseline(recordId: String) { baselines[recordId] = longitudinal to lateral; warmthBaselines[recordId] = warmth }
    /** Average outside temperature over the distance since the record; null before any. */
    fun warmthSince(recordId: String?): Double? = (warmthBaselines[recordId]?.let { warmth - it } ?: warmth).mean
    /** Driving between two records (the later one or now), for one segment of a tire set's life. */
    fun between(fromId: String, toId: String?): Pair<TireStats, Double?>? {
        val from = baselines[fromId] ?: return null
        val to = if (toId == null) longitudinal to lateral else baselines[toId] ?: return null
        val w = (if (toId == null) warmth else warmthBaselines[toId] ?: warmth) - (warmthBaselines[fromId] ?: Moments())
        return TireStats(to.first - from.first, to.second - from.second, true) to w.mean
    }
    fun since(recordId: String?): TireStats = baselines[recordId]
        ?.let { (l, t) -> TireStats(longitudinal - l, lateral - t, true) }
        ?: TireStats(longitudinal, lateral, false)

    fun toJson(): JSONObject = JSONObject().put("long", longitudinal.toJson()).put("lat", lateral.toJson()).put("warm", warmth.toJson())
        .put("kL", kLeft.toJson()).put("kR", kRight.toJson())
        .put("baselines", JSONObject().apply { baselines.forEach { (id, v) -> put(id, JSONObject().put("long", v.first.toJson()).put("lat", v.second.toJson())
            .apply { warmthBaselines[id]?.let { put("warm", it.toJson()) } }) } })
    companion object {
        fun fromJson(o: JSONObject): TireLoad {
            val warm = mutableMapOf<String, Moments>()
            val base = mutableMapOf<String, Pair<Moments, Moments>>().apply {
                o.optJSONObject("baselines")?.let { b -> b.keys().forEach { id -> b.getJSONObject(id).let {
                    put(id, Moments.fromJson(it.optJSONObject("long")) to Moments.fromJson(it.optJSONObject("lat")))
                    it.optJSONObject("warm")?.let { w -> warm[id] = Moments.fromJson(w) }
                } } }
            }
            return TireLoad(Moments.fromJson(o.optJSONObject("long")), Moments.fromJson(o.optJSONObject("lat")), base, Moments.fromJson(o.optJSONObject("warm")), warm)
                .apply { kLeft = Moments.fromJson(o.optJSONObject("kL")); kRight = Moments.fromJson(o.optJSONObject("kR")) }
        }
    }
}

/** How far the tires are into their rotation interval, from the odometer and, when seen, the way they were driven. */
data class Rotation(val baseline: ServiceRecord, val sinceKm: Double, val equivalentKm: Double, val stats: TireStats) {
    val share get() = maxOf(sinceKm, equivalentKm) / TireModel.ROTATION_KM
    val broughtForward get() = equivalentKm > sinceKm + 0.5

    companion object {
        /** The latest rotation or tire change with a known odometer; inspections do not restart the rotation interval. */
        fun baseline(records: List<ServiceRecord>) = records.withIndex()
            .filter { (_, r) -> r.part == Part.TIRE && r.odometerKm != null && r.date != null && (r.action == ServiceAction.ROTATE || r.action == ServiceAction.REPLACE) }
            .maxWithOrNull(compareBy({ it.value.date }, { it.value.odometerKm }, { it.index }))?.value

        fun of(records: List<ServiceRecord>, mileage: Mileage?, load: TireLoad): Rotation? {
            val base = baseline(records) ?: return null
            val current = mileage?.km ?: return null
            val since = (current - base.odometerKm!!).coerceAtLeast(0).toDouble()
            val stats = load.since(base.id)
            // Driving the app did not see counts at the reference rate, as an unseen brake distance does.
            val equivalent = stats.factor?.takeIf { stats.since }?.let { f ->
                val seen = minOf(stats.distanceM / 1000, since)
                seen * f + (since - seen)
            } ?: since
            return Rotation(base, since, equivalent, stats)
        }
    }
}

/** How long the current set has been on the car, from the latest tire replacement on record. */
data class TireAge(val fitted: ServiceRecord, val years: Double) {
    val share get() = years / TireModel.AGE_REPLACE_YEARS
    val status get() = when { years >= TireModel.AGE_REPLACE_YEARS -> DueStatus.DUE; years >= TireModel.AGE_CHECK_YEARS -> DueStatus.SOON; else -> DueStatus.UPCOMING }
    /** "2년 6개월" style, whole months. */
    val label: String get() {
        val months = (years * 12).toInt()
        return listOfNotNull((months / 12).takeIf { it > 0 }?.let { "${it}년" }, (months % 12).takeIf { it > 0 || months == 0 }?.let { "${it}개월" }).joinToString(" ")
    }
    companion object {
        fun of(records: List<ServiceRecord>, today: java.time.LocalDate): TireAge? = records
            .filter { it.part == Part.TIRE && it.action == ServiceAction.REPLACE && it.date != null && !it.date.isAfter(today) }
            .maxByOrNull { it.date!! }?.let { TireAge(it, java.time.temporal.ChronoUnit.DAYS.between(it.date, today) / 365.25) }
    }
}

/**
 * The car's tire facts: new tread of its OE tire (Kumho US OE list), wear per 10,000 km on each axle at the regulation's
 * normal driving (the 2025 convoy of 15 cars, front 1.47 / rear 0.64 mm, scaled by load against the tire's load index),
 * front share by drive layout, and the steering ratio and wheelbase for lateral acceleration (Hyundai US/AU specs).
 * Values marked guessed have no direct source for that model.
 */
data class TireParams(val newMm: Double, val frontPer10k: Double, val rearPer10k: Double, val steeringRatio: Double, val wheelbaseM: Double,
                      val rearTrackM: Double, val guessed: Boolean = false) {
    companion object {
        fun of(spec: ModelSpec): TireParams {
            val hybrid = spec.powertrain == Powertrain.HYBRID
            return when (spec.code) {
                "NX4" -> if (hybrid) TireParams(7.4, 1.12, 0.49, 13.7, 2.755, 1.622) else TireParams(7.4, 1.05, 0.46, 13.7, 2.755, 1.622)
                "CN7" -> TireParams(7.1, 1.22, 0.53, 12.9, 2.720, 1.596)
                "DN8" -> TireParams(7.7, 1.29, 0.56, 13.27, 2.840, 1.630)
                "MX5" -> TireParams(7.4, 1.20, 0.52, 13.7, 2.815, 1.656)
                // Rear-wheel drive: the rear carries about 70 % (one RWD car measured, extrapolated).
                "NE" -> TireParams(7.4, 0.60, 1.40, 14.26, 3.000, 1.647)
                // Casper: no OE tread or steering ratio found; Avante's wear scaled by mass, ratio from lock-to-lock.
                "AX1" -> TireParams(7.5, 0.99, 0.43, 14.0, 2.400, 1.409, guessed = true)
                "AX1 EV" -> TireParams(7.5, 1.27, 0.55, 14.1, 2.580, 1.425, guessed = true)
                else -> TireParams(7.4, 1.05, 0.46, 13.7, 2.755, 1.622, guessed = true)
            }
        }
    }
}

/**
 * Tread left on the set now on the front and rear axles, counted from the latest tire replacement: each stretch between
 * rotations wears each set by its axle's rate times the driving factor and the warm-road factor, and a rotation swaps
 * the sets. Distance comes from the odometer; a stretch the app did not watch counts at the normal rate. Upper is the
 * high end of the measured axle wear range, on the front-rate path where a rotation may have been skipped.
 *
 * A recorded inspection that did not end in new tires says the tread was still at least 3 mm then (the makers' advice
 * is to replace at 3 mm). That caps how fast this set can be wearing, so the multiplier on the estimate drops to
 * (new - 3) / worn-at-inspection when that is lower; [checked] is then set, and the next ask waits for 2.4 mm.
 */
data class TireTread(val front: Double, val rear: Double, val frontHigh: Double, val rearHigh: Double, val fitted: ServiceRecord,
                     val checked: Boolean = false, val highMultiplier: Double = TireModel.WEAR_HIGH,
                     /** The check whose gauge reading the estimate starts from, if any. */
                     val measured: ServiceRecord? = null) {
    val lowest get() = minOf(frontHigh, rearHigh)
    val central get() = minOf(front, rear)
    /** The upper estimate at which the app asks for a look: 3 mm, or 2.4 mm once an inspection has vouched for 3. */
    val askAt get() = if (checked) TireModel.MOTORWAY_TREAD_MM else TireModel.TREAD_SOON_MM

    companion object {
        fun of(records: List<ServiceRecord>, mileage: Mileage?, load: TireLoad, p: TireParams = TireParams.of(CarModel.current)): TireTread? {
            val tire = records.filter { it.part == Part.TIRE && it.odometerKm != null && it.date != null }.sortedWith(compareBy({ it.date }, { it.odometerKm }))
            val fitted = tire.lastOrNull { it.action == ServiceAction.REPLACE } ?: return null
            val now = mileage?.km ?: return null
            val stops = tire.filter { it.action == ServiceAction.ROTATE && it.odometerKm!! >= fitted.odometerKm!! && (it.date!! >= fitted.date!!) }
            // Worn mm on set A (started on the front) and set B, at the centre rate and on the upper path. A stretch with no
            // recorded rotation wears both sets at the axle average in the centre, and at the front rate on the upper path,
            // so an owner who truly never rotated is still covered.
            var a = 0.0; var b = 0.0; var aHi = 0.0; var bHi = 0.0
            var aOnFront = true
            val marks = listOf(fitted) + stops
            marks.forEachIndexed { i, from ->
                val to = marks.getOrNull(i + 1)
                val km = ((to?.odometerKm ?: now) - from.odometerKm!!).coerceAtLeast(0).toDouble()
                val (stats, temp) = load.between(from.id, to?.id) ?: (null to null)
                val drive = stats?.factor?.let { f -> val seen = minOf(stats.distanceM / 1000, km); if (km > 0) (seen * f + (km - seen)) / km else 1.0 } ?: 1.0
                val warm = temp?.let { (1 + TireModel.TEMPERATURE_PER_C * (it - TireModel.REFERENCE_C)).coerceIn(0.75, 1.3) } ?: 1.0
                val per = km * drive * warm / 10_000
                if (km > TireModel.ROTATION_UNKNOWN_KM) {
                    val mean = (p.frontPer10k + p.rearPer10k) / 2 * per
                    a += mean; b += mean; aHi += p.frontPer10k * per; bHi += p.frontPer10k * per
                } else {
                    val front = p.frontPer10k * per; val rear = p.rearPer10k * per
                    if (aOnFront) { a += front; b += rear; aHi += front; bHi += rear } else { a += rear; b += front; aHi += rear; bHi += front }
                }
                if (to != null) aOnFront = !aOnFront
            }
            val (f, r) = if (aOnFront) a to b else b to a
            val (fHi, rHi) = if (aOnFront) aHi to bHi else bHi to aHi
            // A depth read with a gauge replaces the estimate up to then (on the more worn axle, which a check reads first);
            // the model carries on from it, with the upper estimate counting only the wear since.
            tire.lastOrNull { it.action == ServiceAction.INSPECT && it.measuredMm != null && it.odometerKm!! > fitted.odometerKm!! && it.odometerKm <= now }?.let { m ->
                val then = of(tire.filter { it.action != ServiceAction.INSPECT && it.odometerKm!! <= m.odometerKm!! }, Mileage(m.odometerKm!!, m.date!!, "record"), load, p)
                if (then != null) {
                    val shift = m.measuredMm!! - then.central
                    val front = p.newMm - f + shift; val rear = p.newMm - r + shift
                    val sinceF = (then.front + shift - front).coerceAtLeast(0.0); val sinceR = (then.rear + shift - rear).coerceAtLeast(0.0)
                    return TireTread(front, rear, then.front + shift - sinceF * TireModel.WEAR_HIGH, then.rear + shift - sinceR * TireModel.WEAR_HIGH, fitted, measured = m)
                }
            }
            // An inspection that ended without new tires vouches for 3 mm: no path may have worn more than new - 3 by then.
            val inspected = tire.lastOrNull { it.action == ServiceAction.INSPECT && it.odometerKm!! > fitted.odometerKm!! && it.odometerKm <= now }
            val then = inspected?.let { i -> of(tire.filter { it.action != ServiceAction.INSPECT && it.odometerKm!! <= i.odometerKm!! }, Mileage(i.odometerKm!!, i.date!!, "record"), load, p) }
            val room = p.newMm - TireModel.TREAD_SOON_MM
            val centre = then?.let { t -> (p.newMm - t.central).takeIf { it > room }?.let { room / it } } ?: 1.0
            val capHi = then?.let { t -> ((p.newMm - t.lowest) / (t.highMultiplier)).takeIf { it > 0 }?.let { room / it } }?.takeIf { it < TireModel.WEAR_HIGH }
            val high = capHi ?: TireModel.WEAR_HIGH
            return TireTread(p.newMm - f * centre, p.newMm - r * centre, p.newMm - fHi * high, p.newMm - rHi * high, fitted, checked = capHi != null, highMultiplier = high)
        }
    }
}
