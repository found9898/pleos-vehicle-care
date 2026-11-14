package dev.vehiclecare.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * Cabin filter loading from outside-air ventilation time. Wei et al., Environ. Sci. Pollut. Res. 30 (2023), aged a filter
 * for 500 h on outdoor air at low fan without recirculation and treat that as end of service (about one year of driving).
 * Its fractional efficiency fell from 0.94 to 0.58 at 1.1 µm and from 0.71 to 0.33 at 100 nm.
 */
/**
 * Cabin filter loading. Wei et al. aged their filters for 500 h in Shanghai's outside air (PM2.5 42 µg/m³ that month) at
 * about 167 m³/h, which leaves 4.2-5.6 g of PM10 in the filter; traffic dust raised the pressure drop by 100 Pa at 3-4 g
 * (Johansson 1999), so both point at the same load. The app counts hours in those terms: each hour of blowing is weighed
 * by the air through the filter against 167 m³/h and by Korea's PM2.5 that month against 42, so 500 such hours is that
 * load. At the 2024 national mean (16 µg/m³) a low-speed blower needs about 1,300 real hours to get there.
 */
object FilterModel {
    const val END_OF_SERVICE_HOURS = 500.0
    const val WEI_PM25 = 42.0
    const val WEI_FLOW = 167.0
    /** National population-weighted PM2.5, 2024 (µg/m³). */
    const val KOREA_PM25 = 16.0
    /** Monthly against the annual mean: winter and spring high, summer low (2015-2022 monthly means, Seoul's seasons). */
    val MONTH = doubleArrayOf(1.3, 1.3, 1.35, 1.05, 1.0, 0.8, 0.65, 0.6, 0.65, 0.8, 1.05, 1.15)
    /** Recirculated cabin air carries about a tenth of outside's particles once the cabin has settled (2-31 %). */
    const val RECIRC_SHARE = 0.1
    /** With the blower off, ram air still crosses the filter on fresh intake above 10 km/h (Halla patent US7686872). */
    const val RAM_FLOW = 70.0
    const val RAM_KPH = 10.0
    /** Air through the filter by blower share: about 80 m³/h on the lowest step to 450 on the highest (Volvo, Ford examples). */
    fun flow(fanShare: Double?) = 80 + 370 * (fanShare ?: 0.5).coerceIn(0.0, 1.0)
    /**
     * 2024 annual PM2.5 by province (Ministry of Environment, 11 Feb 2025, attachment 1; urban network, national 15.6),
     * with each province's city hall as its point. The nearest point stands for where the car is; neighbouring
     * provinces differ by a few µg/m³ at most, so a border town placed one side or the other moves little.
     */
    val PROVINCES = listOf(
        Triple("서울", 37.5665 to 126.9780, 17.4), Triple("부산", 35.1796 to 129.0756, 13.5), Triple("대구", 35.8714 to 128.6014, 13.9),
        Triple("인천", 37.4563 to 126.7052, 18.9), Triple("광주", 35.1595 to 126.8526, 13.8), Triple("대전", 36.3504 to 127.3845, 14.3),
        Triple("울산", 35.5384 to 129.3114, 14.2), Triple("세종", 36.4800 to 127.2890, 16.1), Triple("경기", 37.2752 to 127.0095, 17.6),
        Triple("강원", 37.8813 to 127.7298, 12.9), Triple("충북", 36.6357 to 127.4917, 16.4), Triple("충남", 36.6588 to 126.6728, 17.6),
        Triple("전북", 35.8202 to 127.1088, 16.0), Triple("전남", 34.8161 to 126.4629, 12.3), Triple("경북", 36.5760 to 128.5056, 15.6),
        Triple("경남", 35.2383 to 128.6925, 13.0), Triple("제주", 33.4890 to 126.4983, 12.3))
    private const val PROVINCE_MEAN = 15.6
    /** Province nearest to [at] and its 2024 PM2.5; null without a position. */
    fun province(at: Pair<Double, Double>?) = at?.let { (lat, lon) -> PROVINCES.minByOrNull { (_, p, _) -> ServiceCatalog.km(lat, lon, p.first, p.second) } }
    /** PM2.5 for [month] where the car is: the province's 2024 level scaled to the population-weighted national mean, or the mean. */
    fun pm25(month: Int, at: Pair<Double, Double>?) = (province(at)?.third?.let { it / PROVINCE_MEAN } ?: 1.0) * KOREA_PM25 * MONTH[month - 1]
    /** Weight of one hour now: [m3h] through the filter in [month] (1-12), against the Shanghai test hour. */
    fun weight(m3h: Double, month: Int, pm25: Double = KOREA_PM25 * MONTH[month - 1]) = m3h / WEI_FLOW * pm25 / WEI_PM25
}

