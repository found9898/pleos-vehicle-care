package dev.vehiclecare.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * Wipe counts from switch position and time. The blade's age, not its wipe count, sets its life: the Korea Consumer
 * Agency's 200,000 wet double strokes (RS R 0031:2003, "about a year or 20,000 km" in a footnote with no basis given)
 * left 8 of 10 blades still good, and a typical Korean driver takes about 3.8 years to wipe that many (about 52,000 a
 * year from airport rain hours and daily driving; DENSO counts 100,000 as two years). So wiping counts only where it
 * runs ahead of that typical pace: each 200,000 wipes beyond it is one more year of blade age.
 */
object WiperModel {
    const val CYCLE_BUDGET = 200_000.0
    const val TYPICAL_PER_YEAR = 52_000.0
    /** Below freezing a stroke may scrape ice or a dry screen: counted twice, on the upper estimate only (a guess, Ford's examples run 2-10). */
    const val FREEZE_FACTOR = 2.0
    /** The manual asks for the wipers to run about twice a week; two weeks unused is worth a word (the 14 days is a guess). */
    const val IDLE_DAYS = 14L
    /** MIST runs the low speed while held, so it counts as LOW. */
    enum class Level { LOW, HIGH, INTERMITTENT, AUTO, UNKNOWN }
    /**
     * Cycles per minute [low, high]. Continuous speeds from a measured motor (Saab/Trico 45 ± 6 and 71 ± 8 on a wet screen)
     * inside FMVSS 104 and EU 1008/2010. No rule or manual gives intermittent pauses: one wipe every 2 to 20 s is assumed.
     * AUTO follows the rain from a single wipe up to high speed.
     */
    fun cpm(level: Level) = when (level) {
        Level.LOW -> 39.0 to 55.0
        Level.HIGH -> 57.0 to 79.0
        Level.INTERMITTENT -> 3.0 to 30.0
        Level.AUTO -> 10.0 to 75.0
        Level.UNKNOWN -> 3.0 to 79.0
    }
    /** Days of blade age added by wiping beyond the typical pace over [days] on the car. */
    fun excessDays(cycles: Double, days: Double) = maxOf(0.0, cycles - TYPICAL_PER_YEAR * days / 365) / CYCLE_BUDGET * 365
}

/** Wipe cycles seen while visible, as a low and high bound, and when the wipers last ran (wall clock). */
class WiperCycles(var low: Double = 0.0, var high: Double = 0.0, val baselines: MutableMap<String, Pair<Double, Double>> = mutableMapOf(),
                  var lastWipedAt: Long? = null) {
    private var since: Long? = null
    private var level = WiperModel.Level.UNKNOWN
    var outsideC: Double? = null

    /** Folds the running interval at the previous level, then continues at [next] (null stops counting). */
    fun update(next: WiperModel.Level?, nowMs: Long, wallMs: Long = System.currentTimeMillis()) {
        since?.let { start ->
            val minutes = (nowMs - start).coerceAtLeast(0) / 60_000.0
            val (a, b) = WiperModel.cpm(level)
            val ice = if ((outsideC ?: 10.0) <= 0.0) WiperModel.FREEZE_FACTOR else 1.0
            low += a * minutes; high += b * minutes * ice
            if (minutes > 0) lastWipedAt = wallMs
        }
        if (next != null) lastWipedAt = wallMs
        since = next?.let { nowMs }; next?.let { level = it }
    }
    fun markBaseline(recordId: String) { baselines[recordId] = low to high }
    fun since(recordId: String?): Triple<Double, Double, Boolean> = baselines[recordId]
        ?.let { (a, b) -> Triple((low - a).coerceAtLeast(0.0), (high - b).coerceAtLeast(0.0), true) } ?: Triple(low, high, false)
    fun toJson(): JSONObject = JSONObject().put("low", low).put("high", high).apply { lastWipedAt?.let { put("last", it) } }
        .put("baselines", JSONObject().apply { baselines.forEach { (id, v) -> put(id, JSONObject().put("low", v.first).put("high", v.second)) } })
    companion object {
        fun fromJson(o: JSONObject) = WiperCycles(o.optDouble("low", 0.0), o.optDouble("high", 0.0), mutableMapOf<String, Pair<Double, Double>>().apply {
            o.optJSONObject("baselines")?.let { b -> b.keys().forEach { id -> b.getJSONObject(id).let { put(id, it.getDouble("low") to it.getDouble("high")) } } }
        }, if (o.has("last")) o.getLong("last") else null)
    }
}

