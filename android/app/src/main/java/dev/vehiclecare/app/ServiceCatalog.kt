package dev.vehiclecare.app

import org.json.JSONObject
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How a job can be done: someone comes to the car, the car goes to a shop, or the owner does it. */
enum class ServiceMode(val label: String, val pitch: String) {
    VISIT("방문 교체", "집이나 회사 주차장으로 와서 해요"),
    SHOP("정비소", "가까운 곳에 예약하고 가요"),
    SELF("직접 교체", "부품만 받아 직접 바꿔요"),
}

/** One priced line of an offer, in won. */
data class ServiceLine(val name: String, val won: Int)

/**
 * A provider's offer for one job. Coordinates exist only for shops, so the car's navigation can be sent there.
 * Everything here is sample data until partners are signed; [SAMPLE] is shown with it.
 */
data class Offer(val id: String, val mode: ServiceMode, val provider: String, val rating: Double, val reviews: Int, val minutes: Int,
                 val lines: List<ServiceLine>, val tags: List<String>, val slots: List<LocalDateTime>,
                 val lat: Double? = null, val lon: Double? = null, val address: String? = null, val distanceKm: Double? = null) {
    val total get() = lines.sumOf { it.won }
}

/** A booked offer, kept on the part until the service is recorded. Payment is on site until the Pleos Payment SDK opens. */
data class Booking(val offerId: String, val provider: String, val mode: ServiceMode, val at: LocalDateTime, val total: Int, val paid: Boolean,
                   val lat: Double? = null, val lon: Double? = null, val address: String? = null) {
    /** The shop again, for a route request from the booking. */
    val offer get() = Offer(offerId, mode, provider, 0.0, 0, 0, emptyList(), emptyList(), listOf(at), lat, lon, address)
    fun toJson(): JSONObject = JSONObject().put("offer", offerId).put("provider", provider).put("mode", mode.name)
        .put("at", at.toString()).put("total", total).put("paid", paid)
        .apply { lat?.let { put("lat", it) }; lon?.let { put("lon", it) }; address?.let { put("address", it) } }
    companion object {
        fun of(o: Offer, at: LocalDateTime) = Booking(o.id, o.provider, o.mode, at, o.total, false, o.lat, o.lon, o.address)
        fun fromJson(o: JSONObject) = Booking(o.getString("offer"), o.getString("provider"), ServiceMode.valueOf(o.getString("mode")),
            LocalDateTime.parse(o.getString("at")), o.getInt("total"), o.optBoolean("paid"),
            if (o.has("lat")) o.getDouble("lat") else null, if (o.has("lon")) o.getDouble("lon") else null, o.optString("address").ifEmpty { null })
    }
}

/**
 * Which ways a job can be done and what each costs. The split follows what the job needs: brake work needs a lift and a
 * brake check after fitting, and A/C work needs refrigerant recovery and charging equipment, so both stay in a shop;
 * blades and cabin filters fit without tools, so the owner can do them; used oil and tire fitting machines rule out
 * doing those yourself, but both are offered as mobile services.
 *
 * Providers, ratings, prices and slots are samples for the demonstration, not real businesses or quotes.
 */
object ServiceCatalog {
    const val SAMPLE = "시연용 예시 · 실제 업체·가격 아님"

    fun modes(part: Part, task: ServiceAction): List<ServiceMode> = when (part) {
        Part.WIPER, Part.CABIN_FILTER -> listOf(ServiceMode.VISIT, ServiceMode.SHOP, ServiceMode.SELF)
        Part.ENGINE_OIL -> listOf(ServiceMode.VISIT, ServiceMode.SHOP)
        Part.TIRE -> if (task == ServiceAction.INSPECT) listOf(ServiceMode.SHOP) else listOf(ServiceMode.VISIT, ServiceMode.SHOP)
        Part.BRAKE_PAD, Part.HVAC -> listOf(ServiceMode.SHOP)
    }