/** Hours of Shanghai-test load through the cabin filter, with baselines at each filter change. */
class FilterDust(var hours: Double = 0.0, val baselines: MutableMap<String, Double> = mutableMapOf()) {
    private var since: Long? = null
    private var m3h = 0.0
    /** Folds the running interval at the previous flow, then continues at [nextM3h] through the filter (0 stops). */
    fun update(nextM3h: Double, nowMs: Long, month: Int = java.time.LocalDate.now().monthValue, at: Pair<Double, Double>? = null) {
        since?.let { start -> hours += (nowMs - start).coerceAtLeast(0) / 3_600_000.0 * FilterModel.weight(m3h, month, pm25) }
        since = if (nextM3h > 0) nowMs else null; m3h = nextM3h; pm25 = FilterModel.pm25(month, at)
    }
    private var pm25 = FilterModel.KOREA_PM25
    fun markBaseline(recordId: String) { baselines[recordId] = hours }
    fun since(recordId: String?): Double? = baselines[recordId]?.let { (hours - it).coerceAtLeast(0.0) }
    fun toJson(): JSONObject = JSONObject().put("h", hours).put("baselines", JSONObject(baselines as Map<*, *>))
    companion object {
        /** Air through the filter now, from the climate state and speed. */
        fun m3h(blowing: Boolean, recirc: Boolean?, fanShare: Double?, kph: Double?) = when {
            blowing && recirc == true -> FilterModel.RECIRC_SHARE * FilterModel.flow(fanShare)
            blowing -> FilterModel.flow(fanShare)
            recirc != true && (kph ?: 0.0) >= FilterModel.RAM_KPH -> FilterModel.RAM_FLOW
            else -> 0.0
        }
        fun fromJson(o: JSONObject) = FilterDust(o.optDouble("h", 0.0), mutableMapOf<String, Double>().apply {
            o.optJSONObject("baselines")?.let { b -> b.keys().forEach { put(it, b.getDouble(it)) } } })
    }
}

/**
 * Refrigerant lost through regular leakage of an intact system, as a first-order loss: Korean cars measured at scrapping
 * kept 65.4 % after 8.7 years and 55.6 % after 12.4, an annual rate of 5.26 % ([CENTRE]). The EU study (Schwarz &
 * Harnisch 2003) found 10.5 % in a car's first year and about 5 % at four to six; [LOW] and [HIGH] bound a car's own
 * rate, with the first year at 10.5 % on the high side. A crash or stone that opens the system (2.2 % of cars a year)
 * is not a leak rate and cannot be forecast.
 */
object RefrigerantModel {
    const val LOW = 0.035
    const val CENTRE = 0.0526
    const val HIGH = 0.105
    /** Charge in grams per model, Korean owner's manuals (±25 g); heat-pump EVs carry more. */
    fun chargeG(spec: ModelSpec): Int = when (spec.code) {
        "NX4" -> 550; "CN7" -> 500; "DN8" -> if (spec.powertrain == Powertrain.HYBRID) 600 else 520
        "MX5" -> if (spec.powertrain == Powertrain.HYBRID) 800 else 625; "NE" -> 900; "AX1" -> 400; "AX1 EV" -> 550
        else -> 550
    }
    /** Share of the charge lost since [from], as a low and high bound. */
    fun lost(from: LocalDate, today: LocalDate): Pair<Double, Double>? {
        if (from.isAfter(today)) return null
        val years = ChronoUnit.DAYS.between(from, today) / 365.25
        val high = if (years <= 1) 1 - (1 - HIGH).pow(years) else 1 - (1 - HIGH) * kotlin.math.exp(-CENTRE * 2 * (years - 1))
        return (1 - kotlin.math.exp(-LOW * years)) to high
    }
}

