package dev.vehiclecare.app

import java.time.LocalDate

/** The vehicle-signal models a judgment reads; built from the live feed on screen and from storage for reminders. */
class SignalSnapshot(
    val oil: OilTrips = OilTrips(),
    val brake: BrakeWork = BrakeWork(),
    val wiper: WiperCycles = WiperCycles(),
    val sound: WiperSound = WiperSound(),
    val cooldown: ClimateTracker = ClimateTracker(),
    val fresh: UsageCounter = UsageCounter(),
    /** Engine oil level as the car reports it, when it does. */
    val oilLevel: String? = null,
    val tire: TireLoad = TireLoad(),
    val outside: OutsideDays = OutsideDays(),
    val heatup: ClimateTracker = ClimateTracker(heating = true),
    val filter: FilterDust = FilterDust(),
    /** Brake fluid level as the car reports it, when it does. */
    val brakeFluid: String? = null,
)

/** A part's state from the schedule and the vehicle signals together. */
data class Assessment(
    val schedule: DueResult,
    val status: DueStatus,
    /** Share of the service interval used: the larger of the schedule's and the signal estimate's. */
    val fraction: Float?,
    /** The estimate, when it brought the part due sooner than the schedule did. */
    val drivenBy: String?,
    /** Changes worth a look that do not by themselves change the status. */
    val observations: List<String>,
    /** The service the status asks for when it is not the plan's own (a tire rotation inside a monthly check). */
    val task: ServiceAction? = null,
)

/**
 * Combines the schedule with what the car measured. Estimates can only bring a service forward, never push the
 * manufacturer's interval back, and each uses the low end of its range so a part is not called due on the optimistic
 * side of the model. A signal share reaches SOON at 90 %, the same point the schedule uses (1,000 of 10,000 km).
 */
object CompositeJudge {
    const val SOON_SHARE = 0.9
    /** Typical brake work for this car per km [kJ]: WLTP-Brake's 79 J per kg and km times its mass. */
    fun brakeReferenceKjPerKm() = BrakeWearModel.TYPICAL_J_PER_KG_KM * BrakeWearModel.params().massKg / 1_000