/**
 * Wiper noise in 1-10 kHz, after Ford's US10308225B2: worn blades are louder across that band, with squeal near 2.5 kHz.
 * Each analysis frame gives band powers. Over one wiping session, the 90th percentile (strokes) minus the 10th
 * (the cabin floor between them) is the wiper's excess noise, which cancels microphone gain and steady road noise.
 * Audio is never stored; only these figures are.
 */
object WiperAcoustics {
    val BANDS = listOf(1_000.0 to 2_000.0, 2_000.0 to 4_000.0, 4_000.0 to 8_000.0, 8_000.0 to 10_000.0)
    const val FRAME = 2048
    const val MIN_FRAMES = 200   // about 9 s at 44.1 kHz; shorter sessions are dropped

    private val window = DoubleArray(FRAME) { 0.5 - 0.5 * cos(2 * PI * it / (FRAME - 1)) }

    /** Power in each band plus the whole 1-10 kHz span, in dB (arbitrary reference). */
    fun bandDb(samples: ShortArray, rate: Int): DoubleArray {
        val re = DoubleArray(FRAME) { if (it < samples.size) samples[it] * window[it] else 0.0 }
        val im = DoubleArray(FRAME)
        fft(re, im)
        val hz = rate.toDouble() / FRAME
        val sums = DoubleArray(BANDS.size + 1)
        for (k in 1 until FRAME / 2) {
            val f = k * hz; val p = re[k] * re[k] + im[k] * im[k]
            BANDS.forEachIndexed { i, (lo, hi) -> if (f >= lo && f < hi) sums[i] += p }
            if (f >= 1_000.0 && f < 10_000.0) sums[BANDS.size] += p
        }
        return DoubleArray(sums.size) { 10 * log10(sums[it] + 1e-9) }
    }

    /** Excess noise per band for one session: p90 minus p10 of frame levels. */
    fun excess(frames: List<DoubleArray>): DoubleArray? {
        if (frames.size < MIN_FRAMES) return null
        return DoubleArray(frames[0].size) { b ->
            val v = frames.map { it[b] }.sorted()
            v[(v.size * 0.9).toInt().coerceAtMost(v.size - 1)] - v[(v.size * 0.1).toInt()]
        }
    }

    /** In-place iterative radix-2 FFT. */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            for (i in 0 until n step len) for (k in 0 until len / 2) {
                val wr = cos(ang * k); val wi = sin(ang * k)
                val ur = re[i + k]; val ui = im[i + k]
                val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                re[i + k] = ur + vr; im[i + k] = ui + vi; re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
            }
            len = len shl 1
        }
    }
}

/** One analysed wiping session, with the switch level it ran at; strokes at another rate are not compared with it. */
data class SoundSession(val atMs: Long, val seconds: Int, val excessDb: DoubleArray, val level: String = WiperModel.Level.UNKNOWN.name)

/**
 * A sustained rise in wiping noise at one switch level, against the sessions right after the blade change. [confirmed]
 * when it was still there in the first sessions after the glass and blade were cleaned.
 */
data class SoundShift(val level: String, val db: Double, val sessions: Int, val confirmed: Boolean = false)

/**
 * Sessions in order; the first [BASELINE] after a replacement set the reference the latest ones are compared with. The
 * first [warmup] sessions on a new blade are left out of it: dust and dirt make new blades noisy (Ford US10068388 drops
 * the first cycles for the same reason).
 */
