package dev.vehiclecare.app

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * A Tucson's last ten months, made up to show each kind of judgment once. Every date counts back from the day the story
 * is loaded, while the climate figures and the weekly trips follow a fixed story calendar, so the distances on screen
 * read the same whichever day it is loaded (only the calendar's own day counts can differ by a day or so): one part due now (the brakes, by braking energy before the odometer gets there), two soon (the oil from short
 * trips, the wiper from its noise) and three with room left. The odometer climbs with the trips, so a later date never
 * has a smaller reading. The figures follow the app's own models; they are not measurements.
 */
object DemoData {
    const val ODOMETER = 38_420L
    /** The car's pace in this story, km a day: the weekly trips below (205.5 km a week). */
    private const val KM_PER_DAY = 205.5 / 7
    /** The story's own calendar ends here, so the seasons behind the oil and the filter do not move with the load date. */
    private val STORY_END: LocalDate = LocalDate.of(2026, 11, 15)
    /** The story always ends on a Friday: a real weekday would reshuffle the trips and move the oil's distance by 30 km. */
    private fun weekday(daysAgo: Int): DayOfWeek = DayOfWeek.FRIDAY.minus(daysAgo.toLong())

    class Story(
        val today: LocalDate, val odometer: Long, val records: List<ServiceRecord>, val counters: Map<Usage, UsageCounter>,
        val filter: FilterDust, val brake: BrakeWork, val wiper: WiperCycles, val sound: WiperSound, val cooldown: ClimateTracker,
        val heatup: ClimateTracker, val oil: OilTrips, val tire: TireLoad, val outside: OutsideDays,
    ) {
        fun snapshot() = SignalSnapshot(oil, brake, wiper, sound, cooldown, counters.getValue(Usage.FRESH), tire = tire, outside = outside, heatup = heatup, filter = filter)
    }

