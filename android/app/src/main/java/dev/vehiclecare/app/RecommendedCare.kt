package dev.vehiclecare.app

/** Reference schedules, not measured wear. Vehicle-specific service guidance takes priority. */
object RecommendedCare {
    const val VERSION = 3
    const val HYUNDAI = "https://ownersmanual.hyundai.com/full_webhelp/NX4/2026/ko_KR/id6b219dd157c.html"
    private const val BOSCH_WIPER = "https://www.boschaftermarket.com/xrm/media/images/country_specific/za/parts_9/wba_tipps_tricks_wischblattpflege_2pager_en_87039.pdf"
    private const val BOSCH_FILTER = "https://www.bosch-presse.de/pressportal/de/en/bosch-cabin-filter-range-covers-96-percent-of-all-passenger-cars-and-93-percent-of-all-hybrid-and-electric-vehicles-in-europe-245125.html"
    private const val MICHELIN = "https://www.michelin.co.kr/auto/advice/change-tyres/how-long-do-tyres-last"

    /**
     * The interval for [part] on [spec], from its owner's manual where the manual gives one and from the part makers'
     * general advice where it does not. [severe]: the owner drives in one of the manual's severe conditions, which
     * shortens the engine oil interval.
     */
    fun plan(part: Part, recordId: String = "unrecorded-${part.name}", paused: Boolean = false, severe: Boolean = false, spec: ModelSpec = CarModel.current): MaintenancePlan {
        val manual = if (spec.scheduleUrl != null) "${spec.name} ${spec.powertrain.label} 취급설명서" else "일반 권장"
        val oil = (if (severe) spec.engineOilSevere else null) ?: spec.engineOil ?: Interval(10_000, 12)
        val (interval, source) = when (part) {
            Part.ENGINE_OIL -> oil to (if (severe && spec.engineOilSevere != null) "$manual · 가혹 조건" else manual)
            Part.BRAKE_PAD -> (spec.brakeInspect ?: Interval(10_000)) to manual
            Part.HVAC -> (spec.acInspect ?: Interval(months = 12)) to manual
            Part.CABIN_FILTER -> spec.cabinFilter?.let { it to manual } ?: (Interval(15_000, 12) to "Bosch · 1년 또는 15,000 km 교체 참고")
            Part.WIPER -> Interval(months = 12) to "Bosch · 연 1회 교체 참고"
            Part.TIRE -> Interval(months = 1) to "Michelin · 매월 상태 점검 · $manual · ${String.format(java.util.Locale.KOREA, "%,d", spec.tireRotationKm ?: 10_000)} km 위치 교환"
        }
        return MaintenancePlan(part, recordId, interval.months, interval.km, part.defaultAction, source, paused)
    }
    fun sourceUrl(part: Part, spec: ModelSpec = CarModel.current) = when (part) {
        Part.WIPER -> BOSCH_WIPER
        Part.CABIN_FILTER -> if (spec.cabinFilter != null) spec.scheduleUrl ?: HYUNDAI else BOSCH_FILTER
        Part.TIRE -> MICHELIN
        else -> spec.scheduleUrl ?: HYUNDAI
    }
}

/**
 * The Tucson 2026 manual's severe driving conditions, numbered as its severe-condition table refers to them. Driving often
 * in any of them changes engine oil and filter to every 5,000 km or 6 months; the table also sends brake discs and pads
 * and the cabin air filter to inspection as needed under some of them. The app measures short trips and outside
 * temperature itself; the rest only the owner knows, and the manual gives no threshold for "often".
 */
object SevereUse {
    const val SOURCE = "https://ownersmanual.hyundai.com/full_webhelp/NX4/2026/ko_KR/id7b77f1c5ffe.html"
    val CONDITIONS = listOf(
        "짧은 거리를 반복적으로 주행", "모래, 먼지가 많은 지역 주행", "과다한 공회전 사용", "교통 체증이 심한 구역 주행",
        "잦은 험로(모랫길, 자갈길, 눈길, 비포장 도로) 주행", "산길, 오르막길, 내리막길 주행", "경찰차, 택시, 상용차, 견인차 등으로 사용",
        "잦은 고속 주행 및 급가감속", "정지와 출발의 잦은 반복", "소금, 부식 물질 위 또는 한랭 지역 주행",
        "견인용, 캠핑용으로 사용 또는 지붕 위 짐칸 탑재 주행", "추천하지 않는 엔진 오일 주입(광유, 세미 합성유, 낮은 오일 등급 외)")
    /** Condition numbers (1-based) under which the table checks each part as needed. */
    private val CHECK = mapOf(Part.BRAKE_PAD to setOf(2, 4, 5, 6, 7, 8, 9, 10), Part.CABIN_FILTER to setOf(2))
    fun checks(part: Part, chosen: Set<Int>) = CHECK[part]?.intersect(chosen).orEmpty().isNotEmpty()
}
