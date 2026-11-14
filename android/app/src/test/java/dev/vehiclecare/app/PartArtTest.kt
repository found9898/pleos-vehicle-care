package dev.vehiclecare.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PartArtTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val record = ServiceRecord("r", Part.TIRE, today.minusMonths(1), 10_000, ServiceAction.INSPECT)
    private val plan = MaintenancePlan(Part.TIRE, "r", 12, 10_000, ServiceAction.INSPECT, "직접 정한 주기")
    @Test fun ringUsesWhicheverScheduleHasProgressedFurther() {
        assertEquals(.75f, PartArt.fraction(plan, record, Mileage(17_500, today), today)!!, .001f)
    }
    @Test fun unknownBaselineHasNoInventedProgress() {
        assertNull(PartArt.fraction(plan, record.copy(date = null, odometerKm = null), null, today))
        assertNull(PartArt.fraction(null, null, null, today))
    }
    @Test fun overdueProgressStopsAtOneInsteadOfInventingNegativeRemainingLife() {
        assertEquals(1f, PartArt.fraction(plan, record, Mileage(50_000, today), today)!!, .001f)
    }
}
