package dev.vehiclecare.app

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager

enum class Powertrain(val label: String) {
    GASOLINE("가솔린"), HYBRID("하이브리드"), ELECTRIC("전기"), LPG("LPG"), DIESEL("디젤"), UNKNOWN("동력원 미확인");

    companion object {
        /** AAOS VehicleFuelType values the car reports in INFO_FUEL_TYPE. */
        fun ofFuelTypes(types: IntArray?, evCapacityWh: Float?): Powertrain {
            val t = types?.toSet().orEmpty()
            val electric = 10 in t
            val liquid = t.any { it in setOf(1, 2, 3, 4, 5, 6, 7) }
            return when {
                electric && liquid -> HYBRID
                electric -> ELECTRIC
                3 in t || 4 in t || 5 in t -> DIESEL
                7 in t -> LPG
                liquid -> GASOLINE
                (evCapacityWh ?: 0f) > 0f -> ELECTRIC
                else -> UNKNOWN
            }
        }
    }
}

/** Body class, from the catalog or, for an unknown car, from its exterior dimensions. */
enum class BodyType(val label: String) { SUV("SUV"), SEDAN("세단"), COMPACT("경차"), MPV("다목적") }

/** A service interval: whichever of the two comes first. */
data class Interval(val km: Long? = null, val months: Int? = null)

/**
 * One model and powertrain as its official owner's manual describes it: the service intervals the app counts against,
 * and the part sizes it recommends. Values the manual does not give stay null and the app falls back to a general
 * reference for them, saying so.
 */
data class ModelSpec(
    val id: String, val name: String, val code: String, val year: Int, val powertrain: Powertrain, val body: BodyType, val engine: String,
    val engineOil: Interval?, val engineOilSevere: Interval?, val brakeInspect: Interval?, val cabinFilter: Interval?,
    val acInspect: Interval?, val tireRotationKm: Long?,
    /** Every size the manual lists; it does not say which trim gets which. [tirePressureKpa] only when one value fits all. */
    val tires: List<String>, val tirePressureKpa: Int?,
    /** Driver and passenger blade lengths from Hyundai Mobis's fitment chart (no official manual lists them); null when not found. */
    val wipers: Pair<Int, Int>?, val oilGrade: String?, val oilCapacityL: Double?,
    val scheduleUrl: String?, val keywords: List<String>,
) {
    val title get() = "$name ${powertrain.label}"
    /** The tires as a buyer says them: one size, or the wheel range when the manual lists several. */
    val tireLabel get() = when {
        tires.isEmpty() -> null
        tires.size == 1 -> tires.first()
        else -> tires.mapNotNull { Regex("R(\\d+)").find(it)?.groupValues?.get(1)?.toInt() }.let { r -> "${r.min()}~${r.max()}인치" }
    }
    /** Electric cars have no engine oil; every other part applies to every powertrain. */
    val parts get() = Part.entries.filter { it != Part.ENGINE_OIL || powertrain != Powertrain.ELECTRIC }
}

/** What the car says about itself through AAOS INFO_* properties (android.car.permission.CAR_INFO, a normal permission). */
data class VehicleIdentity(val make: String?, val model: String?, val year: Int?, val powertrain: Powertrain, val lengthMm: Int?, val heightMm: Int?) {
    val label get() = listOfNotNull(make, model, year?.toString()).joinToString(" ")
    /** Shape from the exterior dimensions, for a car the catalog does not know. */
    val body get() = when {
        lengthMm == null -> BodyType.SUV
        lengthMm < 3_700 -> BodyType.COMPACT
        (heightMm ?: 0) >= 1_850 -> BodyType.MPV
        (heightMm ?: 0) >= 1_600 -> BodyType.SUV
        else -> BodyType.SEDAN
    }

    companion object {
        const val PERMISSION = "android.car.permission.CAR_INFO"

        fun read(context: Context): VehicleIdentity? {
            if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return null
            return runCatching {
                val car = Car.createCar(context.applicationContext)
                try {
                    val m = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                    fun <T> get(id: Int): T? = runCatching { @Suppress("UNCHECKED_CAST") (m.getProperty<Any>(id, 0)?.value as T?) }.getOrNull()
                    val dims = get<Array<Int>>(VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS)
                    VehicleIdentity(get<String>(VehiclePropertyIds.INFO_MAKE)?.trim()?.ifEmpty { null }, get<String>(VehiclePropertyIds.INFO_MODEL)?.trim()?.ifEmpty { null },
                        get<Int>(VehiclePropertyIds.INFO_MODEL_YEAR),
                        Powertrain.ofFuelTypes(get<Array<Int>>(VehiclePropertyIds.INFO_FUEL_TYPE)?.toIntArray(), get<Float>(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)),
                        dims?.getOrNull(1), dims?.getOrNull(0))
                } finally { car.disconnect() }
            }.getOrNull()
        }
    }
}