    /** What the provider does, in a few words, for the card; with the car's part size where the job needs one. */
    fun job(part: Part, task: ServiceAction, spec: ModelSpec = CarModel.current): String = when (part) {
        Part.WIPER -> spec.wipers?.let { (d, p) -> "블레이드 $d·$p mm 교체" } ?: "앞 블레이드 한 쌍 교체"
        Part.CABIN_FILTER -> "실내 공기 필터 교체"
        Part.ENGINE_OIL -> listOfNotNull("엔진오일", spec.oilGrade, spec.oilCapacityL?.let { "${fmtL(it)} L" }).joinToString(" ") + "·필터 교체"
        Part.BRAKE_PAD -> if (task == ServiceAction.REPLACE) "앞 브레이크 패드 교체" else "브레이크 디스크·패드 점검"
        Part.TIRE -> when (task) { ServiceAction.REPLACE -> spec.tireLabel?.let { "$it 타이어 4개 교체" } ?: "타이어 4개 교체"; ServiceAction.ROTATE -> "타이어 위치 교환"
            else -> spec.tirePressureKpa?.let { "공기압 $it kPa·마모 점검" } ?: "공기압·마모 점검" }
        Part.HVAC -> "냉방·난방 성능 점검"
    }
    private fun fmtL(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    /** Why a mode is missing. */
    fun limits(part: Part, task: ServiceAction): String? = when (part) {
        Part.BRAKE_PAD -> "브레이크는 리프트와 장착 후 제동 확인이 필요해 정비소에서만 받아요"
        Part.HVAC -> "냉매 회수·충전 장비가 필요해 정비소에서만 받아요"
        Part.TIRE -> if (task == ServiceAction.INSPECT) "공기압·마모 점검은 가까운 정비소에서 무료로 받는 곳이 많아요" else "타이어 장착·밸런스 장비가 필요해 직접 교체는 빼 두었어요"
        Part.ENGINE_OIL -> "폐유 처리 때문에 직접 교체는 빼 두었어요"
        else -> null
    }

    private fun lines(part: Part, task: ServiceAction, mode: ServiceMode, spec: ModelSpec = CarModel.current): List<ServiceLine> {
        val trip = if (mode == ServiceMode.VISIT) listOf(ServiceLine("출장", if (part == Part.TIRE) 30_000 else 15_000)) else emptyList()
        val labor = { won: Int -> if (mode == ServiceMode.SELF) emptyList() else listOf(ServiceLine("공임", won)) }
        val body = when (part) {
            Part.WIPER -> listOf(ServiceLine(spec.wipers?.let { (d, p) -> "앞 블레이드 $d·$p mm 한 쌍" } ?: "앞 블레이드 한 쌍", 32_000)) + labor(if (mode == ServiceMode.SHOP) 0 else 5_000)
            Part.CABIN_FILTER -> listOf(ServiceLine("활성탄 실내 필터", 24_000)) + labor(if (mode == ServiceMode.SHOP) 5_000 else 5_000)
            Part.ENGINE_OIL -> listOf(ServiceLine(listOfNotNull("합성 엔진오일", spec.oilGrade, spec.oilCapacityL?.let { "${fmtL(it)} L" }).joinToString(" "), 62_000), ServiceLine("오일 필터", 12_000)) + labor(20_000)
            Part.BRAKE_PAD -> if (task == ServiceAction.REPLACE) listOf(ServiceLine("앞 브레이크 패드", 68_000)) + labor(40_000)
                else listOf(ServiceLine("브레이크 디스크·패드 점검", 10_000))
            Part.TIRE -> when (task) {
                ServiceAction.REPLACE -> listOf(ServiceLine(spec.tireLabel?.let { "타이어 $it 4개" } ?: "타이어 4개", 660_000), ServiceLine("장착·밸런스 4개", 40_000))
                ServiceAction.ROTATE -> listOf(ServiceLine("위치 교환·밸런스", 30_000))
                else -> listOf(ServiceLine("공기압·마모 점검", 0))
            }
            Part.HVAC -> listOf(ServiceLine("냉방·난방 성능 점검", 20_000), ServiceLine("냉매 R-1234yf 보충(필요할 때)", 0))
        }
        return body.filter { it.won > 0 || it.name.contains("점검") } + trip
    }

    /** Sample offers for a job; shops are placed around [origin] so distance and route requests work from the car's position. */
    fun offers(part: Part, task: ServiceAction, mode: ServiceMode, origin: Pair<Double, Double>?, now: LocalDateTime): List<Offer> {
        val (lat0, lon0) = origin ?: (37.4979 to 127.0276)
        val day = now.toLocalDate().plusDays(1)
        val slots = { hours: List<Double> -> hours.map { h -> day.plusDays((h / 24).toLong()).atTime(((h % 24).toInt()), ((h % 1) * 60).roundToInt()) } }
        fun shop(id: String, name: String, rating: Double, reviews: Int, minutes: Int, dLat: Double, dLon: Double, address: String, tags: List<String>, hours: List<Double>) =
            Offer("$id-${part.name}-${task.name}", mode, name, rating, reviews, minutes, lines(part, task, mode), tags, slots(hours),
                lat0 + dLat, lon0 + dLon, address, km(lat0, lon0, lat0 + dLat, lon0 + dLon))
        fun visit(id: String, name: String, rating: Double, reviews: Int, extra: Int, tripOff: Int, tags: List<String>, hours: List<Double>) =
            Offer("$id-${part.name}-${task.name}", mode, name, rating, reviews, minutesFor(part, task) + extra,
                lines(part, task, mode).map { if (it.name == "출장") it.copy(won = it.won - tripOff) else it }, tags, slots(hours))
        // The second shop on, labour a little cheaper and the check free, as many do.
        fun cheaper(o: Offer) = o.copy(lines = o.lines.map { when { it.name == "공임" && it.won > 0 -> it.copy(won = it.won - 5_000); it.name.endsWith("점검") -> it.copy(won = 0); else -> it } },
            tags = o.tags + listOfNotNull(if (o.lines.any { it.name.endsWith("점검") }) "점검 무료" else null))
        return when (mode) {
            ServiceMode.VISIT -> listOf(
                visit("visit-a", "바로케어 방문정비", 4.8, 312, 0, 0, listOf("정품 부품", "작업 사진 전송"), listOf(10.0, 14.5, 34.0)),
                visit("visit-b", "온사이트 카서비스", 4.6, 128, 10, 5_000, listOf("주말 가능"), listOf(18.0, 33.0, 57.5)),
                visit("visit-c", "그린핏 출장정비", 4.7, 236, 5, 0, listOfNotNull("친환경 폐유 처리".takeIf { part == Part.ENGINE_OIL }, "평일 저녁"), listOf(19.0, 43.0)),
                visit("visit-d", "나이트오일 모바일", 4.4, 87, 15, 3_000, listOf("밤 10시까지"), listOf(21.0, 45.0, 69.0)))
            ServiceMode.SHOP -> listOf(
                shop("shop-a", "가온 모터스", 4.7, 541, minutesFor(part, task), 0.006, 0.009, "예시 주소 · 현재 위치에서 1 km 안팎", listOf("대기 공간", "당일 가능"), listOf(9.5, 11.0, 15.0)),
                cheaper(shop("shop-b", "바른 정비센터", 4.5, 207, minutesFor(part, task) + 15, -0.014, 0.011, "예시 주소 · 현재 위치에서 2 km 안팎", listOf("현대 순정 부품"), listOf(13.0, 16.5, 34.0))),
                shop("shop-c", "보라 오토케어", 4.8, 389, minutesFor(part, task), 0.019, -0.008, "예시 주소 · 현재 위치에서 2 km 안팎", listOf("수입차 겸용", "세차 포함"), listOf(10.0, 14.0)),
                cheaper(shop("shop-d", "한빛 카센터", 4.3, 96, minutesFor(part, task) + 10, -0.024, -0.017, "예시 주소 · 현재 위치에서 3 km 안팎", listOf("주차 넓음"), listOf(9.0, 17.0, 33.0))),
                shop("shop-e", "새길 타이어·정비", 4.6, 452, minutesFor(part, task), 0.031, 0.022, "예시 주소 · 현재 위치에서 4 km 안팎", if (part == Part.TIRE) listOf("타이어 전문", "얼라인먼트") else listOf("당일 가능"), listOf(11.5, 15.5)),
                cheaper(shop("shop-f", "푸른 타이어센터", 4.4, 174, minutesFor(part, task) + 5, -0.036, 0.031, "예시 주소 · 현재 위치에서 5 km 안팎", if (part == Part.TIRE) listOf("타이어 보관") else listOf("주말 가능"), listOf(10.5, 16.0, 35.0))))
            ServiceMode.SELF -> listOf(
                Offer("self-a-${part.name}-${task.name}", mode, "부품 배송 · 장착 영상 안내", 4.7, 893, 10, lines(part, task, mode),
                    listOf("내일 도착", "${CarModel.current.name} 규격"), slots(listOf(12.0))),
                Offer("self-b-${part.name}-${task.name}", mode, "파츠박스 당일배송", 4.5, 412, 10, lines(part, task, mode).map { if (it.won > 0) it.copy(won = it.won + 3_000) else it },
                    listOf("오늘 도착", "${CarModel.current.name} 규격"), slots(listOf(6.0))),
                Offer("self-c-${part.name}-${task.name}", mode, "그린파츠 정품몰", 4.8, 1_204, 10, lines(part, task, mode),
                    listOf("정품 인증", "모레 도착"), slots(listOf(36.0))))
        }
    }

    private fun minutesFor(part: Part, task: ServiceAction) = when (part) {
        Part.WIPER, Part.CABIN_FILTER -> 15
        Part.ENGINE_OIL -> 30
        Part.BRAKE_PAD -> if (task == ServiceAction.REPLACE) 60 else 20
        Part.TIRE -> when (task) { ServiceAction.REPLACE -> 60; ServiceAction.ROTATE -> 30; else -> 15 }
        Part.HVAC -> 40
    }

    /** Straight-line distance; good enough to order shops a few kilometres apart. */
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dy = (lat2 - lat1) * 111.32
        val dx = (lon2 - lon1) * 111.32 * cos(Math.toRadians((lat1 + lat2) / 2))
        return sqrt(dx * dx + dy * dy)
    }
}