class WiperSound(val sessions: MutableList<SoundSession> = mutableListOf(), val marks: MutableMap<String, Int> = mutableMapOf(),
                 private val warmup: Int = WARMUP) {
    private fun start(recordId: String?) = (marks[recordId] ?: 0) + warmup
    fun add(s: SoundSession) {
        sessions += s
        if (sessions.size > MAX) { val drop = sessions.size - MAX; repeat(drop) { sessions.removeAt(0) }; marks.replaceAll { _, v -> (v - drop).coerceAtLeast(0) } }
    }
    fun markBaseline(recordId: String) { marks[recordId] = sessions.size }
    /**
     * The owner cleaned the glass and blade after a rise at [level] was shown; that level's sessions from here decide what
     * the rise was. Only the level that rose can confirm it, so a quieter level's chance run cannot.
     */
    fun markCleaned(recordId: String, level: String) {
        marks[CLEAN + recordId] = sessions.size
        marks[CLEAN_LEVEL + recordId] = WiperModel.Level.entries.firstOrNull { it.name == level }?.ordinal ?: -1
    }
    private fun cleanedLevel(recordId: String) = marks[CLEAN_LEVEL + recordId]?.let { WiperModel.Level.entries.getOrNull(it)?.name }

    /**
     * The Hyundai manual sends blades that stay noisy after cleaning to inspection. A rise counts only when every one of the
     * latest [RECENT] sessions at a switch level is louder than every one of the first [BASELINE] at that level. Checked after
     * every session, 8 and 6 kept false alarms near 2-3 % over two simulated rainy seasons while catching a +6 dB rise
     * in over 90 % (WiperSoundSimulationTest); 5 and 5 raised false alarms to about 20 %.
     *
     * Film, insects or ice on the glass make the same noise as a worn edge. After [markCleaned], the first [CONFIRM]
     * sessions at that level decide: all still louder than the reference confirms the blade; otherwise the rise went
     * with the dirt and only sessions after the cleaning can raise it again.
     */
    fun shift(recordId: String?): SoundShift? {
        val from = start(recordId)
        val clean = recordId?.let { marks[CLEAN + it] }?.takeIf { it >= from }
        val total = WiperAcoustics.BANDS.size
        return sessions.withIndex().drop(from).groupBy { it.value.level }.mapNotNull { (level, group) ->
            if (group.size < BASELINE) return@mapNotNull null
            val base = group.take(BASELINE).map { it.value.excessDb[total] }
            val later = group.drop(BASELINE).filter { clean == null || it.index >= clean }.map { it.value.excessDb[total] }
            if (clean != null && level == cleanedLevel(recordId!!)) {
                val first = later.take(CONFIRM)
                if (first.size == CONFIRM && first.min() > base.max()) return@mapNotNull SoundShift(level, median(first) - median(base), group.size, true)
            }
            if (later.size < RECENT) return@mapNotNull null
            val recent = later.takeLast(RECENT)
            if (recent.min() <= base.max()) null else SoundShift(level, median(recent) - median(base), group.size)
        }.maxWithOrNull(compareBy({ it.confirmed }, { it.db }))
    }

    /**
     * How far the rise rule has got: of the latest [RECENT] sessions at the busiest switch level, how many are louder than
     * every reference session at that level. Null until a level has its reference.
     */
    fun louder(recordId: String?): Int? {
        val from = start(recordId)
        val total = WiperAcoustics.BANDS.size
        return sessions.drop(from).groupBy { it.level }.values.filter { it.size > BASELINE }.maxByOrNull { it.size }?.let { group ->
            val baseMax = group.take(BASELINE).maxOf { it.excessDb[total] }
            group.drop(BASELINE).takeLast(RECENT).count { it.excessDb[total] > baseMax }
        }
    }

    /** Sessions at the cleaned level since the cleaning while it is still undecided; null without a cleaning or once decided. */
    fun sinceCleaning(recordId: String?): Int? {
        val from = start(recordId)
        val clean = recordId?.let { marks[CLEAN + it] }?.takeIf { it >= from } ?: return null
        val level = cleanedLevel(recordId)
        val seen = sessions.withIndex().drop(from).filter { it.value.level == level }.drop(BASELINE).count { it.index >= clean }
        return if (seen >= CONFIRM) null else seen
    }
    private fun median(v: List<Double>) = v.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

    /** Change of the whole-band excess since the reference, or how many reference sessions are still missing. */
    fun change(recordId: String?): Pair<Double?, Int> {
        val list = sessions.drop(start(recordId))
        if (list.size <= BASELINE) return null to list.size
        val total = WiperAcoustics.BANDS.size
        val base = list.take(BASELINE).map { it.excessDb[total] }.average()
        val now = list.drop(BASELINE).takeLast(RECENT).map { it.excessDb[total] }.average()
        return (now - base) to list.size
    }
    fun toJson(): JSONObject = JSONObject()
        .put("sessions", JSONArray().apply { sessions.forEach { s -> put(JSONObject().put("at", s.atMs).put("s", s.seconds).put("lv", s.level).put("db", JSONArray(s.excessDb.map { Math.round(it * 10) / 10.0 }))) } })
        .put("marks", JSONObject(marks as Map<*, *>))
    companion object {
        const val BASELINE = 8
        const val WARMUP = 2
        const val RECENT = 6
        const val CONFIRM = 3
        const val CLEAN = "clean:"
        const val CLEAN_LEVEL = "clean-level:"
        const val MAX = 300
        fun fromJson(o: JSONObject) = WiperSound(
            mutableListOf<SoundSession>().apply {
                o.optJSONArray("sessions")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { s ->
                    val db = s.getJSONArray("db"); add(SoundSession(s.getLong("at"), s.getInt("s"), DoubleArray(db.length()) { db.getDouble(it) }, s.optString("lv", WiperModel.Level.UNKNOWN.name)))
                } }
            },
            mutableMapOf<String, Int>().apply { o.optJSONObject("marks")?.let { m -> m.keys().forEach { put(it, m.getInt(it)) } } })
    }
}