    fun assess(plan: MaintenancePlan?, record: ServiceRecord?, mileage: Mileage?, today: LocalDate, signals: SignalSnapshot,
               history: List<ServiceRecord> = listOfNotNull(record), report: OwnerReport? = null): Assessment {
        // Pausing silences the reminders, not the judgment: the part is judged the same, and the callers leave it out of alerts.
        if (plan?.paused == true) return assess(plan.copy(paused = false), record, mileage, today, signals, history, report)
        val schedule = ScheduleCalculator.calculate(plan, record, mileage, today)
        val scheduleFraction = PartArt.fraction(plan, record, mileage, today)
        if (plan == null) return Assessment(schedule, schedule.status, scheduleFraction, null, emptyList())
        val observations = observations(plan.part, record, signals)
        val level = if (plan.part == Part.ENGINE_OIL) signals.oilLevel?.takeIf { it != "정상" } else null
        if (level != null) return Assessment(schedule, DueStatus.DUE, scheduleFraction, "엔진 오일량 $level", observations)
        // Low brake fluid is a leak or pads worn far enough to draw the level down; either way it is the car's own warning.
        val fluid = if (plan.part == Part.BRAKE_PAD) signals.brakeFluid?.takeIf { it != "정상" } else null
        if (fluid != null) return Assessment(schedule, DueStatus.DUE, scheduleFraction, "브레이크액 $fluid", observations)
        // Pad thickness since the last pad replacement: due at 3 mm left on the upper estimate, soon at 4 mm. Its share of
        // the usable pad (new down to the 3 mm line) is a real figure, so it fills the ring even while the pad is fine.
        val padMm = if (plan.part == Part.BRAKE_PAD) padLeft(history, signals) else null
        val padShare = padMm?.let { (BrakeWearModel.params().padNewMm - it) / (BrakeWearModel.params().padNewMm - BrakeWearModel.REPLACE_MM) }
        val pad = padMm?.let { left ->
            val mm = String.format(java.util.Locale.KOREA, "%.1f", left)
            when { left <= BrakeWearModel.REPLACE_MM -> Triple(DueStatus.DUE, 1.0, "패드 잔량 추정 $mm mm")
                left <= BrakeWearModel.SOON_MM -> Triple(DueStatus.SOON, padShare!!, "패드 잔량 추정 $mm mm"); else -> null } }
        if (plan.part == Part.TIRE) return tire(schedule, scheduleFraction, observations, history, mileage, signals, today)
        val estimate = record?.let { signalShare(plan, it, mileage, signals) }?.let { (s, by) ->
            Triple(when { s >= 1.0 -> DueStatus.DUE; s >= SOON_SHARE -> DueStatus.SOON; else -> DueStatus.UPCOMING }, s, by) }
        // Heat ageing moves the calendar itself, so it is judged on the calendar's own terms: days left, not a share.
        val ageing = if (plan.part == Part.WIPER) record?.let { ageing(plan, it, today, signals.outside, signals.wiper) } else null
        val climate = { d: CoolingDecline, what: String ->
            val pct = Math.round(d.ratio * 100)
            Triple(d.status, if (d.status == DueStatus.DUE) 1.0 else SOON_SHARE,
                if (d.status == DueStatus.DUE) "${what}이 거의 안 됨 · 점검 직후 대비 $pct%" else "$what 회복 점검 직후 대비 $pct%")
        }
        val cooling = if (plan.part == Part.HVAC) record?.let { signals.cooldown.decline(it.id) }?.let { climate(it, "냉방") } else null
        // A hybrid runs its engine for heat when it chooses and an EV heats electrically; for both only "barely heats" counts, not a slow trend.
        val heating = if (plan.part == Part.HVAC) record?.let { signals.heatup.decline(it.id, soon = CarModel.current.powertrain.let { p -> p != Powertrain.HYBRID && p != Powertrain.ELECTRIC }) }
            ?.let { climate(it, "난방") } else null
        // Estimated fuel in the oil: 3 % is a caution, 5 % a change (where 0W-20 loses about a fifth of its viscosity).
        val diluted = if (plan.part == Part.ENGINE_OIL) record?.let { r -> signals.oil.summary(r.id, null).dilution }?.let { f ->
            val pct = String.format(java.util.Locale.KOREA, "%.1f", f)
            when { f >= OilModel.CHANGE_PCT -> Triple(DueStatus.DUE, 1.0, "연료 희석 추정 $pct%")
                f >= OilModel.CAUTION_PCT -> Triple(DueStatus.SOON, SOON_SHARE, "연료 희석 추정 $pct%"); else -> null } } else null
        val reported = report?.takeIf { record != null }?.let { Triple(it.status, if (it.status == DueStatus.DUE) 1.0 else SOON_SHARE, it.label) }
        // The ring fills with measured shares only: distance, days, dust and pad wear. A state reached by a pattern (noise,
        // slower cooling, a reported smell, fuel in the oil) shows in colour and words, not as a share it never measured.
        val measured = listOfNotNull(scheduleFraction?.toDouble(), estimate?.takeIf { plan.part != Part.WIPER }?.second, ageing?.second, padShare)
        val ringShare = measured.maxOrNull()?.toFloat()?.coerceIn(0f, 1f)
        val pick = listOfNotNull(estimate, ageing, cooling, heating, diluted, pad, reported).maxWithOrNull(compareBy({ rank(it.first) }, { it.second }))
            ?: return Assessment(schedule, schedule.status, ringShare, null, observations)
        val signalStatus = pick.first
        val share = pick.second to pick.third
        val fraction = ringShare
        // Only a due or soon estimate overrides; "plenty left" must not hide a schedule that still lacks information.
        return if (signalStatus != DueStatus.UPCOMING && rank(signalStatus) > rank(schedule.status)) Assessment(schedule, signalStatus, fraction, share.second, observations)
            else Assessment(schedule, schedule.status, fraction, null, observations)
    }

