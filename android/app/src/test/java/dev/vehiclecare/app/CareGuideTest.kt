package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CareGuideTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun guide(part: Part, record: ServiceRecord?, km: Long?, s: SignalSnapshot = SignalSnapshot(), records: List<ServiceRecord> = listOfNotNull(record),
                      report: OwnerReport? = null): Guide {
        val plan = RecommendedCare.plan(part, record?.id ?: "none")
        val mileage = km?.let { Mileage(it, today, "vehicle") }
        val a = CompositeJudge.assess(plan, record, mileage, today, s, records, report)
        return CareGuide.build(part, plan, record, a, s, records, mileage, report, today)
    }

    private fun label(part: Part, record: ServiceRecord, km: Long?, s: SignalSnapshot = SignalSnapshot()): Pair<String, Assessment> {
        val plan = RecommendedCare.plan(part, record.id)
        val mileage = km?.let { Mileage(it, today, "vehicle") }
        val a = CompositeJudge.assess(plan, record, mileage, today, s, listOf(record))
        return CareGuide.label(plan, a, CareGuide.build(part, plan, record, a, s, listOf(record), mileage, null, today).factors) to a
    }

    @Test fun theRingLabelNamesTheJobAndTheDistanceNeverAShare() {
        val oil = label(Part.ENGINE_OIL, ServiceRecord("r", Part.ENGINE_OIL, today.minusMonths(5), 30_000, ServiceAction.REPLACE), 37_620).first
        assertEquals("교체까지 2,380 km", oil)
        val brake = label(Part.BRAKE_PAD, ServiceRecord("r", Part.BRAKE_PAD, today.minusMonths(7), 27_500, ServiceAction.INSPECT), 38_420)
        assertEquals("점검 920 km 지남", brake.first)
        assertFalse(brake.first.contains("%"))
        // The ring fills with what is used: an overdue inspection is a full ring, not an empty one.
        assertEquals(1f, brake.second.fraction)
    }

    @Test fun aNoiseCueAsksForCleaningAndDoesNotPinTheRing() {
        val r = ServiceRecord("r", Part.WIPER, today.minusDays(102), null, ServiceAction.REPLACE)
        val plan = RecommendedCare.plan(Part.WIPER, "r")
        val a = CompositeJudge.assess(plan, r, null, today, SignalSnapshot())
        // Without the cue the ring is the calendar's own share; a sound cue may change the colour, never this figure.
        assertEquals(PartArt.fraction(plan, r, null, today), a.fraction)
    }

    @Test fun withoutARecordItAsksForTheDate() {
        assertEquals("마지막 교체 날짜를 알려 주세요", guide(Part.ENGINE_OIL, null, 40_000).headline)
    }

    @Test fun theNearerLimitIsTheHeadline() {
        val r = ServiceRecord("r", Part.ENGINE_OIL, today.minusMonths(5), 30_000, ServiceAction.REPLACE)
        val g = guide(Part.ENGINE_OIL, r, 37_620)
        assertEquals("2,380 km 남았어요", g.headline)
        assertTrue(g.advice.startsWith("지금은 할 일이 없어요"))
    }

    @Test fun anOverdueScheduleSaysByHowMuch() {
        val r = ServiceRecord("r", Part.BRAKE_PAD, today.minusMonths(7), 27_500, ServiceAction.INSPECT)
        val g = guide(Part.BRAKE_PAD, r, 38_420)
        assertEquals("지금 브레이크 패드 점검을 받아 주세요", g.headline)
        assertEquals("권장 주기를 920 km 넘겼어요.", g.advice)
    }

    @Test fun anOverdueOilQuotesTheSameDistanceAsTheRing() {
        // The example car, driven 6,000 km further without a change: the equivalent distance is well past the interval.
        val story = DemoData.build(today)
        val record = story.records.first { it.part == Part.ENGINE_OIL }
        val plan = RecommendedCare.plan(Part.ENGINE_OIL, record.id)
        val mileage = Mileage(story.odometer + 6_000, today, "vehicle")
        val s = story.snapshot()
        val a = CompositeJudge.assess(plan, record, mileage, today, s, story.records)
        val g = CareGuide.build(Part.ENGINE_OIL, plan, record, a, s, story.records, mileage, null, today)
        val ring = CareGuide.label(plan, a, g.factors)
        assertEquals(DueStatus.DUE, a.status)
        val over = Regex("교체 ([0-9,]+) km 지남").find(ring)!!.groupValues[1]
        assertEquals("엔진이 덜 데워진 채 달린 시간과 공회전이 많아, 오일로 치면 권장 주기를 $over km 넘겼어요.", g.advice)
    }

    @Test fun anOverdueWiperQuotesTheRowItsHeadlineRestsOn() {
        val story = DemoData.build(today)
        val old = story.records.first { it.part == Part.WIPER }.let { it.copy(date = today.minusDays(432)) }
        val records = story.records.map { if (it.id == old.id) old else it }
        val plan = RecommendedCare.plan(Part.WIPER, old.id)
        val mileage = Mileage(story.odometer, today, "vehicle")
        val s = story.snapshot()
        val a = CompositeJudge.assess(plan, old, mileage, today, s, records)
        val g = CareGuide.build(Part.WIPER, plan, old, a, s, records, mileage, null, today)
        val row = g.factors.first { it.title == "기간" }.value
        assertTrue(row.endsWith(" 지남"))
        assertEquals("권장 주기를 ${row.removeSuffix(" 지남")} 넘겼어요.", g.advice)
    }

    @Test fun anOldTireSaysHowLongAgoItWasDueOnce() {
        val set = ServiceRecord("set", Part.TIRE, today.minusMonths(78), 2_000, ServiceAction.REPLACE)
        val check = ServiceRecord("r", Part.TIRE, today.minusDays(9), 39_600, ServiceAction.INSPECT)
        val records = listOf(set, check)
        val plan = RecommendedCare.plan(Part.TIRE, check.id)
        val mileage = Mileage(39_862, today, "vehicle")
        val a = CompositeJudge.assess(plan, check, mileage, today, SignalSnapshot(), records)
        val g = CareGuide.build(Part.TIRE, plan, check, a, SignalSnapshot(), records, mileage, null, today)
        val ring = CareGuide.label(plan, a, g.factors)
        assertEquals("교체 6개월 지남", ring)
        assertFalse(ring.contains("교체 교체"))
    }

    @Test fun anEstimateThatMadeItSoonDoesNotQuoteTheCalendar() {
        val r = ServiceRecord("r", Part.CABIN_FILTER, today.minusMonths(6), 33_600, ServiceAction.REPLACE)
        val dust = FilterDust(465.0, mutableMapOf("r" to 0.0))
        val g = guide(Part.CABIN_FILTER, r, 38_420, SignalSnapshot(filter = dust))
        assertEquals("곧 실내 필터를 바꿔 주세요", g.headline)
        assertTrue(g.factors.any { it.alert && it.title == "먼지 적재 추정" && it.value == "93% 찼어요" })
    }

    @Test fun aLouderWiperAsksToCleanFirst() {
        val sound = WiperSound().apply {
            markBaseline("r")
            listOf(17.0, 14.0, 11.0, 12.5, 11.8, 12.2, 11.5, 12.9, 12.0, 11.2, 15.0, 16.0, 15.5, 13.1, 16.2, 15.9).forEach { add(SoundSession(0, 120, doubleArrayOf(0.0, 0.0, 0.0, 0.0, it), "LOW")) }
        }
        val r = ServiceRecord("r", Part.WIPER, today.minusMonths(5), null, ServiceAction.REPLACE)
        val g = guide(Part.WIPER, r, null, SignalSnapshot(sound = sound))
        assertEquals("유리와 블레이드를 먼저 닦아 보세요", g.headline)
    }

    @Test fun aReportedWeakAirflowSaysSo() {
        val r = ServiceRecord("r", Part.CABIN_FILTER, today.minusMonths(2), 36_000, ServiceAction.REPLACE)
        val g = guide(Part.CABIN_FILTER, r, 37_000, report = OwnerReport.WEAK_AIRFLOW)
        assertEquals("지금 실내 필터를 바꿔 주세요", g.headline)
        assertTrue(g.advice.startsWith("풍량이 약해졌다고"))
        assertEquals("알려 주신 증상", g.factors.first().title)
    }

    @Test fun tiresShowAllThreeJobsAndAskForTheMostUrgent() {
        val set = ServiceRecord("s", Part.TIRE, today.minusYears(5).minusMonths(2), 60_000, ServiceAction.REPLACE)
        val inspect = ServiceRecord("i", Part.TIRE, today.minusDays(3), 70_000, ServiceAction.INSPECT)
        val rotated = ServiceRecord("r", Part.TIRE, today.minusMonths(2), 68_000, ServiceAction.ROTATE)
        val g = guide(Part.TIRE, inspect, 70_200, records = listOf(set, rotated, inspect))
        assertEquals("10개월 안에 타이어 네 개를 함께 교체해 주세요", g.headline)
        assertTrue(g.advice.contains("DOT"))
        assertEquals(listOf("공기압·마모 점검", "위치 교환", "교체"), g.factors.map { it.title })
        assertTrue(g.factors.last().alert)
        // Every row with a bar names both of its ends, so a figure is never shown without what it is measured against.
        g.factors.filter { it.fraction != null }.forEach { assertTrue(it.title, it.now.isNotEmpty() && it.limit.startsWith("권장")) }
        assertEquals("점검 후 3일", g.factors.first().now)
        assertEquals("권장 10,000 km", g.factors[1].limit)
        assertEquals("권장 6년", g.factors.last().limit)
        assertEquals("10개월 남음", g.factors.last().value)
    }

    @Test fun aWornEstimateAsksForTheTreadToBeCheckedNotForNewTires() {
        // 52,000 km on a Tucson set rotated every 10,000 km: centre 3.2 mm on the rear, upper 1.7 mm.
        val set = ServiceRecord("s", Part.TIRE, today.minusYears(3), 10_000, ServiceAction.REPLACE)
        val rotations = (1..5).map { ServiceRecord("r$it", Part.TIRE, today.minusMonths(36L - it * 6), 10_000L + it * 10_000, ServiceAction.ROTATE) }
        // The last inspection was at 40,000 km, when even the upper estimate was far from 3 mm, so it vouches for nothing now.
        val inspect = ServiceRecord("i", Part.TIRE, today.minusDays(20), 40_000, ServiceAction.INSPECT)
        val g = guide(Part.TIRE, inspect, 62_000, records = listOf(set) + rotations + inspect)
        assertEquals("곧 타이어 홈 깊이를 확인해 주세요", g.headline)
        assertTrue(g.advice.contains("마모 한계 표시"))
        val row = g.factors.last()
        assertEquals("홈 깊이 추정", row.title); assertTrue(row.alert); assertEquals("2.4 mm면 확인", row.limit)
        // An inspection at 61,000 km that ended without new tires says 3 mm or more was left: the upper rate drops to fit,
        // and the next ask waits for 2.4 mm.
        val checked = ServiceRecord("j", Part.TIRE, today.minusDays(1), 61_000, ServiceAction.INSPECT)
        assertFalse(guide(Part.TIRE, checked, 62_000, records = listOf(set) + rotations + checked).headline.contains("홈 깊이"))
    }
}

class VoiceBriefTest {
    @Test fun itSaysNothingWithNothingDueAndNamesTheFirstJob() {
        assertEquals(null, VoiceBrief.sentence(emptyList()))
        assertEquals("브레이크 패드 점검 시기가 됐어요. 세운 뒤 미리정비에서 확인해 주세요.", VoiceBrief.sentence(listOf("브레이크 패드 점검")))
        assertEquals("브레이크 패드 점검 외 2건의 정비 시기가 됐어요. 세운 뒤 미리정비에서 확인해 주세요.", VoiceBrief.sentence(listOf("브레이크 패드 점검", "엔진오일·필터 교체", "와이퍼 교체")))
    }
}