/** The model the running app counts against; [CareStore] keeps it in step with what is saved. */
object CarModel {
    @Volatile var current: ModelSpec = VehicleCatalog.DEFAULT
}

/**
 * The models the app knows, from Hyundai's official web owner's manuals (see materials/07_model_specs). A car the
 * catalog does not know gets [generic]: its powertrain decides the parts, its size the picture, and general references
 * the intervals.
 */
object VehicleCatalog {
    // Generated from materials/07_model_specs/specs.json: intervals, tires and oil from each official owner's manual.
    // Cabin filter: 12 months as the manuals' replacement interval; Casper's air-conditioning chapter adds 15,000 km.
    // Wipers: only the Mobis chart rows marked medium confidence; Ioniq 5 and Casper share a row with other models.
    val MODEL_NX4_2026_GASOLINE_1_6T = ModelSpec("nx4-2026-gasoline-1.6t", "투싼", "NX4", 2026, Powertrain.GASOLINE, BodyType.SUV, "1.6 터보",
        engineOil = Interval(10_000, 12), engineOilSevere = Interval(5000, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("235/65 R17", "235/60 R18", "235/55 R19"), tirePressureKpa = 240, wipers = 650 to 400, oilGrade = "0W-20", oilCapacityL = 4.8,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/NX4/2026/ko_KR/id6b219dd157c.html", keywords = listOf("tucson", "투싼", "nx4"))
    val MODEL_NX4_2026_HYBRID_1_6T = ModelSpec("nx4-2026-hybrid-1.6t", "투싼", "NX4", 2026, Powertrain.HYBRID, BodyType.SUV, "1.6 터보",
        engineOil = Interval(10_000, 12), engineOilSevere = Interval(5000, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("235/65 R17", "235/60 R18", "235/55 R19"), tirePressureKpa = 240, wipers = 650 to 400, oilGrade = "0W-20", oilCapacityL = 4.8,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/NX4HEV/2026/ko_KR/id6b219dd157c.html", keywords = listOf("tucson", "투싼", "nx4"))
    val MODEL_CN7_2026_GASOLINE_1_6 = ModelSpec("cn7-2026-gasoline-1.6", "아반떼", "CN7", 2026, Powertrain.GASOLINE, BodyType.SEDAN, "1.6",
        engineOil = Interval(15_000, 12), engineOilSevere = Interval(7500, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("195/65 R15", "205/55 R16", "225/45 R17", "235/40 R18"), tirePressureKpa = null, wipers = 600 to 450, oilGrade = "0W-20", oilCapacityL = 3.8,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_pdf/CN7/2026/ko_KR", keywords = listOf("avante", "elantra", "아반떼", "cn7"))
    val MODEL_CN7_2026_HYBRID_1_6 = ModelSpec("cn7-2026-hybrid-1.6", "아반떼", "CN7", 2026, Powertrain.HYBRID, BodyType.SEDAN, "1.6",
        engineOil = Interval(15_000, 12), engineOilSevere = Interval(7500, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("205/55 R16", "225/45 R17", "235/40 R18"), tirePressureKpa = null, wipers = 600 to 450, oilGrade = "0W-20", oilCapacityL = 3.8,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_pdf/CN7HEV/2026/ko_KR", keywords = listOf("avante", "elantra", "아반떼", "cn7"))
    val MODEL_DN8_2026_GASOLINE_2_0 = ModelSpec("dn8-2026-gasoline-2.0", "쏘나타 디 엣지", "DN8", 2026, Powertrain.GASOLINE, BodyType.SEDAN, "2.0",
        engineOil = Interval(15_000, 12), engineOilSevere = Interval(7500, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("205/65 R16", "215/55 R17", "235/45 R18", "245/40 R19"), tirePressureKpa = 240, wipers = 650 to 450, oilGrade = "0W-20", oilCapacityL = 4.3,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/DN8/2026/ko_KR/id6b219dd157c.html", keywords = listOf("sonata", "쏘나타", "dn8"))
    val MODEL_DN8_2026_GASOLINE_1_6T = ModelSpec("dn8-2026-gasoline-1.6t", "쏘나타 디 엣지", "DN8", 2026, Powertrain.GASOLINE, BodyType.SEDAN, "1.6 터보",
        engineOil = Interval(10_000, 12), engineOilSevere = Interval(5000, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("205/65 R16", "215/55 R17", "235/45 R18", "245/40 R19"), tirePressureKpa = 240, wipers = 650 to 450, oilGrade = "0W-20", oilCapacityL = 4.8,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/DN8/2026/ko_KR/id6b219dd157c.html", keywords = listOf("sonata", "쏘나타", "dn8"))
    val MODEL_DN8_2026_LPG_2_0 = ModelSpec("dn8-2026-lpg-2.0", "쏘나타 디 엣지", "DN8", 2026, Powertrain.LPG, BodyType.SEDAN, "2.0",
        engineOil = Interval(15_000, 12), engineOilSevere = Interval(7500, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("205/65 R16", "215/55 R17", "235/45 R18", "245/40 R19"), tirePressureKpa = 240, wipers = 650 to 450, oilGrade = "0W-20", oilCapacityL = 4.3,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/DN8/2026/ko_KR/id6b219dd157c.html", keywords = listOf("sonata", "쏘나타", "dn8"))
    val MODEL_DN8_2026_HYBRID_2_0 = ModelSpec("dn8-2026-hybrid-2.0", "쏘나타 디 엣지", "DN8", 2026, Powertrain.HYBRID, BodyType.SEDAN, "2.0",
        engineOil = Interval(15_000, 12), engineOilSevere = Interval(7500, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("205/65 R16", "215/55 R17", "235/45 R18"), tirePressureKpa = 240, wipers = 650 to 450, oilGrade = "0W-16", oilCapacityL = 4.3,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/DN8HEV/2026/ko_KR/id6b219dd157c.html", keywords = listOf("sonata", "쏘나타", "dn8"))
    val MODEL_MX5_2026_GASOLINE_2_5T = ModelSpec("mx5-2026-gasoline-2.5t", "싼타페", "MX5", 2026, Powertrain.GASOLINE, BodyType.SUV, "2.5 터보",
        engineOil = Interval(10_000, 12), engineOilSevere = Interval(5000, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("235/60 R18", "255/45 R20", "245/45 R21"), tirePressureKpa = null, wipers = null, oilGrade = "0W-30", oilCapacityL = 5.8,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/MX5/2026/ko_KR/id6b219dd157c.html", keywords = listOf("santafe", "싼타페", "mx5"))
    val MODEL_MX5_2026_HYBRID_1_6T = ModelSpec("mx5-2026-hybrid-1.6t", "싼타페", "MX5", 2026, Powertrain.HYBRID, BodyType.SUV, "1.6 터보",
        engineOil = Interval(10_000, 12), engineOilSevere = Interval(5000, 6), brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("235/60 R18", "255/45 R20"), tirePressureKpa = 240, wipers = null, oilGrade = "0W-20", oilCapacityL = 4.8,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/MX5HEV/2026/ko_KR/id6b219dd157c.html", keywords = listOf("santafe", "싼타페", "mx5"))
    val MODEL_NE_2026_ELECTRIC_EV = ModelSpec("ne-2026-electric-ev", "아이오닉 5", "NE", 2026, Powertrain.ELECTRIC, BodyType.SUV, "",
        engineOil = null, engineOilSevere = null, brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("235/55 R19", "255/45 R20"), tirePressureKpa = null, wipers = null, oilGrade = null, oilCapacityL = null,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/NE1/2026/ko_KR/topic_gkm_cpv_k1c.html", keywords = listOf("ioniq5", "아이오닉5", "ne1"))
    val MODEL_AX1_2026_GASOLINE_1_0 = ModelSpec("ax1-2026-gasoline-1.0", "캐스퍼", "AX1", 2026, Powertrain.GASOLINE, BodyType.COMPACT, "1.0",
        engineOil = Interval(15_000, 12), engineOilSevere = Interval(7500, 6), brakeInspect = Interval(20000), cabinFilter = Interval(15_000, 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("185/65 R15", "205/45 R17"), tirePressureKpa = null, wipers = null, oilGrade = "0W-20", oilCapacityL = 3.1,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/AX/2026/ko_KR/id6b219dd157c.html", keywords = listOf("casper", "캐스퍼", "ax1"))
    val MODEL_AX1_2026_GASOLINE_1_0T = ModelSpec("ax1-2026-gasoline-1.0t", "캐스퍼", "AX1", 2026, Powertrain.GASOLINE, BodyType.COMPACT, "1.0 터보",
        engineOil = Interval(10_000, 12), engineOilSevere = Interval(5000, 6), brakeInspect = Interval(20000), cabinFilter = Interval(15_000, 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("185/65 R15", "205/45 R17"), tirePressureKpa = null, wipers = null, oilGrade = "0W-30", oilCapacityL = 3.6,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/AX/2026/ko_KR/id6b219dd157c.html", keywords = listOf("casper", "캐스퍼", "ax1"))
    val MODEL_AX1EV_2026_ELECTRIC_EV = ModelSpec("ax1ev-2026-electric-ev", "캐스퍼 일렉트릭", "AX1 EV", 2026, Powertrain.ELECTRIC, BodyType.COMPACT, "",
        engineOil = null, engineOilSevere = null, brakeInspect = Interval(10000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10000,
        tires = listOf("185/65 R15", "205/45 R17"), tirePressureKpa = null, wipers = null, oilGrade = null, oilCapacityL = null,
        scheduleUrl = "https://ownersmanual.hyundai.com/full_webhelp/AXEV/2026/ko_KR/topic_gkm_cpv_k1c.html", keywords = listOf("casper", "캐스퍼", "ax1", "inster"))

    val models: List<ModelSpec> = listOf(MODEL_NX4_2026_GASOLINE_1_6T, MODEL_NX4_2026_HYBRID_1_6T, MODEL_CN7_2026_GASOLINE_1_6, MODEL_CN7_2026_HYBRID_1_6, MODEL_DN8_2026_GASOLINE_2_0, MODEL_DN8_2026_GASOLINE_1_6T, MODEL_DN8_2026_LPG_2_0, MODEL_DN8_2026_HYBRID_2_0, MODEL_MX5_2026_GASOLINE_2_5T, MODEL_MX5_2026_HYBRID_1_6T, MODEL_NE_2026_ELECTRIC_EV, MODEL_AX1_2026_GASOLINE_1_0, MODEL_AX1_2026_GASOLINE_1_0T, MODEL_AX1EV_2026_ELECTRIC_EV)

    val TUCSON_GASOLINE = MODEL_NX4_2026_GASOLINE_1_6T
    val DEFAULT = TUCSON_GASOLINE

    // The first catalog id, kept so a saved choice still resolves.
    fun byId(id: String?) = models.find { it.id == id } ?: if (id == "tucson-2026-gasoline") TUCSON_GASOLINE else null

    /** The known model the car reports, by name and powertrain; null when the name is not in the catalog. */
    fun match(identity: VehicleIdentity): ModelSpec? {
        val name = identity.model?.lowercase()?.replace(" ", "")?.replace("-", "") ?: return null
        val candidates = models.filter { m -> m.keywords.any { name.contains(it) } }.let { c -> c.filter { it.powertrain == identity.powertrain }.ifEmpty { c } }
        // Engines the car does not tell apart (Sonata 2.0 or 1.6 turbo): take the shorter oil interval, so a reminder is never late.
        return candidates.minByOrNull { it.engineOil?.km ?: Long.MAX_VALUE }
    }

    /** A car outside the catalog: parts by powertrain, picture by size, intervals from general references. */
    fun generic(identity: VehicleIdentity?, powertrain: Powertrain = identity?.powertrain ?: Powertrain.UNKNOWN) = ModelSpec(
        "generic-${powertrain.name.lowercase()}", identity?.model ?: "내 차", "", identity?.year ?: 0, powertrain, identity?.body ?: BodyType.SUV, "",
        engineOil = if (powertrain == Powertrain.ELECTRIC) null else Interval(10_000, 12), engineOilSevere = if (powertrain == Powertrain.ELECTRIC) null else Interval(5_000, 6),
        brakeInspect = Interval(10_000), cabinFilter = Interval(months = 12), acInspect = Interval(months = 12), tireRotationKm = 10_000,
        tires = emptyList(), tirePressureKpa = null, wipers = null, oilGrade = null, oilCapacityL = null, scheduleUrl = null, keywords = emptyList())
}