/** Outside temperature seen while collecting, as a sum and count per date; the newest [MAX_DAYS] dates are kept. */
/** One day's outside readings as their mean offset from that hour's normal, and the warmest sun-soaked cabin at a trip start. */
data class DayHeat(val offsetSum: Double = 0.0, val n: Int = 0, val soakC: Double? = null) {
    val offset: Double? get() = if (n > 0) offsetSum / n else null
}

/**
 * Outside temperature on the days the car was used, kept by date as the offset from the normal temperature at the hour
 * it was read. Readings come while driving, so comparing an 8 a.m. reading with the afternoon maximum would read every
 * commute as a cool day.
 */
class OutsideDays(val days: MutableMap<String, DayHeat> = sortedMapOf()) {
    fun add(at: java.time.LocalDateTime, celsius: Double) {
        if (celsius !in -40.0..60.0) return
        val d = days[at.toLocalDate().toString()] ?: DayHeat()
        days[at.toLocalDate().toString()] = d.copy(offsetSum = d.offsetSum + celsius - RubberAging.normalAt(at.monthValue, at.hour + at.minute / 60.0), n = d.n + 1)
        trim()
    }
    /**
     * A trip start after the car stood [RubberAging.SOAK_PARK_MINUTES] or more: a cabin [RubberAging.SOAK_GAP] or more above
     * outside air means it stood in the sun.
     */
    fun soak(date: java.time.LocalDate, cabinC: Double, outsideC: Double) {
        if (cabinC !in -40.0..90.0 || cabinC - outsideC < RubberAging.SOAK_GAP) return
        val d = days[date.toString()] ?: DayHeat()
        days[date.toString()] = d.copy(soakC = maxOf(d.soakC ?: cabinC, cabinC))
        trim()
    }
    fun day(date: java.time.LocalDate): DayHeat? = days[date.toString()]
    private fun trim() { while (days.size > MAX_DAYS) days.remove(days.keys.minOrNull()) }
    fun toJson(): JSONObject = JSONObject().apply { days.forEach { (d, v) -> put(d, JSONArray().put(v.offsetSum).put(v.n).apply { v.soakC?.let { put(it) } }) } }
    companion object {
        const val MAX_DAYS = 900
        fun fromJson(o: JSONObject) = OutsideDays(sortedMapOf<String, DayHeat>().apply {
            o.keys().forEach { k -> o.optJSONArray(k)?.let { put(k, DayHeat(it.getDouble(0), it.getInt(1), if (it.length() > 2) it.getDouble(2) else null)) } }
        })
    }
}