    /**
     * The monthly check stays the plan; rotation runs beside it on its own odometer baseline, and hard driving brings
     * it forward. It overrides only when it is more urgent, like any estimate.
     */
    /**
     * Tires carry three jobs: the monthly check (the plan), rotation every 10,000 km, and replacement six years after
     * manufacture. The most urgent one is asked for; on a tie the bigger job wins (replacement, then rotation), since
     * it covers the smaller.
     */
    private fun tire(schedule: DueResult, scheduleFraction: Float?, observations: List<String>, history: List<ServiceRecord>,
                     mileage: Mileage?, signals: SignalSnapshot, today: LocalDate): Assessment {
        data class Job(val status: DueStatus, val share: Double, val by: String, val task: ServiceAction)
        val rotation = Rotation.of(history, mileage, signals.tire)?.let { r ->
            val km = String.format(java.util.Locale.KOREA, "%,.0f", maxOf(r.sinceKm, r.equivalentKm))
            Job(when { r.share >= 1.0 -> DueStatus.DUE; r.share >= SOON_SHARE -> DueStatus.SOON; else -> DueStatus.UPCOMING }, r.share,
                if (r.broughtForward) "운전 습관 환산 $km km" else "${r.baseline.action.label} 후 $km km", ServiceAction.ROTATE)
        }
        val age = TireAge.of(history, today)?.let { Job(it.status, it.share, "장착 후 ${it.label}", ServiceAction.REPLACE) }
        // The tread is an estimate a gauge can settle, and the tire-to-tire spread is ±35 %, so it asks for a check, never
        // a new set: soon once the upper estimate reaches 3 mm, due once the centre reaches 2.4 mm (Hyundai's motorway
        // line). A recorded inspection narrows the estimate (see TireTread) instead of silencing it.
        val tread = TireTread.of(history, mileage, signals.tire)?.let { t ->
            val p = TireParams.of(CarModel.current)
            val share = ((p.newMm - t.lowest) / (p.newMm - TireModel.MOTORWAY_TREAD_MM)).coerceAtLeast(0.0)
            Job(when { t.central <= TireModel.MOTORWAY_TREAD_MM -> DueStatus.DUE; t.lowest <= t.askAt -> DueStatus.SOON; else -> DueStatus.UPCOMING },
                share, "홈 깊이 추정 ${String.format(java.util.Locale.KOREA, "%.1f", t.central)} mm", ServiceAction.INSPECT)
        }
        val jobs = listOfNotNull(rotation, age, tread)
        val fraction = (listOfNotNull(scheduleFraction?.toDouble()) + jobs.map { it.share }).maxOrNull()?.toFloat()?.coerceIn(0f, 1f)
        val pick = jobs.filter { it.status != DueStatus.UPCOMING }.maxWithOrNull(compareBy({ rank(it.status) }, { it.task == ServiceAction.REPLACE }))
        if (pick == null || rank(pick.status) < rank(schedule.status)) return Assessment(schedule, schedule.status, fraction, null, observations)
        return Assessment(schedule, pick.status, fraction, pick.by, observations, pick.task)
    }

    /** Front pad left on the upper estimate [mm], counted from the latest pad replacement the app saw; null without one. */
    /** The latest brake check with a thickness read, if it is newer than the last pad change. */
    fun padMeasured(history: List<ServiceRecord>, signals: SignalSnapshot): ServiceRecord? {
        val pads = history.filter { it.part == Part.BRAKE_PAD }
        val measured = pads.filter { it.action == ServiceAction.INSPECT && it.measuredMm != null && signals.brake.baselines.containsKey(it.id) }
            .maxByOrNull { it.date ?: java.time.LocalDate.MIN } ?: return null
        val replaced = pads.filter { it.action == ServiceAction.REPLACE }.maxByOrNull { it.date ?: java.time.LocalDate.MIN }
        return measured.takeIf { replaced == null || (it.date ?: java.time.LocalDate.MIN) >= (replaced.date ?: java.time.LocalDate.MIN) }
    }

    fun padLeft(history: List<ServiceRecord>, signals: SignalSnapshot): Double? {
        val pads = history.filter { it.part == Part.BRAKE_PAD }
        // A thickness read at an inspection replaces the estimate up to then; wear is counted on from that inspection.
        padMeasured(history, signals)?.let { m -> return m.measuredMm!! - signals.brake.padWornMm(m.id).second }
        val replaced = pads.filter { it.action == ServiceAction.REPLACE }.maxByOrNull { it.date ?: java.time.LocalDate.MIN }
        if (replaced == null || !signals.brake.baselines.containsKey(replaced.id)) return null
        return BrakeWearModel.params().padNewMm - signals.brake.padWornMm(replaced.id).second
    }

