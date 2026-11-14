package dev.vehiclecare.app

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import org.json.JSONObject

/**
 * Use of a vehicle function while the app was collecting: seconds for running functions, events for presses.
 * Usage only, never a wear estimate.
 */
class UsageCounter(var total: Long = 0, var runningSinceMs: Long? = null, val baselines: MutableMap<String, Long> = mutableMapOf()) {
    fun onState(on: Boolean, nowMs: Long) {
        checkpoint(nowMs)
        runningSinceMs = if (on) runningSinceMs ?: nowMs else null
    }
    /** Folds whole elapsed seconds of the open interval into the total so a killed process loses under a minute. */
    fun checkpoint(nowMs: Long) {
        val since = runningSinceMs ?: return
        val seconds = ((nowMs - since) / 1000).coerceAtLeast(0)
        total += seconds; runningSinceMs = since + seconds * 1000
    }
    fun add(events: Long) { total += events }
    fun markBaseline(recordId: String) { baselines[recordId] = total }
    /** Amount since the record, or since tracking began when the record predates tracking. */
    fun since(recordId: String?): Pair<Long, Boolean> = baselines[recordId]?.let { (total - it).coerceAtLeast(0) to true } ?: (total to false)
}

/** What each counter measures and which part it informs. */
enum class Usage(val part: Part, val label: String, val counts: Boolean = false) {
    WIPER(Part.WIPER, "앞 와이퍼 작동"),
    AC(Part.HVAC, "에어컨 작동"),
    BLOWER(Part.CABIN_FILTER, "공조 송풍"),
    BRAKE(Part.BRAKE_PAD, "브레이크 페달 작동", counts = true),
    // Filter loading splits blower time by air source; the first entry per part is the one shown as its headline usage.
    FRESH(Part.CABIN_FILTER, "외기 송풍"),
    RECIRC(Part.CABIN_FILTER, "내기 순환 송풍"),
    ;
    companion object { fun of(part: Part) = entries.firstOrNull { it.part == part } }
}

/** Last known climate state; null means the car has not reported it (or the car lacks it). */
data class Climate(val power: Boolean? = null, val ac: Boolean? = null, val fan: Int? = null, val recirc: Boolean? = null,
                   val cabinC: Double? = null, val setC: Double? = null) {
    // An unknown power state does not block counting; a reported OFF does.
    val acRunning get() = power != false && ac == true
    val blowing get() = power != false && (fan ?: 0) > 0
    // An unreported intake counts as outside air: the filter then loads faster, never slower, than shown.
    val freshBlowing get() = blowing && recirc != true
    val recircBlowing get() = blowing && recirc == true
}

/** Counts a press only on a released-to-pressed change the app actually saw. */
class PressEdge {
    private var last: Boolean? = null
    fun pressed(now: Boolean?): Boolean { val edge = last == false && now == true; last = now; return edge }
    /** Forget the last state; presses while not collecting were not seen and must not be inferred. */
    fun forget() { last = null }
}

/** A warning the car itself raised. */
data class CarWarning(val on: Boolean, val observedAt: Long)

class SignalStore(context: Context) {
    private val prefs = context.getSharedPreferences("vehicle-signals-v1", Context.MODE_PRIVATE)
    fun counter(u: Usage): UsageCounter = runCatching {
        val o = JSONObject(prefs.getString(u.key, "{}")!!)
        val baselines = mutableMapOf<String, Long>()
        o.optJSONObject("baselines")?.let { b -> b.keys().forEach { baselines[it] = b.getLong(it) } }
        // An open interval uses elapsed-realtime and cannot survive a process restart.
        UsageCounter(o.optLong("total", 0), null, baselines)
    }.getOrDefault(UsageCounter())
    fun save(u: Usage, c: UsageCounter) {
        prefs.edit().putString(u.key, JSONObject().put("total", c.total).put("baselines", JSONObject(c.baselines as Map<*, *>)).toString()).apply()
    }
    fun warning(key: String): CarWarning? = prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.let { CarWarning(it.getBoolean("on"), it.getLong("at")) }
    fun saveWarning(key: String, w: CarWarning) { prefs.edit().putString(key, JSONObject().put("on", w.on).put("at", w.observedAt).toString()).apply() }
    fun brakeWork(): BrakeWork = runCatching { BrakeWork.fromJson(JSONObject(prefs.getString(BRAKE_WORK, "{}")!!)) }.getOrDefault(BrakeWork())
    fun saveBrakeWork(w: BrakeWork) { prefs.edit().putString(BRAKE_WORK, w.toJson().toString()).apply() }
    fun wiperCycles(): WiperCycles = runCatching { WiperCycles.fromJson(JSONObject(prefs.getString(WIPER_CYCLES, "{}")!!)) }.getOrDefault(WiperCycles())
    fun saveWiperCycles(c: WiperCycles) { prefs.edit().putString(WIPER_CYCLES, c.toJson().toString()).apply() }
    fun driveModes(): DriveModes = runCatching { DriveModes.fromJson(JSONObject(prefs.getString(DRIVE_MODES, "{}")!!)) }.getOrDefault(DriveModes())
    fun saveDriveModes(d: DriveModes) { prefs.edit().putString(DRIVE_MODES, d.toJson().toString()).apply() }
    fun filterDust(): FilterDust = runCatching { FilterDust.fromJson(JSONObject(prefs.getString(FILTER_DUST, "{}")!!)) }.getOrDefault(FilterDust())
    fun saveFilterDust(f: FilterDust) { prefs.edit().putString(FILTER_DUST, f.toJson().toString()).apply() }
    fun wiperSound(): WiperSound = runCatching { WiperSound.fromJson(JSONObject(prefs.getString(WIPER_SOUND, "{}")!!)) }.getOrDefault(WiperSound())
    fun saveWiperSound(w: WiperSound) { prefs.edit().putString(WIPER_SOUND, w.toJson().toString()).apply() }
    fun cooldown(): ClimateTracker = runCatching { ClimateTracker.fromJson(JSONObject(prefs.getString(COOLDOWN, "{}")!!)) }.getOrDefault(ClimateTracker())
    fun saveCooldown(c: ClimateTracker) { prefs.edit().putString(COOLDOWN, c.toJson().toString()).apply() }
    fun heatup(): ClimateTracker = runCatching { ClimateTracker.fromJson(JSONObject(prefs.getString(HEATUP, "{}")!!), heating = true) }.getOrDefault(ClimateTracker(heating = true))
    fun saveHeatup(c: ClimateTracker) { prefs.edit().putString(HEATUP, c.toJson().toString()).apply() }
    fun oilTrips(): OilTrips = runCatching { OilTrips.fromJson(JSONObject(prefs.getString(OIL_TRIPS, "{}")!!)) }.getOrDefault(OilTrips())
    fun saveOilTrips(t: OilTrips) { prefs.edit().putString(OIL_TRIPS, t.toJson().toString()).apply() }
    fun tireLoad(): TireLoad = runCatching { TireLoad.fromJson(JSONObject(prefs.getString(TIRE_LOAD, "{}")!!)) }.getOrDefault(TireLoad())
    fun saveTireLoad(t: TireLoad) { prefs.edit().putString(TIRE_LOAD, t.toJson().toString()).apply() }
    fun outsideDays(): OutsideDays = runCatching { OutsideDays.fromJson(JSONObject(prefs.getString(OUTSIDE_DAYS, "{}")!!)) }.getOrDefault(OutsideDays())
    fun saveOutsideDays(d: OutsideDays) { prefs.edit().putString(OUTSIDE_DAYS, d.toJson().toString()).apply() }
    /** Wall-clock time the car last went into Park while collecting; survives the head unit sleeping between trips. */
    fun parkedAt(): Long? = prefs.getLong(PARKED_AT, -1).takeIf { it > 0 }
    fun saveParkedAt(ms: Long) { prefs.edit().putLong(PARKED_AT, ms).apply() }
    fun clear() { prefs.edit().clear().apply() }
    /** The stored signals, for judgments made without the live feed (the daily reminder check). */
    fun snapshot() = SignalSnapshot(oilTrips(), brakeWork(), wiperCycles(), wiperSound(), cooldown(), counter(Usage.FRESH), tire = tireLoad(), outside = outsideDays(), heatup = heatup(), filter = filterDust())
    // "wiper" predates the other counters and keeps its key so existing totals survive.
    private val Usage.key get() = name.lowercase()
    companion object { const val WASHER = "washer"; const val BRAKE_WORK = "brake-work"; const val WIPER_CYCLES = "wiper-cycles"; const val WIPER_SOUND = "wiper-sound"; const val COOLDOWN = "cooldown"; const val OIL_TRIPS = "oil-trips"; const val TIRE_LOAD = "tire-load"; const val OUTSIDE_DAYS = "outside-hours"; const val PARKED_AT = "parked-at"; const val HEATUP = "heatup"; const val FILTER_DUST = "filter-dust"; const val DRIVE_MODES = "drive-modes" }
}

