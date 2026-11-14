package dev.vehiclecare.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Part(val title: String, val subtitle: String, val defaultAction: ServiceAction) {
    WIPER("와이퍼", "와이퍼 교체 시점을 챙겨요", ServiceAction.REPLACE),
    ENGINE_OIL("엔진오일·필터", "오일과 오일 필터를 함께 관리해요", ServiceAction.REPLACE),
    BRAKE_PAD("브레이크 패드", "점검 후 교체 필요 여부를 확인해요", ServiceAction.INSPECT),
    TIRE("타이어", "마모 점검과 교체 기록을 챙겨요", ServiceAction.INSPECT),
    HVAC("냉방·난방", "냉난방 점검 시점을 챙겨요", ServiceAction.INSPECT),
    CABIN_FILTER("실내 공기 필터", "공조 필터 교체 시점을 챙겨요", ServiceAction.REPLACE),
    ;

    val supportsDistance: Boolean get() = this != WIPER && this != HVAC
}

/**
 * Symptoms the owner reports, as the Tucson 2026 manual names them for the cabin air filter: a clogged filter weakens
 * the airflow and can make the air smell; A/C smell from the vents is usually a damp evaporator instead.
 */
enum class OwnerReport(val label: String, val status: DueStatus) {
    WEAK_AIRFLOW("풍량 약해짐 신고", DueStatus.DUE),
    ODOR("냄새 신고", DueStatus.SOON),
}

enum class ServiceAction(val label: String) { REPLACE("교체"), INSPECT("점검"), ROTATE("위치 교환") }
enum class DueStatus(val label: String) {
    UNREGISTERED("시작 전"), PAUSED("일시정지"), NEEDS_INFO("정보가 필요해요"),
    UPCOMING("주기 여유"), SOON("곧 할 때"), DUE("지금 할 때"),
}

data class ServiceRecord(
    val id: String,
    val part: Part,
    val date: LocalDate?,
    val odometerKm: Long?,
    val action: ServiceAction,
    /** What a gauge read at this service, in mm: tread depth on a tire check, pad thickness on a brake check. */
    val measuredMm: Double? = null,
)

data class MaintenancePlan(
    val part: Part,
    val recordId: String,
    val intervalMonths: Int?,
    val intervalKm: Long?,
    val action: ServiceAction,
    val source: String,
    val paused: Boolean = false,
)

data class Mileage(val km: Long, val updatedOn: LocalDate, val source: String = "manual", val observedAt: Long = 0)

data class DueResult(
    val status: DueStatus,
    val dueDate: LocalDate? = null,
    val dueKm: Long? = null,
    val remainingDays: Long? = null,
    val remainingKm: Long? = null,
    val missing: List<String> = emptyList(),
)

/** A schedule reminder, never an estimate of wear or vehicle health. */
object ScheduleCalculator {
    const val SOON_DAYS = 30L
    /** A month-long check would be "soon" from its first day with a 30-day window; it gets its last week instead. */
    fun soonDays(months: Int?) = if (months == 1) 7L else SOON_DAYS
    const val SOON_KM = 1_000L
    const val MAX_ODOMETER = 10_000_000L

    fun calculate(plan: MaintenancePlan?, record: ServiceRecord?, mileage: Mileage?, today: LocalDate): DueResult {
        if (plan == null) return DueResult(DueStatus.UNREGISTERED)
        val missing = mutableListOf<String>()
        val months = plan.intervalMonths?.takeIf { it in 1..240 }
        val intervalKm = plan.intervalKm?.takeIf { plan.part.supportsDistance && it in 1..MAX_ODOMETER }
        val date = record?.date?.takeIf { !it.isAfter(today) }
        val baselineKm = record?.odometerKm?.takeIf { it in 0..MAX_ODOMETER }
        val currentKm = mileage?.takeIf { it.km in 0..MAX_ODOMETER && !it.updatedOn.isAfter(today) }?.km
        val dueDate = if (months != null && date != null) date.plusMonths(months.toLong()) else null
        val dueKm = if (intervalKm != null && baselineKm != null) baselineKm + intervalKm else null
        if (months != null && date == null) missing += "최근 정비 날짜가 필요해요"
        if (intervalKm != null && baselineKm == null) missing += "정비 당시 주행거리가 필요해요"
        if (intervalKm != null && currentKm == null) missing += "현재 주행거리를 입력해 주세요"
        if (intervalKm != null && baselineKm != null && currentKm != null && currentKm < baselineKm) missing += "현재 주행거리가 정비 당시보다 작아요"
        if (months == null && intervalKm == null) missing += "기간 또는 거리 주기를 설정해 주세요"
        val days = dueDate?.let { ChronoUnit.DAYS.between(today, it) }
        val km = if (dueKm != null && currentKm != null && currentKm >= (baselineKm ?: 0)) dueKm - currentKm else null
        // A stale odometer can prove a threshold was already reached; it cannot prove it wasn't.
        val stale = intervalKm != null && mileage != null && mileage.updatedOn != today
        if (stale) missing += "거리 결과는 ${mileage!!.updatedOn} 입력값 기준이에요"
        val status = when {
            plan.paused -> DueStatus.PAUSED
            (days != null && days <= 0) || (km != null && km <= 0) -> DueStatus.DUE
            (days != null && days <= soonDays(months)) || (km != null && km <= SOON_KM) -> DueStatus.SOON
            missing.isNotEmpty() -> DueStatus.NEEDS_INFO
            days != null || km != null -> DueStatus.UPCOMING
            else -> DueStatus.NEEDS_INFO
        }
        return DueResult(status, dueDate, dueKm, days, km, missing)
    }
}