/**
 * Age of the blade rubber in normal-year days. The 12-month replacement (Bosch, DENSO, Valeo; Hyundai's Korean manuals
 * give none, the US Sonata manual says check every 6 months) already spans a normal Korean year with its ozone, sun and
 * seasons, so every day counts as one and only the car's own measurements move it:
 * - heat, weighted by Arrhenius. Air ageing of blade-type rubbers runs 74-90 kJ/mol for chloroprene (Bouaziz et al.,
 *   Polymers 12, 2354, 2020) and 90 for natural rubber (Mott & Roland 2001); EPDM fell to 51 kJ/mol below 100 °C (RSC Adv
 *   2024). Status uses 50, which moves the result least. A day read warmer than the normal at the hours it was read
 *   shifts that day's normal curve (KMA 1991-2020 monthly mean minimum at 06:00, maximum at 15:00, two half-cosines).
 * - sun: a trip that starts with the cabin [SOAK_GAP] or more over outside air after standing means the car stood in
 *   the sun. Blades on cars parked in the shade last 2-3 times longer (Champion); with sun and ozone cracking named as
 *   the main field failure in Bosch, Mitsuba and Denso patents, such a day counts [SUN_DAY] times. The cabin
 *   temperature itself is not used: the blade sits outside in the wind, and dashboard-level heat at 80 kJ/mol would turn
 *   one summer month into more than a year.
 * Weights are divided by the normal year's average.
 */
object RubberAging {
    const val EA_LOW = 50_000.0
    const val EA_HIGH = 90_000.0
    const val SOAK_GAP = 5.0
    /** A sun-soaked day ages the blade this much: 1.4, between all-sun lasting a half and a third of all-shade (a guess on Champion's 2-3x). */
    const val SUN_DAY = 1.4
    const val SOAK_PARK_MINUTES = 30L
    private const val GAS = 8.314
    /** KMA national normal monthly mean daily maximum and minimum, 1991-2020, January first. */
    val NORMAL_MAX = doubleArrayOf(4.6, 7.2, 12.4, 18.7, 23.6, 26.8, 29.1, 29.9, 26.1, 20.9, 13.8, 6.8)
    val NORMAL_MIN = doubleArrayOf(-5.2, -3.5, 0.8, 6.3, 11.9, 17.1, 21.4, 21.8, 16.4, 9.3, 2.9, -3.1)
    private val DAYS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    fun weight(celsius: Double, ea: Double) = kotlin.math.exp(ea / GAS * (1 / 293.15 - 1 / (celsius + 273.15)))

    /** Normal temperature at [hour] (0-24) in [month] (1-12): minimum at 06:00, maximum at 15:00. */
    fun normalAt(month: Int, hour: Double): Double {
        val lo = NORMAL_MIN[month - 1]; val hi = NORMAL_MAX[month - 1]
        val h = ((hour % 24) + 24) % 24
        return if (h in 6.0..15.0) lo + (hi - lo) * (1 - kotlin.math.cos(Math.PI * (h - 6) / 9)) / 2
        else hi - (hi - lo) * (1 - kotlin.math.cos(Math.PI * (((h - 15) + 24) % 24) / 15)) / 2
    }
    /** Mean ageing weight of a normal day in [month] with every hour shifted by [shift] °C. */
    private fun dayWeight(month: Int, shift: Double, ea: Double) = (0 until 48).sumOf { weight(normalAt(month, it / 2.0 + .25) + shift, ea) } / 48
    private fun normal(ea: Double) = (1..12).sumOf { DAYS[it - 1] * dayWeight(it, 0.0, ea) } / 365

    /** Normal-year days of ageing between the two dates, at activation energy [ea]. */
    fun equivalentDays(from: java.time.LocalDate, today: java.time.LocalDate, seen: OutsideDays, ea: Double): Double {
        if (!from.isBefore(today)) return 0.0
        val norm = normal(ea)
        val plain = DoubleArray(12) { dayWeight(it + 1, 0.0, ea) }
        var d = from; var total = 0.0
        while (d.isBefore(today)) {
            val m = d.monthValue
            total += 1
            seen.day(d)?.let { h ->
                h.offset?.let { total += (dayWeight(m, it, ea) - plain[m - 1]) / norm }
                if (h.soakC != null) total += SUN_DAY - 1
            }
            d = d.plusDays(1)
        }
        return total
    }
}
