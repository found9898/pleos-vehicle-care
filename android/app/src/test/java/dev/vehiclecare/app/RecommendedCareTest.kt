package dev.vehiclecare.app

import org.junit.Assert.*
import org.junit.Test

class RecommendedCareTest {
    @Test fun everyPartHasAUsableReferenceWithoutUserConfiguration() {
        Part.entries.forEach {
            val p = RecommendedCare.plan(it)
            assertTrue(p.intervalMonths != null || p.intervalKm != null)
            assertTrue(RecommendedCare.sourceUrl(it).startsWith("https://"))
            assertEquals(it.defaultAction, p.action)
        }
    }
    @Test fun referenceReplacementAndInspectionAreDistinct() {
        assertEquals(12, RecommendedCare.plan(Part.WIPER).intervalMonths)
        assertNull(RecommendedCare.plan(Part.WIPER).intervalKm)
        assertEquals(10000L, RecommendedCare.plan(Part.ENGINE_OIL).intervalKm)
        assertEquals(ServiceAction.INSPECT, RecommendedCare.plan(Part.BRAKE_PAD).action)
        assertEquals(ServiceAction.INSPECT, RecommendedCare.plan(Part.TIRE).action)
        assertEquals(1, RecommendedCare.plan(Part.TIRE).intervalMonths)
    }
}
