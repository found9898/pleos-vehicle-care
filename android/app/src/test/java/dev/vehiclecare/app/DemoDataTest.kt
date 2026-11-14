package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The demo story must show one part due, two soon and three with room, on any day it is loaded. */
class DemoDataTest {
    private fun judge(today: LocalDate): Map<Part, Assessment> {
        CarModel.current = VehicleCatalog.TUCSON_GASOLINE
        val story = DemoData.build(today)
        val mileage = Mileage(story.odometer, today, "vehicle")
        val s = story.snapshot()
        return CarModel.current.parts.associateWith { part ->
            val record = story.records.first { it.id == "demo-${part.name}" }
            CompositeJudge.assess(RecommendedCare.plan(part, record.id), record, mileage, today, s, story.records)
        }
    }

    @Test fun oneDueTwoSoonThreeWithRoomWhicheverDayItLoads() {
        listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 23), LocalDate.of(2026, 12, 3), LocalDate.of(2027, 3, 14), LocalDate.of(2027, 7, 30)).forEach { day ->
            val a = judge(day)
            println("$day " + a.entries.joinToString(" | ") { (p, v) -> "${p.name} ${v.status} ${v.fraction?.let { Math.round(it * 100) }} ${v.drivenBy}" })
            assertEquals("$day brake", DueStatus.DUE, a.getValue(Part.BRAKE_PAD).status)
            assertTrue("$day brake by energy", a.getValue(Part.BRAKE_PAD).drivenBy!!.startsWith("제동 에너지"))
            assertEquals("$day oil", DueStatus.SOON, a.getValue(Part.ENGINE_OIL).status)
            assertTrue("$day oil by trips", a.getValue(Part.ENGINE_OIL).drivenBy!!.startsWith("운행 조건"))
            assertEquals("$day wiper", DueStatus.SOON, a.getValue(Part.WIPER).status)
            assertTrue("$day wiper by sound", a.getValue(Part.WIPER).drivenBy!!.startsWith("작동 소리"))
            listOf(Part.TIRE, Part.HVAC, Part.CABIN_FILTER).forEach { assertEquals("$day $it", DueStatus.UPCOMING, a.getValue(it).status) }
        }
    }

    /** The video and the planning documents quote these figures, so the load date must not move them. */
    @Test fun theOilDistanceReadsTheSameOnEveryWeekday() {
        (0L..6L).map { LocalDate.of(2026, 11, 2).plusDays(it) }.plus(LocalDate.of(2027, 3, 14)).forEach { today ->
            CarModel.current = VehicleCatalog.TUCSON_GASOLINE
            val story = DemoData.build(today)
            val record = story.records.first { it.id == "demo-${Part.ENGINE_OIL.name}" }
            val plan = RecommendedCare.plan(Part.ENGINE_OIL, record.id)
            val mileage = Mileage(story.odometer, today, "vehicle")
            val a = CompositeJudge.assess(plan, record, mileage, today, story.snapshot(), story.records)
            val guide = CareGuide.build(Part.ENGINE_OIL, plan, record, a, story.snapshot(), story.records, mileage, null, today)
            assertEquals("$today", "478 km 안에 엔진오일을 갈아 주세요", guide.headline)
            assertEquals("$today", "실제 6,605 km + 운행 조건 2,917 km", guide.factors.first().now)
        }
    }

    @Test fun theHistoryNeverRunsBackwards() {
        val records = DemoData.build(LocalDate.of(2026, 11, 5)).records.sortedBy { it.date }
        records.zipWithNext().forEach { (a, b) -> assertTrue("${a.id} ${b.id}", a.odometerKm!! <= b.odometerKm!!) }
    }
}
