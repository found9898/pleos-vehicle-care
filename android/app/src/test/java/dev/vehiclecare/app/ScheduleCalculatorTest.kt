package dev.vehiclecare.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleCalculatorTest {
    @Test fun legacyWiperDistanceCannotRequireCurrentOdometer() {
        val day = LocalDate.of(2026, 10, 27)
        val r = ServiceRecord("w", Part.WIPER, day.minusDays(5), 40_000, ServiceAction.REPLACE)
        val p = MaintenancePlan(Part.WIPER, r.id, 5, 2_000, r.action, "기존 설정")
        val output = ScheduleCalculator.calculate(p, r, null, day)
        assertEquals(DueStatus.UPCOMING, output.status)
        assertNull(output.dueKm)
        assertEquals(day.minusDays(5).plusMonths(5), output.dueDate)
        assertTrue(output.missing.isEmpty())
    }

    @Test fun wiperReminderDoesNotBecomeDueFromOldDistanceSetting() {
        val day = LocalDate.of(2026, 10, 27)
        val r = ServiceRecord("w", Part.WIPER, day.minusDays(5), 40_000, ServiceAction.REPLACE)
        val p = MaintenancePlan(Part.WIPER, r.id, 12, 2_000, r.action, "기존 설정")
        assertEquals(DueStatus.UPCOMING, ScheduleCalculator.calculate(p, r, Mileage(80_000, day), day).status)
    }
    private val today = LocalDate.of(2026, 10, 26)
    private val record = ServiceRecord("baseline", Part.ENGINE_OIL, today.minusMonths(2), 20_000, ServiceAction.REPLACE)
    private val plan = MaintenancePlan(Part.ENGINE_OIL, record.id, 12, 10_000, ServiceAction.REPLACE, "직접 정한 주기")
    private fun result(p: MaintenancePlan? = plan, r: ServiceRecord? = record, km: Long? = 24_000, date: LocalDate = today) =
        ScheduleCalculator.calculate(p, r, km?.let { Mileage(it, date) }, today)

    @Test fun unregisteredIsNotHealthy() { assertEquals(DueStatus.UNREGISTERED, result(p = null).status) }
    @Test fun upcomingRequiresCompleteCurrentInputs() { assertEquals(DueStatus.UPCOMING, result().status) }
    @Test fun distanceDueEvenIfCalendarNotDue() { assertEquals(DueStatus.DUE, result(km = 30_000).status) }
    @Test fun calendarDueEvenIfDistanceNotDue() { assertEquals(DueStatus.DUE, result(r = record.copy(date = today.minusMonths(12))).status) }
    @Test fun overdueDistanceIsSignedAndNotClamped() { assertEquals(-2_500L, result(km = 32_500).remainingKm) }
    @Test fun overdueTimeIsSignedAndNotClamped() { assertEquals(-5L, result(r = record.copy(date = today.minusMonths(12).minusDays(5))).remainingDays) }
    @Test fun soonDistanceInclusive() { assertEquals(DueStatus.SOON, result(km = 29_000).status) }
    @Test fun outsideSoonWindow() { assertEquals(DueStatus.UPCOMING, result(km = 28_999).status) }
    @Test fun soonTimeInclusive() { assertEquals(DueStatus.SOON, result(r = record.copy(date = today.plusDays(30).minusMonths(12))).status) }
    @Test fun unknownBaselineDoesNotMeanTodayOrZero() {
        val output = result(r = record.copy(date = null, odometerKm = null))
        assertEquals(DueStatus.NEEDS_INFO, output.status); assertNull(output.dueDate); assertNull(output.dueKm)
    }
    @Test fun missingCurrentMileageStillAllowsCalendarDue() {
        assertEquals(DueStatus.DUE, result(r = record.copy(date = today.minusMonths(13)), km = null).status)
    }
    @Test fun missingDateStillAllowsDistanceDue() { assertEquals(DueStatus.DUE, result(r = record.copy(date = null), km = 30_000).status) }
    @Test fun dateOnlyWorksWithoutAnyMileage() { assertEquals(DueStatus.UPCOMING, result(p = plan.copy(intervalKm = null), km = null).status) }
    @Test fun distanceOnlyWorksWithoutAnyDate() { assertEquals(DueStatus.UPCOMING, result(p = plan.copy(intervalMonths = null), r = record.copy(date = null)).status) }
    @Test fun staleMileageCannotProveThereIsTimeLeft() { assertEquals(DueStatus.NEEDS_INFO, result(date = today.minusDays(1)).status) }
    @Test fun staleMileageCanProveThresholdAlreadyReached() { assertEquals(DueStatus.DUE, result(km = 32_000, date = today.minusDays(10)).status) }
    @Test fun staleSoonMileageRemainsCautiousAndLabeled() {
        val output = result(km = 29_500, date = today.minusDays(1))
        assertEquals(DueStatus.SOON, output.status); assertTrue(output.missing.any { it.contains("입력값 기준") })
    }
    @Test fun rolledBackMileageIsNotNegativeWear() {
        val output = result(km = 19_000)
        assertEquals(DueStatus.NEEDS_INFO, output.status); assertNull(output.remainingKm)
    }
    @Test fun futureServiceDateIsNotAcceptedAsBaseline() { assertNull(result(r = record.copy(date = today.plusDays(1))).dueDate) }
    @Test fun futureMileageTimestampIsNotCurrent() { assertEquals(DueStatus.NEEDS_INFO, result(date = today.plusDays(1)).status) }
    @Test fun pausedRetainsDeadlineButNeverAlerts() {
        val output = result(p = plan.copy(paused = true), km = 50_000)
        assertEquals(DueStatus.PAUSED, output.status); assertEquals(30_000L, output.dueKm)
    }
    @Test fun monthEndsUseCalendarNotFixedThirtyDays() {
        val output = ScheduleCalculator.calculate(plan.copy(intervalMonths = 1, intervalKm = null), record.copy(date = LocalDate.of(2026, 1, 31)), null, LocalDate.of(2026, 2, 1))
        assertEquals(LocalDate.of(2026, 2, 28), output.dueDate)
    }
    @Test fun leapDayRollsToEndOfFebruary() {
        val output = ScheduleCalculator.calculate(plan.copy(intervalMonths = 12, intervalKm = null), record.copy(date = LocalDate.of(2024, 2, 29)), null, LocalDate.of(2024, 3, 1))
        assertEquals(LocalDate.of(2025, 2, 28), output.dueDate)
    }
    @Test fun invalidIntervalsDoNotProduceAHealthyResult() { assertEquals(DueStatus.NEEDS_INFO, result(p = plan.copy(intervalMonths = 0, intervalKm = -1)).status) }
    @Test fun deletingActiveRecordDoesNotUseAnUnrelatedRecord() { assertEquals(DueStatus.NEEDS_INFO, result(r = null).status) }
    @Test fun unknownDistanceAxisPreventsBroadUpcomingClaim() { assertEquals(DueStatus.NEEDS_INFO, result(km = null).status) }
}