    fun build(today: LocalDate, carOdometer: Long = 0): Story {
        // The Virtual Console never lets the odometer go back, so a second load tells the same story at the car's reading.
        val odometer = maxOf(ODOMETER, carOdometer)
        val ago = { days: Int -> today.minusDays(days.toLong()) }
        val km = { days: Int -> odometer - Math.round(days * KM_PER_DAY) }
        val month = { date: LocalDate -> STORY_END.minusDays(java.time.temporal.ChronoUnit.DAYS.between(date, today)).monthValue }
        val id = { p: Part -> "demo-${p.name}" }

        // Days back from today. Oil: changed 225 days ago, about 6,600 km. Brake pads: new 238 days ago, about 7,000 km.
        // Wiper blades: 120 days. Cabin filter: 140 days of a 12-month interval. A/C check: 300 days. Tires: a set 940
        // days ago, rotated 170 days ago, checked 9 days ago.
        val dates = mapOf(Part.ENGINE_OIL to 225, Part.BRAKE_PAD to 238, Part.WIPER to 120, Part.CABIN_FILTER to 140, Part.HVAC to 300, Part.TIRE to 9)
        val records = listOf(
            ServiceRecord("demo-TIRE-set", Part.TIRE, ago(940), km(940), ServiceAction.REPLACE),
            ServiceRecord("demo-TIRE-rotation", Part.TIRE, ago(170), km(170), ServiceAction.ROTATE),
        ) + dates.map { (part, days) ->
            // The brake record is a pad change, so the pad thickness is counted from new.
            ServiceRecord(id(part), part, ago(days), km(days), if (part == Part.BRAKE_PAD) ServiceAction.REPLACE else part.defaultAction)
        }

        // Outside air through the filter: an hour and a half a day at a middle blower step, weighed by the story month's dust.
        val filter = FilterDust(0.0, mutableMapOf(id(Part.CABIN_FILTER) to 0.0))
        val freshHours = 1.5
        for (d in dates.getValue(Part.CABIN_FILTER) downTo 1) filter.hours += freshHours * FilterModel.weight(FilterModel.flow(0.5), month(ago(d)))
        val counters = mapOf(
            Usage.WIPER to 34_000L,                                               // about nine and a half hours since the blades
            Usage.BRAKE to 13_800L,
            Usage.AC to 410_000L,
            Usage.BLOWER to 1_520_000L,
            Usage.FRESH to Math.round(dates.getValue(Part.CABIN_FILTER) * freshHours * 3600),
            Usage.RECIRC to 480_000L,
        ).mapValues { (u, total) -> UsageCounter(total, null, mutableMapOf(id(u.part) to 0L)) }

        // 7,000 km on the new pads at 1.48 times this Tucson's typical braking per km (a hilly commute): the brake energy
        // reaches the 10,000 km inspection before the odometer does.
        val brakeKm = km(0) - km(dates.getValue(Part.BRAKE_PAD))
        val brakeJ = brakeKm * 1.48 * CompositeJudge.brakeReferenceKjPerKm() * 1_000
        val brake = BrakeWork(brakeJ, brakeKm * 1_000.0, mutableMapOf(id(Part.BRAKE_PAD) to BrakeTotals()), wearJ = brakeJ, wearHighJ = brakeJ)

        // Wiping a little above the typical pace since the blades went on; last used three days ago.
        val bladeDays = dates.getValue(Part.WIPER)
        val wipes = WiperModel.TYPICAL_PER_YEAR * bladeDays / 365 * 1.04
        val wiper = WiperCycles(wipes, wipes * 1.6, mutableMapOf(id(Part.WIPER) to (0.0 to 0.0)), millis(ago(3)))

        // Low-speed sessions: two dusty ones on the new blades, eight settling near 12 dB, then the latest six all above
        // them: the cue to clean the glass and blades first.
        val sound = WiperSound()
        val settled = listOf(15.2, 14.1, 12.1, 11.8, 12.4, 11.6, 12.9, 12.2, 11.9, 12.6)
        val lately = listOf(14.8, 15.6, 13.4, 16.1, 15.2, 15.9)
        (settled + lately).forEachIndexed { i, db -> sound.add(SoundSession(millis(ago(bladeDays - 4 - i * 7)), 240, doubleArrayOf(0.0, db, 0.0, 0.0, db), "LOW")) }
        sound.add(SoundSession(millis(ago(60)), 180, doubleArrayOf(0.0, 10.4, 0.0, 0.0, 10.4), "INTERMITTENT"))
        sound.marks[id(Part.WIPER)] = 0

        // Eight summer pull-downs set the cooling reference and the later ones match it: the A/C is as it was.
        val cooldown = ClimateTracker()
        val summer = listOf(.56 to 28.0, .61 to 29.0, .53 to 27.0, .58 to 30.0, .55 to 28.0, .6 to 31.0, .57 to 29.0, .54 to 28.0)
        val after = listOf(.57 to 30.0, .55 to 29.0, .59 to 31.0, .56 to 28.0, .58 to 30.0, .55 to 29.0)
        summer.forEachIndexed { i, (share, out) -> cooldown.add(ClimateSession(millis(ago(160 - i * 3)), share * 12 / 3, 34.0, 22.0, out, share)) }
        after.forEachIndexed { i, (share, out) -> cooldown.add(ClimateSession(millis(ago(100 - i * 6)), share * 12 / 3, 34.0, 22.0, out, share)) }
        cooldown.marks[id(Part.HVAC)] = 0

        // Cold starts after the check set the heating reference; three chilly recent mornings warm up as before.
        val heat = ClimateTracker(heating = true)
        val winter = listOf(Triple(.62, -3.0, 42.0), Triple(.58, -1.0, 38.0), Triple(.66, 1.0, 51.0), Triple(.6, -4.0, 45.0),
            Triple(.64, 2.0, 40.0), Triple(.57, 0.0, 36.0), Triple(.63, 3.0, 47.0), Triple(.61, -2.0, 43.0))
        val recent = listOf(Triple(.6, 4.0, 41.0), Triple(.62, 3.0, 44.0), Triple(.59, 5.0, 39.0))
        winter.forEachIndexed { i, (share, out, kph) -> heat.add(ClimateSession(millis(ago(295 - i * 8)), share * 18 / 8, out + 2, 22.0, out, share, 1.0, false, kph)) }
        recent.forEachIndexed { i, (share, out, kph) -> heat.add(ClimateSession(millis(ago(12 - i * 3)), share * 18 / 8, out + 2, 22.0, out, share, 1.0, false, kph)) }
        heat.marks[id(Part.HVAC)] = 0

        // Weekday commutes, a school run twice a week, a weekend shop and a Sunday drive, from the oil change to yesterday.
        val oil = OilTrips()
        for (d in dates.getValue(Part.ENGINE_OIL) downTo 1) {
            val day = ago(d)
            val outside = when (month(day)) { 12, 1, 2 -> 1.0; 3, 11 -> 9.0; 4, 10 -> 15.0; 5, 9 -> 20.0; 6 -> 24.0; else -> 28.0 } + (d % 5 - 2)
            val at = millis(day)
            val h = 3_600_000L   // trips are timed from 08:00
            // Each trip runs through the same speed-sample path as a real drive, so the oil temperature model sees it.
            val weekday = weekday(d)
            when (weekday) {
                DayOfWeek.SATURDAY -> { oil.drive(at + 2 * h, 3.4, 10.0, 1.5, outside); oil.drive(at + 3 * h, 3.4, 10.0, 1.5, outside) }
                DayOfWeek.SUNDAY -> oil.drive(at + 6 * h, 41.8, 55.0, 3.0, outside)
                else -> {
                    oil.drive(at, 14.5, 32.0, 4.5, outside - 3); oil.drive(at + 21 * h / 2, 14.8, 35.0, 5.0, outside)
                    if (weekday == DayOfWeek.TUESDAY || weekday == DayOfWeek.THURSDAY) {
                        // The last school run is one trip, so the drive the demo adds brings the count to what the video says.
                        if (d == 1) oil.drive(at + 7 * h, 5.2, 16.0, 2.0, outside - 9)
                        else { oil.drive(at + 7 * h, 2.6, 8.0, 1.0, outside); oil.drive(at + 8 * h, 2.6, 8.0, 1.0, outside) }
                    }
                }
            }
        }
        oil.marks[id(Part.ENGINE_OIL)] = 0

        // Since the rotation, city-leaning: 0.55 m/s² RMS fore-aft and 0.95 sideways weigh about 1.12 times the
        // regulation's normal driving, at 14 °C on average.
        val seen = (km(0) - km(170)) * 1_000.0
        val tire = TireLoad(Moments(seen, 0.0, seen * 0.55 * 0.55), Moments(seen, 0.0, seen * 0.95 * 0.95),
            mutableMapOf("demo-TIRE-rotation" to (Moments() to Moments())), Moments(seen, seen * 14.0, 0.0), mutableMapOf("demo-TIRE-rotation" to Moments()))

        // Commutes since the blade change read near the normal for their hour, with a few hot weekend soaks.
        val outside = OutsideDays()
        for (d in bladeDays downTo 1) {
            val t = ago(d)
            if (weekday(d) == DayOfWeek.SATURDAY) continue
            outside.add(t.atTime(8, 20), RubberAging.normalAt(t.monthValue, 8.33) + (d % 5 - 2))
            outside.add(t.atTime(18, 40), RubberAging.normalAt(t.monthValue, 18.67) + (d % 3 - 1))
            if (weekday(d) == DayOfWeek.SUNDAY && d % 4 == 0) outside.soak(t, RubberAging.NORMAL_MAX[t.monthValue - 1] + 22.0, RubberAging.NORMAL_MAX[t.monthValue - 1])
        }
        return Story(today, odometer, records, counters, filter, brake, wiper, sound, cooldown, heat, oil, tire, outside)
    }

