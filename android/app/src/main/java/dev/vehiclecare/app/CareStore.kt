package dev.vehiclecare.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Versioned, local-only state; records and the active baseline are stored atomically. */
class CareStore(context: Context, name: String = "vehicle-care-v1") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    var plans: MutableMap<Part, MaintenancePlan> = linkedMapOf()
        private set
    var records: MutableList<ServiceRecord> = mutableListOf()
        private set
    var mileage: Mileage? = null
    var vehicleName = "투싼"
    /** The state is the demo story ([DemoData]), not the owner's own records. */
    var demo = false
    /** The manual's severe conditions the owner says apply, by their number in [SevereUse.CONDITIONS]. */
    var severe: MutableSet<Int> = sortedSetOf()
    /** What the owner noticed about a part since its active record, by record id; a new record starts clean. */
    var reports: MutableMap<String, OwnerReport> = mutableMapOf()
    /** A booked service per part, until that service is recorded. */
    var bookings: MutableMap<Part, Booking> = mutableMapOf()
    /** The catalog model the owner confirmed or the car's name matched; null while the app only knows the car's identity. */
    var modelId: String? = null
        private set
    /** What the car last reported about itself, kept so a car outside the catalog still has its powertrain and shape. */
    var identity: VehicleIdentity? = null
        private set
    /** The model the schedules and part sizes come from. */
    val spec: ModelSpec get() = VehicleCatalog.byId(modelId) ?: if (identity == null) VehicleCatalog.DEFAULT else VehicleCatalog.generic(identity)
    private var recommendationsVersion = 0
    var loadFailed = false
        private set

    init { reload() }

    fun reload() {
        try {
            val raw = JSONObject(prefs.getString("state", "{}")!!)
            require(raw.optInt("version", 1) == 1)
            recommendationsVersion = raw.optInt("recommendationsVersion", 0)
            vehicleName = raw.optString("vehicleName", "투싼")
            demo = raw.optBoolean("demo", false)
            modelId = raw.optString("modelId").ifEmpty { null }
            identity = raw.optJSONObject("identity")?.let { o -> VehicleIdentity(o.optString("make").ifEmpty { null }, o.optString("model").ifEmpty { null },
                if (o.has("year")) o.getInt("year") else null, runCatching { Powertrain.valueOf(o.getString("powertrain")) }.getOrDefault(Powertrain.UNKNOWN),
                if (o.has("length")) o.getInt("length") else null, if (o.has("height")) o.getInt("height") else null) }
            severe = sortedSetOf<Int>().apply { raw.optJSONArray("severe")?.let { a -> repeat(a.length()) { add(a.getInt(it)) } } }
            bookings = mutableMapOf<Part, Booking>().apply {
                raw.optJSONObject("bookings")?.let { o -> o.keys().forEach { k -> runCatching { put(Part.valueOf(k), Booking.fromJson(o.getJSONObject(k))) } } }
            }
            reports = mutableMapOf<String, OwnerReport>().apply {
                raw.optJSONObject("reports")?.let { o -> o.keys().forEach { k -> runCatching { OwnerReport.valueOf(o.getString(k)) }.getOrNull()?.let { put(k, it) } } }
            }
            mileage = raw.optJSONObject("mileage")?.let { Mileage(it.getLong("km"), LocalDate.parse(it.getString("date")), it.optString("source", "manual"), it.optLong("observedAt", 0)) }
            val loadedRecords = mutableListOf<ServiceRecord>()
            raw.optJSONArray("records")?.let { array ->
                repeat(array.length()) { index -> loadedRecords += recordFromJson(array.getJSONObject(index)) }
            }
            val loadedPlans = linkedMapOf<Part, MaintenancePlan>()
            raw.optJSONArray("plans")?.let { array ->
                repeat(array.length()) { index ->
                    val obj = array.getJSONObject(index)
                    val part = Part.valueOf(obj.getString("part"))
                    loadedPlans[part] = MaintenancePlan(part, obj.getString("recordId"), obj.nullInt("months"),
                        obj.nullLong("km"), ServiceAction.valueOf(obj.getString("action")), obj.getString("source"), obj.optBoolean("paused"))
                }
            }
            records = loadedRecords
            plans = loadedPlans
            loadFailed = false
            CarModel.current = spec
        } catch (_: Exception) {
            // Preserve the original bytes. Never overwrite corrupt/unsupported state with an empty model.
            loadFailed = true
        }
    }

    fun applyRecommendations() {
        if (loadFailed || recommendationsVersion >= RecommendedCare.VERSION) return
        Part.entries.forEach { part ->
            val old = plans[part]
            plans[part] = RecommendedCare.plan(part, old?.recordId ?: "unrecorded-${part.name}", old?.paused ?: false, severe.isNotEmpty())
            clearDraft(part)
        }
        recommendationsVersion = RecommendedCare.VERSION
        save()
    }

    /**
     * Takes what the car reports. A name the catalog knows sets the model unless the owner chose one; otherwise the
     * identity alone decides the parts and picture. Returns true when the model changed.
     */
    fun identify(found: VehicleIdentity): Boolean {
        if (loadFailed) return false
        val before = spec.id
        identity = found
        if (modelId == null) VehicleCatalog.match(found)?.let { modelId = it.id }
        return applyModel(before)
    }

    /** The owner's choice of model; null goes back to what the car reports. */
    fun chooseModel(id: String?): Boolean {
        val before = spec.id
        modelId = id ?: identity?.let { VehicleCatalog.match(it)?.id }
        return applyModel(before)
    }

    private fun applyModel(before: String): Boolean {
        CarModel.current = spec
        val changed = spec.id != before
        if (changed) Part.entries.forEach { part ->
            val old = plans[part]
            plans[part] = RecommendedCare.plan(part, old?.recordId ?: "unrecorded-${part.name}", old?.paused ?: false, severe.isNotEmpty())
        }
        save()
        return changed
    }

    fun report(part: Part) = activeRecord(part)?.let { reports[it.id] }
    fun activeRecord(part: Part) = plans[part]?.let { p -> records.find { it.id == p.recordId && it.part == part } }

    fun save() {
        check(!loadFailed) { "저장된 데이터를 읽지 못했어요. 초기화 전에 확인해 주세요." }
        val raw = JSONObject().put("version", 1).put("vehicleName", vehicleName).put("recommendationsVersion", recommendationsVersion)
            .put("demo", demo)
            .put("modelId", modelId ?: "")
            .put("severe", JSONArray(severe.toList()))
            .put("reports", JSONObject().apply { reports.forEach { (k, v) -> put(k, v.name) } })
            .put("bookings", JSONObject().apply { bookings.forEach { (k, v) -> put(k.name, v.toJson()) } })
        identity?.let { i -> raw.put("identity", JSONObject().put("make", i.make ?: "").put("model", i.model ?: "").put("powertrain", i.powertrain.name)
            .apply { i.year?.let { put("year", it) }; i.lengthMm?.let { put("length", it) }; i.heightMm?.let { put("height", it) } }) }
        mileage?.let { raw.put("mileage", JSONObject().put("km", it.km).put("date", it.updatedOn.toString()).put("source", it.source).put("observedAt", it.observedAt)) }
        raw.put("records", JSONArray().apply { records.forEach { put(recordJson(it)) } })
        raw.put("plans", JSONArray().apply { plans.values.forEach { p ->
            put(JSONObject().put("part", p.part.name).put("recordId", p.recordId).put("months", p.intervalMonths)
                .put("km", p.intervalKm).put("action", p.action.name).put("source", p.source).put("paused", p.paused))
        } })
        check(prefs.edit().putString("state", raw.toString()).commit()) { "저장 공간을 확인해 주세요." }
    }

    fun draft(part: Part): JSONObject? = prefs.getString("draft-${part.name}", null)?.let {
        runCatching { JSONObject(it) }.getOrNull()
    }
    fun saveDraft(part: Part, value: JSONObject) { prefs.edit().putString("draft-${part.name}", value.toString()).apply() }
    fun clearDraft(part: Part) { prefs.edit().remove("draft-${part.name}").apply() }
    fun reset() {
        check(prefs.edit().clear().commit())
        recommendationsVersion = 0; mileage = null; vehicleName = "투싼"; demo = false; modelId = null; identity = null; CarModel.current = VehicleCatalog.DEFAULT; severe = sortedSetOf(); reports.clear(); bookings.clear(); plans.clear(); records.clear(); loadFailed = false
    }

    companion object {
        private fun JSONObject.nullLong(key: String): Long? = if (isNull(key)) null else getLong(key)
        private fun JSONObject.nullInt(key: String): Int? = if (isNull(key)) null else getInt(key)
        fun recordJson(r: ServiceRecord) = JSONObject().put("id", r.id).put("part", r.part.name)
            .put("date", r.date?.toString()).put("km", r.odometerKm).put("action", r.action.name).apply { r.measuredMm?.let { put("mm", it) } }
        fun recordFromJson(o: JSONObject) = ServiceRecord(o.getString("id"), Part.valueOf(o.getString("part")),
            o.optString("date").takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDate::parse),
            o.nullLong("km"), ServiceAction.valueOf(o.getString("action")), if (o.has("mm") && !o.isNull("mm")) o.getDouble("mm") else null)
    }
}