    /** Days left on the replacement interval counted in normal-year ageing days; null before a dated record. */
    fun ageing(plan: MaintenancePlan, record: ServiceRecord, today: LocalDate, outside: OutsideDays, cycles: WiperCycles = WiperCycles()): Triple<DueStatus, Double, String>? {
        val from = record.date ?: return null
        val period = java.time.temporal.ChronoUnit.DAYS.between(from, from.plusMonths((plan.intervalMonths ?: return null).toLong())).toDouble()
        val aged = RubberAging.equivalentDays(from, today, outside, RubberAging.EA_LOW) + bladeWipeDays(record, today, cycles)
        val left = period - aged
        val status = when { left <= 0 -> DueStatus.DUE; left <= ScheduleCalculator.soonDays(plan.intervalMonths) -> DueStatus.SOON; else -> DueStatus.UPCOMING }
        return Triple(status, aged / period, "블레이드 노화 환산 ${String.format(java.util.Locale.KOREA, "%,.0f", aged)}일")
    }

    /** Blade days added by wiping beyond the typical pace, on the lower wipe count, since the app saw the blade fitted. */
    fun bladeWipeDays(record: ServiceRecord, today: LocalDate, cycles: WiperCycles): Double {
        val from = record.date ?: return 0.0
        val (low, _, since) = cycles.since(record.id)
        return if (since) WiperModel.excessDays(low, java.time.temporal.ChronoUnit.DAYS.between(from, today).toDouble()) else 0.0
    }

    /**
     * Distance a typical driver of this car would need for the same friction wear (after regeneration and the
     * disc-temperature factor), plus distance the app did not see. Null until 1,000 km of braking is on record.
     */
    fun brakeEquivalentKm(record: ServiceRecord, sinceKm: Double?, s: SignalSnapshot): Double? {
        val (_, distance, since) = s.brake.since(record.id)
        if (!since || distance < 1_000) return null
        return s.brake.totalsSince(record.id).wearJ / 1_000 / brakeReferenceKjPerKm() + ((sinceKm ?: 0.0) - distance / 1_000).coerceAtLeast(0.0)
    }

    /** Share of the interval the vehicle signals say is used, with what it was measured as. */
    fun signalShare(plan: MaintenancePlan, record: ServiceRecord, mileage: Mileage?, s: SignalSnapshot): Pair<Double, String>? {
        val sinceKm = record.odometerKm?.let { at -> mileage?.km?.takeIf { it >= at }?.let { (it - at).toDouble() } }
        val km = { v: Double -> String.format(java.util.Locale.KOREA, "%,.0f", v) }
        return when (plan.part) {
            Part.ENGINE_OIL -> {
                plan.intervalKm ?: return null
                val sum = s.oil.summary(record.id, sinceKm)
                if (sum.trips == 0) return null
                // Stress is in kilometres of the model's normal use; a severe-condition plan has already halved the interval itself.
                sum.effectiveKm.first / OilModel.normalKm() to "운행 조건 환산 ${km(sum.effectiveKm.first)} km"
            }
            Part.BRAKE_PAD -> {
                val interval = plan.intervalKm ?: return null
                val equivalent = brakeEquivalentKm(record, sinceKm, s) ?: return null
                equivalent / interval to "제동 에너지 환산 ${km(equivalent)} km"
            }
            Part.WIPER -> {
                // Noise here, the Hyundai manual's own inspection cue; age (calendar, heat, sun, heavy wiping) is judged beside it in days.
                // A rise is first a cue to clean the glass and blade; one that is still there afterwards is the manual's inspection case.
                s.sound.shift(record.id)?.let {
                    if (it.confirmed) 1.0 to String.format(java.util.Locale.KOREA, "닦은 뒤에도 작동 소리 %+.1f dB", it.db)
                    else SOON_SHARE to String.format(java.util.Locale.KOREA, "작동 소리 %+.1f dB", it.db)
                }
            }
            Part.CABIN_FILTER -> {
                val load = s.filter.since(record.id)?.takeIf { it > 0 } ?: return null
                load / FilterModel.END_OF_SERVICE_HOURS to "먼지 적재 추정 ${Math.round(load / FilterModel.END_OF_SERVICE_HOURS * 100)}%"
            }
            else -> null
        }
    }

    /** Chips for changes that do not move the status. Wiper noise and A/C cooling used to be shown here; both now judge. */
    @Suppress("UNUSED_PARAMETER")
    fun observations(part: Part, record: ServiceRecord?, s: SignalSnapshot): List<String> = emptyList()

    private fun rank(s: DueStatus) = when (s) { DueStatus.DUE -> 3; DueStatus.SOON -> 2; DueStatus.UPCOMING -> 1; else -> 0 }
}
