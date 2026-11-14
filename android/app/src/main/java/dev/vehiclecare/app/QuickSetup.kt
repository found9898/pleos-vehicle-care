package dev.vehiclecare.app

import java.time.LocalDate

/**
 * First-run setup by rough answers: when each part was last done, and for the distance parts how far the car has gone
 * since. A rough answer is still a baseline the schedule can count from; "모르겠어요" leaves the part unrecorded, and the
 * owner can put in the exact date later from the part's menu.
 *
 * The tire step asks when the current set went on and how far it has run, and saves that as a replacement: the one record
 * the rotation interval, the six-year age line and the tread estimate all count from. Asking when the owner last looked
 * at the tires would feed only the monthly check, and few owners could say.
 */
object QuickSetup {
    class Choice(val label: String, val daysAgo: Long?)
    /** [sinceNew]: the set came with the car, so it has run the whole odometer and the baseline is 0 km. */
    class KmChoice(val label: String, val km: Long?, val sinceNew: Boolean = false)

    /** Whether the step asks when the part was done; the brake inspection is counted in distance only. */
    fun asksDate(part: Part) = part != Part.BRAKE_PAD
    /** Whether the step asks the distance since: the oil and brake schedules, and the tire's rotation and tread wear. */
    fun asksKm(part: Part) = part == Part.ENGINE_OIL || part == Part.BRAKE_PAD || part == Part.TIRE

    /** What the answer records: the tire answer is about the set on the car, so it is its replacement. */
    fun action(part: Part) = if (part == Part.TIRE) ServiceAction.REPLACE else part.defaultAction

    fun question(part: Part) = when (part) {
        Part.WIPER -> "와이퍼를 마지막으로 언제 바꾸셨나요?"
        Part.ENGINE_OIL -> "엔진오일을 마지막으로 언제 가셨나요?"
        Part.BRAKE_PAD -> "브레이크 패드 점검을 받고 얼마나 달렸나요?"
        Part.TIRE -> "지금 타이어는 언제 다셨나요?"
        Part.HVAC -> "냉방·난방 점검을 마지막으로 언제 받으셨나요?"
        Part.CABIN_FILTER -> "실내 공기 필터를 마지막으로 언제 바꾸셨나요?"
    }

    fun hint(part: Part) = if (part == Part.TIRE) "차를 살 때 달려 나온 타이어 그대로라면 차를 받은 때를 골라 주세요. 위치 교환 기록은 나중에 부품 메뉴에서 넣을 수 있어요."
        else "대략이어도 괜찮아요. 정확한 날짜는 나중에 부품 메뉴에서 고칠 수 있어요."

    /** Labels over the two rows when a step asks both. */
    fun dateLabel(part: Part) = if (part == Part.TIRE) "타이어를 단 때" else "${action(part).label}한 때"
    fun kmLabel(part: Part) = if (part == Part.TIRE) "이 타이어로 달린 거리" else "그 뒤로 달린 거리"

    /** Rough times back, sized to the interval: years for a set of tires, months for the yearly parts. */
    fun dates(part: Part) = if (part == Part.TIRE) listOf(
        Choice("1년 안", 182), Choice("2년 전쯤", 731), Choice("3년 전쯤", 1_096), Choice("4년 전쯤", 1_461), Choice("5년 넘었어요", 2_009), Choice("모르겠어요", null),
    ) else listOf(
        Choice("한 달 안", 14), Choice("3개월 전쯤", 91), Choice("6개월 전쯤", 182), Choice("1년 전쯤", 365), Choice("1년 넘었어요", 456), Choice("모르겠어요", null),
    )

    /** Distances sized to the interval: thousands of km for oil and brakes, tens of thousands for a set of tires. */
    fun distances(part: Part) = if (part == Part.TIRE) listOf(
        KmChoice("새 차 때부터", null, sinceNew = true), KmChoice("1만 km 안", 5_000), KmChoice("2만 km쯤", 20_000), KmChoice("3만 km쯤", 30_000),
        KmChoice("4만 km 넘게", 45_000), KmChoice("모르겠어요", null),
    ) else listOf(
        KmChoice("1,000 km 안", 500), KmChoice("3,000 km쯤", 3_000), KmChoice("5,000 km쯤", 5_000), KmChoice("8,000 km쯤", 8_000),
        KmChoice("1만 km 넘게", 11_000), KmChoice("모르겠어요", null),
    )

    /**
     * The record the answers stand for, or null when they say nothing. A distance becomes an odometer baseline only when
     * the car's reading is known, and an answer larger than the reading is held at zero; a set that came with the car
     * starts at zero whatever the reading.
     */
    fun record(id: String, part: Part, today: LocalDate, odometer: Long?, date: Choice?, km: KmChoice?): ServiceRecord? {
        val day = date?.daysAgo?.let { today.minusDays(it) }
        val baseline = if (km?.sinceNew == true) 0L else km?.km?.let { driven -> odometer?.let { (it - driven).coerceAtLeast(0) } }
        if (day == null && baseline == null) return null
        return ServiceRecord(id, part, day, baseline, action(part))
    }

    /** The answer as one short line for the step list ("3개월 전쯤 · 5,000 km쯤"). */
    fun summary(date: Choice?, km: KmChoice?) = listOfNotNull(date?.label, km?.label).distinct().joinToString(" · ")
}