/**
 * One cool-down or warm-up: how fast the cabin moved toward the set point after the A/C (or heater) started against it.
 * [closed] is the share of the start gap closed in the first probe minutes, which in the cabin simulations separated
 * lost capacity from day-to-day spread better than the raw rate. [speed] is the mean vehicle speed over the probe; engine
 * heat, and so the heater, follows engine load.
 */
data class ClimateSession(val atMs: Long, val ratePerMin: Double, val startC: Double, val setC: Double, val outsideC: Double?, val closed: Double? = null,
                    val fan: Double? = null, val recirc: Boolean? = null, val speed: Double? = null, val soakH: Double? = null) {
    /**
     * Run like [o]: blower share within [ClimateTracker.FAN_MATCH], the same intake, and for a warm-up a similar speed.
     * Unknown conditions match anything. Three more splits were tried in simulation and left out because each thinned the
     * eight-session reference until chance decided: the start's heat over outside air for pull-downs, parked-hour bands
     * for warm-ups (a 3-hour stop still holds a third of the coolant's heat; [soakH] is kept for that reason), and the
     * -6.7 °C line below which OBD may stop judging the thermostat.
     */
    fun alike(o: ClimateSession, bySpeed: Boolean = true) = (fan == null || o.fan == null || abs(fan - o.fan) <= ClimateTracker.FAN_MATCH) &&
        (recirc == null || o.recirc == null || recirc == o.recirc) &&
        (!bySpeed || speed == null || o.speed == null || abs(speed - o.speed) <= ClimateTracker.SPEED_MATCH)
}

/** A cooling decline against the sessions right after an A/C check: SOON for a steady loss, DUE for one that barely cools. */
data class CoolingDecline(val status: DueStatus, val ratio: Double)

/**
 * Times cabin cool-downs (or, with [heating], warm-ups) and tracks their speed against the first few after a service.
 * A low refrigerant charge reduces A/C performance (Hyundai NX4 owner's manual, system maintenance), so a slowing trend in
 * similar outside temperatures is the observable sign. A session starts when the cabin is at least [START_GAP] on the
 * far side of the set point while the system runs.
 *
 * Heating mirrors the OBD II thermostat monitor (13 CCR 1968.2 (e)(10)): a cold engine must warm its coolant to near the
 * regulating temperature within a set time after start, and a monitor does not run when the engine starts warm. The app
 * cannot read coolant, so it times the cabin, and the caller only lets a warm-up start right after a long stop.
 */