/**
 * Process-scoped, read-only feed for wiper, A/C and blower time, brake presses and the washer fluid warning. Counting
 * happens only while something holds the feed open: the visible screen, or [SignalService] in the background. With
 * neither, Android freezes the process, so events are lost and an open interval would otherwise absorb the frozen period.
 * Vehicle speed is not in the Pleos SDK; it comes from AAOS PERF_VEHICLE_SPEED, which needs the CAR_SPEED runtime grant.
 * Tire pressure and telltales (TPMS) need PRIVATE_CAR_SAFETY at runtime in SDK 2.3.2, and steering angle needs
 * READ_CAR_STEERING (signature|privileged); a third-party app holds neither, so tires are judged from speed and the
 * rear-wheel ticks that CAR_SPEED also opens.
 */
object VehicleSignals {
    private val waiting = "차량 연결 대기"
    private val states = Usage.entries.associateWith { waiting }.toMutableMap()
    private val handler = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private lateinit var store: SignalStore
    private val counters = mutableMapOf<Usage, UsageCounter>()
    private val listeners = mutableSetOf<() -> Unit>()
    private val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var wiperOn = false
    private var climate = Climate()
    private var washerAttempts = 0
    // Who keeps the feed open; counting runs while any holder remains.
    private val holders = mutableSetOf<String>()
    private val collecting get() = holders.isNotEmpty()
    const val SCREEN = "screen"
    const val SERVICE = "service"
    val screenShown get() = SCREEN in holders
    /** Set by [SignalService] when Android let it hold the microphone out of sight. */
    var backgroundMic = false
    private val brakeEdge = PressEdge()
    private var brakeDirty = false
    const val SPEED_PERMISSION = Car.PERMISSION_SPEED
    /**
     * OBD thermostat monitors only run from a cold engine (13 CCR 1968.2 (e)(10.3.1)); three hours parked stands in for
     * the coolant reading the app does not get. A warm-up may start in the first two minutes of the trip.
     */
    const val COLD_START_HOURS = 3L
    const val COLD_START_WINDOW_MS = 120_000L
    private const val DEPARTURE_STOP_MS = 5 * 60_000L
    private var car: Car? = null
    private var brakeWork = BrakeWork()
    private var speedState = "차속 권한 필요"
    private lateinit var app: Context
    private var cooldown = ClimateTracker()
    private var heatup = ClimateTracker(heating = true)
    private var filterDust = FilterDust()
    /** Air through the cabin filter now: blower on either intake, or ram air on fresh intake while moving. */
    private fun filterFlow() = if (!collecting) 0.0 else FilterDust.m3h(climate.blowing, climate.recirc,
        climate.fan?.let { f -> fanMax?.takeIf { it > 0 }?.let { (f.toDouble() / it).coerceIn(0.0, 1.0) } }, speedKph)
    /** Until when (elapsed ms) a warm-up may start: the first minutes of a trip after a long enough stop for a cold engine. */
    private var coldStartUntil = 0L
    private var soakedHours: Double? = null
    private var speedKph: Double? = null
    private var outsideC: Double? = null
    private var speedRegistered = false
    private var tireLoad = TireLoad()
    private var outsideDays = OutsideDays()
    private var outsideDirty = false
    private var wheelState = "바퀴 회전 대기"
    private var tickReset: Long? = null
    private var tickMicrometres: List<Double> = emptyList()
    private val wheelCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) { (value.value as? Array<*>)?.let { v -> handler.post { onWheels(v) } } }
        override fun onErrorEvent(propertyId: Int, zone: Int) { handler.post { wheelState = "바퀴 회전 수신 오류" } }
    }
    private val outsideCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) { (value.value as? Float)?.let { v -> handler.post { onOutside(v.toDouble()) } } }
        override fun onErrorEvent(propertyId: Int, zone: Int) {}
    }
    private val recircListener = object : HvacRecirculationStateListener {
        override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) { handler.post { onClimate(climate.copy(recirc = state)) } }
        override fun onFailed(e: Exception) {}
    }
    private val cabinListener = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(value: Float?) { handler.post { onClimate(climate.copy(cabinC = value?.toDouble()?.takeIf { it in -40.0..80.0 })) } }
        override fun onFailed(e: Exception) {}
    }
    private val setListener = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(value: Float?) { handler.post { onClimate(climate.copy(setC = value?.toDouble()?.takeIf { it in 10.0..35.0 })) } }
        override fun onFailed(e: Exception) {}
    }
    private var oilTrips = OilTrips()
    // Last gear seen while collecting; null after a gap, so a trip already under way is never started mid-drive.
    private var parked: Boolean? = null
    private var gearState = "변속 상태 대기"
    private var oilLevel: String? = null
    /** Brake fluid level as the car reports it (Pleos Safety), when it does. */
    private var brakeFluid: String? = null
    private val brakeFluidListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(state: OilLevelState?) { handler.post { onBrakeFluid(state) } }
        override fun onFailed(e: Exception) {}
    }
    private var drivingMode: DrivingModeType? = null
    private val drivingModeListener = object : DrivingModeStateListener {
        override fun onDrivingModeStateUpdated(state: DrivingModeType?) { handler.post { drivingMode = state } }
        override fun onFailed(e: Exception) {}
    }
    private var driveModes = DriveModes()
    /** True once the car has reported its ignition; then the stop is timed from switch-off, not from Park. */
    private var ignitionSeen = false
    private var lastIgnition = 0
    private val ignitionCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) { (value.value as? Int)?.let { v -> handler.post { onIgnition(v) } } }
        override fun onErrorEvent(propertyId: Int, zone: Int) {}
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { handler.post { onGear(gear) } }
        override fun onFailed(e: Exception) { handler.post { gearState = "변속 상태 대기" } }
    }
    private val oilLevelListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(state: OilLevelState?) { handler.post { onOilLevel(state) } }
        override fun onFailed(e: Exception) {}
    }
    private var wiperCycles = WiperCycles()
    private var wiperSound = WiperSound()
    private var wiperLevel = WiperModel.Level.UNKNOWN
    private var micState = "마이크 권한 필요"
    private val mic = WiperMic(
        // Tagged with the switch level at the end of the session; noise is compared only between sessions at one level.
        onSession = { s -> handler.post { wiperSound.add(s.copy(level = wiperLevel.name)); store.saveWiperSound(wiperSound); micState = "소리 기록됨"; notifyChanged() } },
        onState = { v -> handler.post { micState = v } })
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) { (value.value as? Float)?.let { v -> handler.post { onSpeed(v.toDouble()) } } }
        override fun onErrorEvent(propertyId: Int, zone: Int) { handler.post { speedState = "차속 수신 오류" } }
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) { handler.post { onWiper(state) } }
        override fun onFailed(e: Exception) { handler.post { setState(Usage.WIPER, "와이퍼 상태 대기") } }
    }
    private val washerListener = object : LowWasherFluidWaringListener {
        override fun onLowWasherFluidWaringUpdated(state: Boolean?) { handler.post { state?.let { onWarning(SignalStore.WASHER, it) } } }
        override fun onFailed(e: Exception) {}
    }
    private val powerListener = object : HvacPowerStateListener {
        override fun onHvacPowerStateUpdated(state: Boolean?) { handler.post { onClimate(climate.copy(power = state)) } }
        override fun onFailed(e: Exception) {}
    }
    private val acListener = object : HvacAcStateListener {
        override fun onHvacAcStateUpdated(state: Boolean?) { handler.post { onClimate(climate.copy(ac = state)) } }
        override fun onFailed(e: Exception) {}
    }
    private val fanListener = object : HvacFanSpeedListener {
        override fun onHvacFanSpeedUpdated(speed: Int?) { handler.post { onClimate(climate.copy(fan = speed)) } }
        override fun onFailed(e: Exception) {}
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(state: Boolean?) { handler.post { onBrake(state) } }
        override fun onFailed(e: Exception) { handler.post { setState(Usage.BRAKE, "페달 상태 대기") } }
    }
    private val checkpoint = object : Runnable {
        override fun run() {
            // While something keeps running no event arrives; refresh the visible totals once a minute.
            val now = SystemClock.elapsedRealtime()
            val running = counters.filterValues { it.runningSinceMs != null }
            running.forEach { (u, c) -> c.checkpoint(now); store.save(u, c) }
            if (wiperOn && collecting) { wiperCycles.update(wiperLevel, now); store.saveWiperCycles(wiperCycles); vehicle?.let { readWiperSwitch(it) } }
            timeCooldown()
            filterDust.update(filterFlow(), now, at = VehiclePlaces.location); store.saveFilterDust(filterDust)
            store.saveDriveModes(driveModes)
            if (brakeDirty) { store.saveBrakeWork(brakeWork); store.saveTireLoad(tireLoad) }
            if (outsideDirty) { store.saveOutsideDays(outsideDays); outsideDirty = false }
            if (running.isNotEmpty() || brakeDirty) { brakeDirty = false; notifyChanged() }
            handler.postDelayed(this, 60_000)
        }
    }

    fun usage(u: Usage): UsageCounter = counters.getValue(u)
    fun state(u: Usage): String = states.getValue(u)
    fun washer(): CarWarning? = store.warning(SignalStore.WASHER)
    fun brakeWork(): BrakeWork = brakeWork
    fun speedState(): String = speedState
    fun wiperCycles(): WiperCycles = wiperCycles
    fun wiperSound(): WiperSound = wiperSound
    fun micState(): String = micState
    fun micGranted(context: Context) = mic.granted(context)
    fun cooldown(): ClimateTracker = cooldown
    fun heatup(): ClimateTracker = heatup
    fun filterDust(): FilterDust = filterDust
    fun brakeFluid(): String? = brakeFluid
    fun driveModes(): DriveModes = driveModes
    fun climate(): Climate = climate
    fun outsideC(): Double? = outsideC
    fun oilTrips(): OilTrips = oilTrips
    fun oilLevel(): String? = oilLevel
    fun outsideDays(): OutsideDays = outsideDays
    fun tireLoad(): TireLoad = tireLoad
    fun wheelState(): String = if (wheelState != "바퀴 회전 연동") wheelState else when (tireLoad.ticksAgreed) {
        true -> wheelState; false -> "바퀴 회전값이 차속과 다름"; null -> "주행 대기" }
    /** Everything the composite judgment reads, as this process currently holds it. */
    fun snapshot() = if (!::store.isInitialized) SignalSnapshot()
        else SignalSnapshot(oilTrips, brakeWork, wiperCycles, wiperSound, cooldown, counters.getValue(Usage.FRESH), oilLevel, tireLoad, outsideDays, heatup, filterDust, brakeFluid)
    fun oilState(): String = when {
        !speedGranted(app) -> speedState
        oilTrips.driving -> "운행 중"
        else -> gearState
    }
    /** Re-applies the microphone window after the user answers the permission prompt. */
    fun micChanged() { micState = "와이퍼 작동 대기"; recount() }
    fun speedGranted(context: Context) = context.checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED
    fun subscribe(l: () -> Unit) { listeners += l }
    fun unsubscribe(l: () -> Unit) { listeners -= l }
    /** Starts a new usage period for the part when its service is recorded. */
    fun markCleaned(recordId: String, level: String) {
        if (!::store.isInitialized) return
        wiperSound.markCleaned(recordId, level); store.saveWiperSound(wiperSound); notifyChanged()
    }
    fun markBaseline(part: Part, recordId: String) {
        if (part == Part.ENGINE_OIL) { oilTrips.markBaseline(recordId); store.saveOilTrips(oilTrips); return }
        if (part == Part.TIRE) { tireLoad.markBaseline(recordId); store.saveTireLoad(tireLoad); return }
        val u = Usage.of(part) ?: return
        Usage.entries.filter { it.part == part }.forEach { each ->
            counters.getValue(each).apply { checkpoint(SystemClock.elapsedRealtime()); markBaseline(recordId) }
            store.save(each, counters.getValue(each))
        }
        if (u == Usage.AC) { cooldown.markBaseline(recordId); store.saveCooldown(cooldown); heatup.markBaseline(recordId); store.saveHeatup(heatup) }
        if (u == Usage.BRAKE) { brakeWork.markBaseline(recordId); store.saveBrakeWork(brakeWork) }
        if (part == Part.CABIN_FILTER) { filterDust.update(filterFlow(), SystemClock.elapsedRealtime(), at = VehiclePlaces.location); filterDust.markBaseline(recordId); store.saveFilterDust(filterDust) }
        if (u == Usage.WIPER) {
            wiperCycles.update(if (collecting && wiperOn) wiperLevel else null, SystemClock.elapsedRealtime())
            wiperCycles.markBaseline(recordId); wiperSound.markBaseline(recordId)
            store.saveWiperCycles(wiperCycles); store.saveWiperSound(wiperSound)
        }
    }
    /** Re-reads every model from storage after something outside the feed replaced it (the demo story). */
    fun reloadStored() {
        if (!::store.isInitialized) return
        Usage.entries.forEach { counters[it] = store.counter(it) }
        brakeWork = store.brakeWork()
        wiperCycles = store.wiperCycles(); wiperSound = store.wiperSound(); cooldown = store.cooldown(); heatup = store.heatup(); oilTrips = store.oilTrips(); tireLoad = store.tireLoad(); outsideDays = store.outsideDays(); filterDust = store.filterDust()
        recount(); notifyChanged()
    }
    fun reset() { store.clear(); Usage.entries.forEach { counters[it] = UsageCounter() }; brakeWork = BrakeWork(); wiperCycles = WiperCycles(); wiperSound = WiperSound(); cooldown = ClimateTracker(); heatup = ClimateTracker(heating = true); oilTrips = OilTrips(); tireLoad = TireLoad(); outsideDays = OutsideDays(); filterDust = FilterDust(); recount(); notifyChanged() }

    /** Drops a holder; with none left, closes every counting window before the process can be frozen. */
    fun release(holder: String) {
        if (!::store.isInitialized || !holders.remove(holder)) return
        if (collecting) { recount(); return }
        brakeEdge.forget(); recount(); cooldown.abort(); heatup.abort(); coldStartUntil = 0L
        oilTrips.abort(); parked = null
        brakeWork.gap(); store.saveBrakeWork(brakeWork)
        tireLoad.gap(); tickReset = null; store.saveTireLoad(tireLoad)
    }
    /** Adds a holder and reads current vehicle state, since changes while nothing was collecting were not seen. */
    fun hold(context: Context, holder: String) {
        start(context); holders += holder; recount(); connectCar(context)
        val v = vehicle(context)
        runCatching { v.getWiper().getCurrentWiperState(front, { s -> handler.post { onWiper(s) } }, {}) }
        readWiperSwitch(v)
        readClimate(v)
        runCatching { v.getBrake().getCurrentBrakePedalStatus({ s -> handler.post { onBrake(s) } }, {}) }
        runCatching { v.getGear().getCurrentGearSelectionState({ g -> handler.post { onGear(g) } }, {}) }
    }

    /**
     * The one SDK connection for this process. The Pleos service keeps a single callback per application id,
     * so a second Vehicle instance silently steals results from the first.
     */
    fun vehicle(context: Context): Vehicle = vehicle ?: Vehicle(context.applicationContext).also {
        vehicle = it; runCatching { it.initialize() }
    }

    fun start(context: Context) {
        if (::store.isInitialized) return
        store = SignalStore(context.applicationContext)
        Usage.entries.forEach { counters[it] = store.counter(it) }
        brakeWork = store.brakeWork()
        wiperCycles = store.wiperCycles(); wiperSound = store.wiperSound(); cooldown = store.cooldown(); heatup = store.heatup(); oilTrips = store.oilTrips(); tireLoad = store.tireLoad(); outsideDays = store.outsideDays(); filterDust = store.filterDust(); driveModes = store.driveModes()
        app = context.applicationContext
        micState = if (mic.granted(app)) "와이퍼 작동 대기" else "마이크 권한 필요"
        val v = vehicle(context)
        handler.postDelayed({ connectWiper(v); connectWasher(v); connectClimate(v); connectBrake(v); connectGear(v); connectOilLevel(v); connectSteering(v)
            connectBrakeFluid(v); connectDrivingMode(v) }, 1500)
        handler.postDelayed(checkpoint, 60_000)
    }

    private fun readable(type: AccessType) = type == AccessType.READ || type == AccessType.READ_WRITE

    private fun connectWiper(v: Vehicle): Unit = runCatching {
        v.getWiper().checkWiperStateCapabilities({ config, areas ->
            handler.post {
                if (!readable(config.accessType) || areas.keys.none { it == front }) { setState(Usage.WIPER, "와이퍼 상태 미지원"); return@post }
                v.getWiper().registerWiperState(front, wiperListener)
                v.getWiper().getCurrentWiperState(front, { s -> handler.post { onWiper(s) } }, {})
            }
        }, { handler.post { setState(Usage.WIPER, waiting); retry { connectWiper(v) } } })
    }.onFailure { setState(Usage.WIPER, waiting); retry { connectWiper(v) } }.let {}

    private fun connectWasher(v: Vehicle): Unit = runCatching {
        v.getSafety().checkLowWasherFluidWarningStateCapabilities({ config ->
            handler.post {
                if (!readable(config.accessType)) return@post
                v.getSafety().registerLowWasherFluidWaringState(washerListener)
                v.getSafety().hasLowWasherFluidWarning({ low -> handler.post { low?.let { onWarning(SignalStore.WASHER, it) } } }, {})
            }
        }, { handler.post { retryWasher(v) } })
    }.onFailure { retryWasher(v) }.let {}

    // Many vehicles (and the emulator) do not expose the washer level; give up quietly after a few tries.
    private fun retryWasher(v: Vehicle) { if (++washerAttempts < 3) retry { connectWasher(v) } }

    // Each climate property reports on its own seat area (on Pleos Connect 2.1.0, A/C and power use the
    // whole first row while fan speed rejects that area), so every property keeps the area its capability gave.
    private var acArea: SeatArea? = null
    private var powerArea: SeatArea? = null
    private var fanArea: SeatArea? = null
    private var fanMax: Int? = null

    /** A/C on/off is the anchor: without it neither A/C time nor blower time is attributed to the system. */
    private fun connectClimate(v: Vehicle): Unit = runCatching {
        val hvac = v.getHVAC()
        hvac.checkHvacAcCapabilities({ config, areas ->
            handler.post {
                val area = areas.firstOrNull()
                if (!readable(config.accessType) || area == null) {
                    setState(Usage.AC, "에어컨 상태 미지원"); setState(Usage.BLOWER, "송풍 상태 미지원"); return@post
                }
                acArea = area; hvac.registerHvacAcState(area, acListener)
                // Power and fan are optional refinements; a car that lacks them still counts A/C time.
                runCatching { hvac.checkHvacPowerCapabilities({ c, list -> handler.post {
                    list.firstOrNull()?.takeIf { readable(c.accessType) }?.let { powerArea = it; hvac.registerHvacPowerState(it, powerListener) }
                    readClimate(v)
                } }, {}) }
                runCatching { hvac.checkHvacFanSpeedCapabilities({ c, bounds -> handler.post {
                    if (bounds.isEmpty() || !readable(c.accessType)) setState(Usage.BLOWER, "송풍 상태 미지원")
                    else { fanArea = bounds.keys.first(); fanMax = bounds.values.first().maxValue; hvac.registerHvacFanSpeed(fanArea!!, fanListener) }
                    readClimate(v)
                } }, { handler.post { setState(Usage.BLOWER, "송풍 상태 미지원") } }) }
                connectClimateExtras(v)
                readClimate(v)
            }
        }, { handler.post { setState(Usage.AC, waiting); setState(Usage.BLOWER, waiting); retry { connectClimate(v) } } })
    }.onFailure { retry { connectClimate(v) } }.let {}

    private var recircArea: SeatArea? = null
    private var cabinArea: SeatArea? = null
    private var setArea: SeatArea? = null

    /** Intake and temperatures refine the filter and cool-down figures; a car without them keeps the plain timers. */
    private fun connectClimateExtras(v: Vehicle) {
        val hvac = v.getHVAC()
        runCatching { hvac.checkHvacRecirculationCapabilities({ c, list -> handler.post {
            list.firstOrNull()?.takeIf { readable(c.accessType) }?.let { recircArea = it; hvac.registerHvacRecirculationState(it, recircListener); readClimate(v) }
        } }, {}) }
        runCatching { hvac.checkHvacTemperatureCurrentCapabilities({ c, list -> handler.post {
            list.firstOrNull()?.takeIf { readable(c.accessType) }?.let { cabinArea = it; hvac.registerHvacTemperatureCurrent(it, cabinListener); readClimate(v) }
        } }, {}) }
        runCatching { hvac.checkHvacTemperatureCapabilities({ c, bounds -> handler.post {
            bounds.keys.firstOrNull()?.takeIf { readable(c.accessType) }?.let { setArea = it; hvac.registerHvacTemperature(it, setListener); readClimate(v) }
        } }, {}) }
    }

    private fun readClimate(v: Vehicle) {
        val hvac = v.getHVAC()
        acArea?.let { a -> runCatching { hvac.getHvacAcState(a, { s -> handler.post { onClimate(climate.copy(ac = s)) } }, {}) } }
        powerArea?.let { a -> runCatching { hvac.getHvacPowerState(a, { s -> handler.post { onClimate(climate.copy(power = s)) } }, {}) } }
        fanArea?.let { a -> runCatching { hvac.getHvacFanSpeed(a, { s -> handler.post { onClimate(climate.copy(fan = s)) } }, {}) } }
        recircArea?.let { a -> runCatching { hvac.getHvacRecirculationState(a, { s -> handler.post { onClimate(climate.copy(recirc = s)) } }, {}) } }
        cabinArea?.let { a -> runCatching { hvac.getHvacTemperatureCurrent(a, { t -> handler.post { onClimate(climate.copy(cabinC = t?.toDouble()?.takeIf { it in -40.0..80.0 })) } }, {}) } }
    }

    // Gear has no capability check in SDK 2.3.2; a failed registration leaves the state as unsupported.
    /** Steering-wheel angle (Pleos SteeringWheel, CAR_DRIVE): lateral acceleration for tire wear without wheel-tick bias. */
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) { angle?.let { a -> handler.post { tireLoad.steering(a.toDouble(), SystemClock.elapsedRealtime()) } } }
        override fun onFailed(e: Exception) {}
    }
    private fun connectSteering(v: Vehicle) {
        runCatching { v.getSteeringWheel().checkSteeringWheelAngleCapabilities({ config, _ ->
            handler.post { if (readable(config.accessType)) runCatching { v.getSteeringWheel().registerSteeringWheelAngle(steeringListener) } }
        }, {}) }
    }

    private fun connectGear(v: Vehicle) {
        runCatching { v.getGear().registerGearSelection(gearListener); v.getGear().getCurrentGearSelectionState({ g -> handler.post { onGear(g) } }, {}) }
            .onFailure { gearState = "변속 상태 미지원" }
    }

    private fun connectOilLevel(v: Vehicle): Unit = runCatching {
        v.getSafety().checkEngineOilLevelStateCapabilities({ config, _ ->
            handler.post {
                if (!readable(config.accessType)) return@post
                v.getSafety().registerEngineOilLevelState(oilLevelListener)
                v.getSafety().getEngineOilLevelState({ s -> handler.post { onOilLevel(s) } }, {})
            }
        }, {})
    }.let {}

    private fun connectBrakeFluid(v: Vehicle): Unit = runCatching {
        v.getSafety().checkBrakeFluidLevelStateCapabilities({ config, _ ->
            handler.post {
                if (!readable(config.accessType)) return@post
                v.getSafety().registerBrakeFluidLevelState(brakeFluidListener)
                v.getSafety().getBrakeFluidLevelState({ s -> handler.post { onBrakeFluid(s) } }, {})
            }
        }, {})
    }.let {}

    private fun onBrakeFluid(state: OilLevelState?) {
        val next = when (state) { OilLevelState.NORMAL -> "정상"; OilLevelState.LOW -> "부족"; OilLevelState.CRITICALLY_LOW -> "매우 부족"; else -> null }
        if (next != brakeFluid) { brakeFluid = next; notifyChanged() }
    }

    private fun connectDrivingMode(v: Vehicle): Unit = runCatching {
        v.getDrivingMode().checkDrivingModeStateCapabilities({ config ->
            handler.post {
                if (!readable(config.accessType)) return@post
                v.getDrivingMode().registerDrivingModeState(drivingModeListener)
                v.getDrivingMode().getCurrentDrivingModeState({ m -> handler.post { drivingMode = m } }, {})
            }
        }, {})
    }.let {}

    /** AAOS VehicleIgnitionState: 1 LOCK, 2 OFF, 3 ACC, 4 ON, 5 START. Switching off starts the stop the engine cools in. */
    private fun onIgnition(state: Int) {
        ignitionSeen = true
        if (state in 1..2 && ::store.isInitialized) store.saveParkedAt(System.currentTimeMillis())
        if (state in 4..5 && lastIgnition in 1..3 && ::app.isInitialized) VoiceBrief.onDeparture(app)
        lastIgnition = state
    }

    private fun onOilLevel(state: OilLevelState?) {
        val next = when (state) { OilLevelState.NORMAL -> "정상"; OilLevelState.LOW -> "부족"; OilLevelState.CRITICALLY_LOW -> "매우 부족"; OilLevelState.HIGH -> "과다"; else -> null }
        if (next != oilLevel) { oilLevel = next; notifyChanged() }
    }

    /**
     * A trip leaving a long stop: the cabin at that moment says whether the car stood in the sun (blade heat ageing), and
     * after [COLD_START_HOURS] the engine has cooled enough for a heater warm-up to be timed in the first minutes.
     */
    private fun soak() {
        val since = System.currentTimeMillis() - (store.parkedAt() ?: 0L)
        if (since >= COLD_START_HOURS * 3_600_000) { coldStartUntil = SystemClock.elapsedRealtime() + COLD_START_WINDOW_MS; soakedHours = since / 3_600_000.0 }
        val cabin = climate.cabinC; val out = outsideC
        if (since < RubberAging.SOAK_PARK_MINUTES * 60_000 || cabin == null || out == null) return
        outsideDays.soak(java.time.LocalDate.now(), cabin, out); outsideDirty = true
    }

    private fun onGear(gear: VehicleGear?) {
        if (gear == null || gear == VehicleGear.GEAR_UNKNOWN) return
        val nowParked = gear == VehicleGear.GEAR_PARK
        gearState = if (nowParked) "지금 주차 중" else "운행 감지"
        if (!collecting) return
        val now = SystemClock.elapsedRealtime()
        // A trip opens only on a Park-to-drive change seen here; its start would otherwise be unknown.
        if (parked == true && !nowParked) {
            // A car with no ignition signal says it has set off only here; a short stop at the shops is not a new departure.
            if (System.currentTimeMillis() - (store.parkedAt() ?: 0L) >= DEPARTURE_STOP_MS) VoiceBrief.onDeparture(app)
            oilTrips.gear(false, now, outsideC); soak()
        }
        // Without an ignition signal Park stands in for switching off; with one, the stop starts at switch-off.
        if (nowParked && parked == false && !ignitionSeen) store.saveParkedAt(System.currentTimeMillis())
        if (nowParked) oilTrips.gear(true, now, outsideC)?.let { store.saveOilTrips(oilTrips); flush(); Reminders.evaluate(app) }
        parked = nowParked
        notifyChanged()
    }

    private fun connectBrake(v: Vehicle): Unit = runCatching {
        v.getBrake().checkBrakePedalStatusCapabilities({ config ->
            handler.post {
                if (!readable(config.accessType)) { setState(Usage.BRAKE, "페달 상태 미지원"); return@post }
                v.getBrake().registerBrakePedalStatus(brakeListener)
                v.getBrake().getCurrentBrakePedalStatus({ s -> handler.post { onBrake(s) } }, {})
            }
        }, { handler.post { setState(Usage.BRAKE, waiting); retry { connectBrake(v) } } })
    }.onFailure { retry { connectBrake(v) } }.let {}

    /**
     * AAOS properties the Pleos SDK lacks: outside temperature (CAR_EXTERIOR_ENVIRONMENT, granted at install) and vehicle
     * speed once the user has granted CAR_SPEED. Safe to call repeatedly.
     */
    fun connectCar(context: Context) {
        val c = car ?: runCatching { Car.createCar(context.applicationContext) }.getOrNull()?.also { created ->
            car = created
            runCatching { (created.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).registerCallback(outsideCallback, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, 0.1f) }
            // CAR_POWERTRAIN is a normal permission; the emulator's VHAL does not carry IGNITION_STATE, so this stays silent there.
            runCatching { (created.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).registerCallback(ignitionCallback, VehiclePropertyIds.IGNITION_STATE, 0f) }
        } ?: run { speedState = "차속 미지원"; return }
        if (speedRegistered) return
        if (!speedGranted(context)) { speedState = "차속 권한 필요"; return }
        runCatching {
            val manager = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            // 10 Hz resolves a stop from city speed into a dozen segments; the papers resample to 4 Hz.
            if (!manager.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 10f)) error("speed not registered")
            speedRegistered = true; speedState = "차속 연동"
            connectWheels(manager)
        }.onFailure { speedState = "차속 미지원" }
    }

    private fun onSpeed(mps: Double) {
        if (!collecting) return
        brakeWork.speed(mps, SystemClock.elapsedRealtime())
        tireLoad.speed(mps, SystemClock.elapsedRealtime())
        oilTrips.speed(mps, SystemClock.elapsedRealtime())
        // Distance by driving mode, for the share of sport-mode driving shown beside braking and tire load.
        speedKph?.let { prev -> driveModes.add((prev / 3.6 + mps) / 2 * 0.1, drivingMode) }
        // Crossing the ram-air speed changes the air through the filter while the blower is off.
        val wasRam = (speedKph ?: 0.0) >= FilterModel.RAM_KPH
        speedKph = mps * 3.6
        if (wasRam != (mps * 3.6 >= FilterModel.RAM_KPH) && !climate.blowing) filterDust.update(filterFlow(), SystemClock.elapsedRealtime(), at = VehiclePlaces.location)
        // Saved and repainted with the minute checkpoint; a 10 Hz feed must not write preferences per sample.
        brakeDirty = true
        speedState = "차속 연동"
    }

    /**
     * WHEEL_TICK (CAR_SPEED): the rear wheels' tick counts, with the distance per tick from the property config. Only a
     * car that reports all four wheels with a known tick size is used; the rest are judged on longitudinal driving alone.
     */
    private fun connectWheels(manager: CarPropertyManager) {
        runCatching {
            val config = manager.getCarPropertyConfig(VehiclePropertyIds.WHEEL_TICK) ?: error("no wheel ticks")
            val array = config.configArray
            if (array.size < 5 || array[0] and 0b1111 != 0b1111 || (1..4).any { array[it] <= 0 }) error("wheel ticks incomplete")
            tickMicrometres = (1..4).map { array[it].toDouble() }
            if (!manager.registerCallback(wheelCallback, VehiclePropertyIds.WHEEL_TICK, 10f)) error("wheel ticks not registered")
            wheelState = "바퀴 회전 연동"
        }.onFailure { wheelState = "바퀴 회전 미지원" }
    }

    /** [reset count, front left, front right, rear left, rear right] ticks. */
    private fun onWheels(v: Array<*>) {
        if (!collecting || v.size < 5 || tickMicrometres.size < 4) return
        val n = v.map { (it as? Number)?.toLong() ?: return }
        val reset = tickReset != null && tickReset != n[0]
        tickReset = n[0]
        tireLoad.wheels(n[3] * tickMicrometres[2] / 1e6, n[4] * tickMicrometres[3] / 1e6, reset)
    }

    private fun onBrake(state: Boolean?) {
        if (collecting) brakeWork.pedal(state == true, SystemClock.elapsedRealtime())
        if (brakeEdge.pressed(state) && collecting) {
            counters.getValue(Usage.BRAKE).add(1); store.save(Usage.BRAKE, counters.getValue(Usage.BRAKE)); brakeDirty = true
        }
        // Presses come every few seconds in traffic; the count repaints with the minute checkpoint, not per press.
        setState(Usage.BRAKE, if (state == null) "페달 상태 대기" else "차량에서 수신 중")
    }

    /** Writes what the minute checkpoint would, so a judgment made right after parking sees the whole trip. */
    private fun flush() {
        val now = SystemClock.elapsedRealtime()
        counters.forEach { (u, c) -> c.checkpoint(now); store.save(u, c) }
        store.saveBrakeWork(brakeWork); store.saveTireLoad(tireLoad); store.saveOutsideDays(outsideDays); brakeDirty = false; outsideDirty = false
    }

    private fun retry(block: () -> Unit) { handler.postDelayed({ if (vehicle != null) block() }, 10_000) }

    /** The switch has no change listener in SDK 2.3.2; it is read on wiper state changes and each minute while wiping. */
    private fun readWiperSwitch(v: Vehicle) {
        runCatching { v.getWiper().getCurrentWiperSwitch(front, { sw -> handler.post { onWiperSwitch(sw) } }, {}) }
    }
    private fun onWiperSwitch(sw: WiperSwitch?) {
        val next = when (sw) {
            WiperSwitch.CONTINUOUS_LEVEL_1, WiperSwitch.MIST -> WiperModel.Level.LOW
            WiperSwitch.CONTINUOUS_LEVEL_2, WiperSwitch.CONTINUOUS_LEVEL_3, WiperSwitch.CONTINUOUS_LEVEL_4, WiperSwitch.CONTINUOUS_LEVEL_5 -> WiperModel.Level.HIGH
            WiperSwitch.AUTO -> WiperModel.Level.AUTO
            WiperSwitch.INTERMITTENT_LEVEL_1, WiperSwitch.INTERMITTENT_LEVEL_2, WiperSwitch.INTERMITTENT_LEVEL_3,
            WiperSwitch.INTERMITTENT_LEVEL_4, WiperSwitch.INTERMITTENT_LEVEL_5 -> WiperModel.Level.INTERMITTENT
            else -> WiperModel.Level.UNKNOWN
        }
        if (next == wiperLevel) return
        // Close the interval at the old rate before switching.
        if (collecting && wiperOn) wiperCycles.update(next, SystemClock.elapsedRealtime())
        wiperLevel = next
    }

    private fun onWiper(state: WiperState?) {
        // SERVICE/OTHER positions are not wiping in normal use, so only ON counts.
        wiperOn = state == WiperState.ON
        vehicle?.let { readWiperSwitch(it) }
        states[Usage.WIPER] = if (wiperOn) "작동 중" else "차량에서 수신 중"
        recount(); notifyChanged()
    }

    private fun onClimate(next: Climate) {
        climate = next
        states[Usage.AC] = if (climate.acRunning) "작동 중" else "차량에서 수신 중"
        states[Usage.BLOWER] = if (climate.fan == null) "송풍 상태 대기" else if (climate.blowing) "송풍 중" else "차량에서 수신 중"
        recount(); timeCooldown(); notifyChanged()
    }

    /** Logged by date only while collecting, so it follows the car's own days of use; it feeds blade heat ageing. */
    private fun onOutside(celsius: Double) {
        outsideC = celsius
        brakeWork.outsideC = celsius; tireLoad.outsideC = celsius; wiperCycles.outsideC = celsius
        if (collecting && ::store.isInitialized) { outsideDays.add(java.time.LocalDateTime.now(), celsius); outsideDirty = true }
        timeCooldown()
    }

    private fun timeCooldown() {
        if (!::store.isInitialized) return
        val fanShare = climate.fan?.let { f -> fanMax?.takeIf { it > 0 }?.let { (f.toDouble() / it).coerceIn(0.0, 1.0) } }
        val now = SystemClock.elapsedRealtime()
        cooldown.update(collecting && climate.acRunning, climate.cabinC, climate.setC, outsideC, now, fanShare, climate.recirc, speedKph)
            ?.let { store.saveCooldown(cooldown); notifyChanged() }
        // The heater needs nothing but the blower; whether the A/C also runs to dry the air does not matter.
        // An EV heats from the first minute and needs no cold engine; an engine car is timed only right after a long stop.
        val electric = CarModel.current.powertrain == Powertrain.ELECTRIC
        heatup.update(collecting && climate.blowing && parked == false, climate.cabinC, climate.setC, outsideC, now, fanShare, climate.recirc, speedKph,
            canStart = electric || now < coldStartUntil, parkedHours = if (electric) null else soakedHours)
            ?.let { store.saveHeatup(heatup); notifyChanged() }
    }

    /** Applies the current vehicle state to every counter and persists any interval that closed. */
    private fun recount() {
        if (!::store.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        val running = mapOf(Usage.WIPER to wiperOn, Usage.AC to climate.acRunning, Usage.BLOWER to climate.blowing,
            Usage.FRESH to climate.freshBlowing, Usage.RECIRC to climate.recircBlowing)
        running.forEach { (u, on) -> counters.getValue(u).onState(collecting && on, now); store.save(u, counters.getValue(u)) }
        val wiping = collecting && wiperOn
        wiperCycles.update(if (wiping) wiperLevel else null, now); store.saveWiperCycles(wiperCycles)
        filterDust.update(filterFlow(), now, at = VehiclePlaces.location); store.saveFilterDust(filterDust)
        // Out of sight the microphone needs the service to have been granted it; otherwise Android records silence.
        if (wiping && (screenShown || backgroundMic)) mic.start(app) else mic.stop()
    }

    private fun setState(u: Usage, value: String) { if (states[u] != value) { states[u] = value; notifyChanged() } }

    private fun onWarning(key: String, on: Boolean) {
        val changed = store.warning(key)?.on != on
        store.saveWarning(key, CarWarning(on, System.currentTimeMillis()))
        if (changed) notifyChanged()
    }

    private fun notifyChanged() { listeners.toList().forEach { it() } }
}

/** Distance by the car's driving mode, in metres; unknown when the car does not report one. */
class DriveModes(var normalM: Double = 0.0, var ecoM: Double = 0.0, var sportM: Double = 0.0) {
    fun add(m: Double, mode: DrivingModeType?) {
        if (m <= 0) return
        when (mode) { DrivingModeType.SPORT -> sportM += m; DrivingModeType.ECO -> ecoM += m; DrivingModeType.NORMAL -> normalM += m; else -> {} }
    }
    val totalM get() = normalM + ecoM + sportM
    /** Share of reported distance in sport mode; null until 10 km carry a mode. */
    val sportShare: Double? get() = if (totalM < 10_000) null else sportM / totalM
    fun toJson(): JSONObject = JSONObject().put("n", normalM).put("e", ecoM).put("s", sportM)
    companion object { fun fromJson(o: JSONObject) = DriveModes(o.optDouble("n", 0.0), o.optDouble("e", 0.0), o.optDouble("s", 0.0)) }
}