    /** Replaces everything the app holds with [story], on the Tucson whose history it is. */
    fun apply(context: Context, story: Story) {
        val care = CareStore(context)
        care.reset(); care.applyRecommendations()
        // The emulator reports a generic electric "Connect" car; the story is a Tucson's, so the demo fixes the model.
        care.chooseModel(VehicleCatalog.TUCSON_GASOLINE.id)
        care.mileage = Mileage(story.odometer, story.today, "vehicle", System.currentTimeMillis())
        care.records += story.records
        story.records.filter { it.id == "demo-${it.part.name}" }.forEach { care.plans[it.part] = RecommendedCare.plan(it.part, it.id) }
        care.demo = true
        care.save()
        Reminders.forget(context)
        // The story is a finished setup; the first-run questions would only ask what it already holds.
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit().putBoolean("done", true).apply()
        val store = SignalStore(context)
        store.clear()
        story.counters.forEach { (u, c) -> store.save(u, c) }
        store.saveFilterDust(story.filter); store.saveBrakeWork(story.brake); store.saveWiperCycles(story.wiper)
        store.saveWiperSound(story.sound); store.saveCooldown(story.cooldown); store.saveHeatup(story.heatup)
        store.saveOilTrips(story.oil); store.saveTireLoad(story.tire); store.saveOutsideDays(story.outside)
    }

    private fun millis(date: LocalDate) = date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** One drive at 1 Hz: idle split before and after, a steady speed between, ending [minutes] after [wallMs]. */
    private fun OilTrips.drive(wallMs: Long, km: Double, minutes: Double, idle: Double, outside: Double) {
        val kph = km / (minutes - idle) * 60
        gear(false, 0, outside, wallMs)
        val total = (minutes * 60).toInt()
        for (sec in 1..total) speed(if (sec < idle * 30 || sec > total - idle * 30) 0.0 else kph / 3.6, sec * 1_000L)
        gear(true, total * 1_000L, outside, wallMs + total * 1_000L)
    }
}