class ClimateTracker(val sessions: MutableList<ClimateSession> = mutableListOf(), val marks: MutableMap<String, Int> = mutableMapOf(),
                     val heating: Boolean = false, private val heatProbe: Double? = null,
                     /** Compare warm-ups only within 3-6, 6-12 and 12+ parked hours; off, kept for the simulation that rejected it. */
                     private val soakBands: Boolean = false) {
    private var startMs: Long? = null
    private var startC = 0.0
    private var setC = 0.0
    private var outsideC: Double? = null
    private var closed: Double? = null
    private val fans = mutableListOf<Double>()
    private val intakes = mutableListOf<Boolean>()
    private val speeds = mutableListOf<Double>()
    private val sign get() = if (heating) -1.0 else 1.0
    /** An EV heats from the first minute (4-5 kW on a cold start), so its warm-up is timed over [EV_HEAT_PROBE_MINUTES]. */
    val probeMinutes get() = if (heating) heatProbe ?: (if (CarModel.current.powertrain == Powertrain.ELECTRIC) EV_HEAT_PROBE_MINUTES else HEAT_PROBE_MINUTES) else PROBE_MINUTES
    private var soakH: Double? = null
    private val windowMinutes get() = if (heating) probeMinutes + 2 else WINDOW_MINUTES

    /**
     * Feeds the latest state; returns a finished session when one completes. [fanShare] (blower step over its maximum),
     * [recirc] and [speedKph] are sampled until the probe closes, so a session can later be compared with ones run the
     * same way. A session opens only while [canStart].
     */
    fun update(running: Boolean, cabinC: Double?, setPointC: Double?, outside: Double?, nowMs: Long,
               fanShare: Double? = null, recirc: Boolean? = null, speedKph: Double? = null, canStart: Boolean = true, parkedHours: Double? = null): ClimateSession? {
        val start = startMs
        if (start == null) {
            if (canStart && running && cabinC != null && setPointC != null && sign * (cabinC - setPointC) >= START_GAP) {
                startMs = nowMs; startC = cabinC; setC = setPointC; outsideC = outside; closed = null; fans.clear(); intakes.clear(); speeds.clear(); soakH = parkedHours
                fanShare?.let(fans::add); recirc?.let(intakes::add); speedKph?.let(speeds::add)
            }
            return null
        }
        if (!running || cabinC == null) { startMs = null; return null }
        val minutes = (nowMs - start) / 60_000.0
        if (closed == null) { fanShare?.let(fans::add); recirc?.let(intakes::add); speedKph?.let(speeds::add) }
        val near = sign * (cabinC - setC) <= 0.5
        if (closed == null && (minutes >= probeMinutes || near)) closed = (startC - cabinC) / (startC - setC)
        val reached = near && minutes >= MIN_MINUTES
        if (minutes < windowMinutes && !reached) return null
        startMs = null
        return ClimateSession(System.currentTimeMillis(), sign * (startC - cabinC) / minutes, startC, setC, outsideC, closed,
            fans.takeIf { it.isNotEmpty() }?.average(), intakes.takeIf { it.isNotEmpty() }?.let { i -> i.count { it } * 2 > i.size },
            speeds.takeIf { it.isNotEmpty() }?.average(), soakH)
            .also { add(it) }
    }
    /** Drops an open session; time the app did not see cannot be timed. */
    fun abort() { startMs = null }

    fun add(c: ClimateSession) {
        sessions += c
        if (sessions.size > MAX) { val drop = sessions.size - MAX; repeat(drop) { sessions.removeAt(0) }; marks.replaceAll { _, v -> (v - drop).coerceAtLeast(0) } }
    }
    fun markBaseline(recordId: String) { marks[recordId] = sessions.size }

    /**
     * Each later session in a similar outside temperature, with the reference sessions run like it (at least [MIN_MATCH]).
     * Blower step and intake change how fast a cabin cools as much as a partial charge loss does, so a session is only
     * held against reference sessions with the same intake and a blower share within [FAN_MATCH].
     */
    private fun compared(recordId: String?): List<Pair<Double, List<Double>>>? {
        // A warm-up mostly idling says more about idle heat than the heater (Ford holds its monitor past 50 % idle).
        // A pull-down started parked has no ram air on the condenser and runs about two thirds as fast (KATECH 2011). Matching
        // by speed instead thinned the reference so much that chance alone raised false alarms (CooldownSimulationTest), so
        // those pull-downs are left out and the rest compared as before.
        val list = sessions.drop(marks[recordId] ?: 0).filter { it.closed != null && !(it.speed != null && it.speed < (if (heating) SLOW_KPH else PARKED_KPH)) }
        if (list.size <= BASELINE) return null
        val base = list.take(BASELINE)
        val baseOut = base.mapNotNull { it.outsideC }.takeIf { it.isNotEmpty() }?.average()
        return list.drop(BASELINE)
            .filter { baseOut == null || it.outsideC == null || abs(it.outsideC - baseOut) <= SIMILAR_OUTSIDE }
            .mapNotNull { s -> base.filter { it.alike(s, bySpeed = heating) && (!soakBands || band(it.soakH) == band(s.soakH)) }.takeIf { it.size >= MIN_MATCH }?.let { m -> s.closed!! to m.map { it.closed!! } } }
    }

    /**
     * Hyundai's manual ties low refrigerant to reduced A/C performance. DUE when the last two comparable sessions closed
     * under [LOST_SHARE] of their reference median (the system barely cools, as a fully lost charge does); SOON when each
     * of the last [RECENT] closed less than every reference session run like it. Sizes come from CooldownSimulationTest.
     */
    fun decline(recordId: String?, soon: Boolean = true): CoolingDecline? {
        val c = compared(recordId)?.filter { (_, ref) -> median(ref) > 0 } ?: return null
        if (c.size < 2) return null
        val last2 = c.takeLast(2)
        if (last2.all { (v, ref) -> v < LOST_SHARE * median(ref) }) return CoolingDecline(DueStatus.DUE, last2.map { (v, ref) -> v / median(ref) }.average())
        val recent = c.takeLast(RECENT)
        if (soon && recent.size >= RECENT && recent.all { (v, ref) -> v < ref.min() })
            return CoolingDecline(DueStatus.SOON, median(recent.map { (v, ref) -> v / median(ref) }))
        return null
    }

    /** Recent cooling against the reference (median ratio over the latest [RECENT] comparable sessions), and sessions with a share. */
    fun recovery(recordId: String?): Pair<Double?, Int> {
        val count = sessions.drop(marks[recordId] ?: 0).count { it.closed != null }
        val c = compared(recordId)?.filter { (_, ref) -> median(ref) > 0 }?.takeLast(RECENT)?.takeIf { it.isNotEmpty() } ?: return null to count
        return median(c.map { (v, ref) -> v / median(ref) }) to count
    }

    /** Latest speed, change against the reference in percent (null until enough sessions), and sessions counted. */
    fun summary(recordId: String?): Triple<Double?, Double?, Int> {
        val list = sessions.drop(marks[recordId] ?: 0)
        val latest = list.lastOrNull()?.ratePerMin
        if (list.size <= BASELINE) return Triple(latest, null, list.size)
        val base = list.take(BASELINE)
        val baseOut = base.mapNotNull { it.outsideC }.takeIf { it.isNotEmpty() }?.average()
        // Compare like with like: only later sessions in a similar outside temperature.
        val similar = list.drop(BASELINE).filter { baseOut == null || it.outsideC == null || abs(it.outsideC - baseOut) <= SIMILAR_OUTSIDE }
        if (similar.isEmpty()) return Triple(latest, null, list.size)
        val change = (similar.takeLast(BASELINE).map { it.ratePerMin }.average() / base.map { it.ratePerMin }.average() - 1) * 100
        return Triple(latest, change, list.size)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("sessions", JSONArray().apply { sessions.forEach { s -> put(JSONObject().put("at", s.atMs).put("rate", s.ratePerMin).put("start", s.startC).put("set", s.setC).apply { s.outsideC?.let { put("out", it) }; s.closed?.let { put("cl", it) }; s.fan?.let { put("fan", it) }; s.recirc?.let { put("rc", it) }; s.speed?.let { put("sp", it) }; s.soakH?.let { put("soak", it) } }) } })
        .put("marks", JSONObject(marks as Map<*, *>))

    companion object {
        const val START_GAP = 3.0
        const val WINDOW_MINUTES = 5.0
        const val MIN_MINUTES = 2.0
        const val BASELINE = 8
        const val RECENT = 6
        const val PROBE_MINUTES = 3.0
        const val LOST_SHARE = 0.25
        const val SIMILAR_OUTSIDE = 5.0
        const val FAN_MATCH = 0.2
        const val SPEED_MATCH = 20.0
        const val HEAT_PROBE_MINUTES = 8.0
        const val EV_HEAT_PROBE_MINUTES = 4.0
        const val SLOW_KPH = 10.0
        const val PARKED_KPH = 5.0
        private fun band(h: Double?) = when { h == null -> -1; h < 6 -> 0; h < 12 -> 1; else -> 2 }
        const val MIN_MATCH = 3
        // About three summers; a shorter list would trim the post-check reference away and hide a decline.
        const val MAX = 400
        private fun median(v: List<Double>) = v.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
        fun fromJson(o: JSONObject, heating: Boolean = false) = ClimateTracker(
            mutableListOf<ClimateSession>().apply {
                o.optJSONArray("sessions")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { s ->
                    add(ClimateSession(s.getLong("at"), s.getDouble("rate"), s.getDouble("start"), s.getDouble("set"), if (s.has("out")) s.getDouble("out") else null,
                        if (s.has("cl")) s.getDouble("cl") else null, if (s.has("fan")) s.getDouble("fan") else null, if (s.has("rc")) s.getBoolean("rc") else null,
                        if (s.has("sp")) s.getDouble("sp") else null, if (s.has("soak")) s.getDouble("soak") else null))
                } }
            },
            mutableMapOf<String, Int>().apply { o.optJSONObject("marks")?.let { m -> m.keys().forEach { put(it, m.getInt(it)) } } }, heating)
    }
}
