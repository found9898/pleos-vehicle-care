package dev.vehiclecare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleModelTest {
    @Test fun fuelTypesNameThePowertrain() {
        assertEquals(Powertrain.ELECTRIC, Powertrain.ofFuelTypes(intArrayOf(10), null))
        assertEquals(Powertrain.HYBRID, Powertrain.ofFuelTypes(intArrayOf(1, 10), null))
        assertEquals(Powertrain.GASOLINE, Powertrain.ofFuelTypes(intArrayOf(1), null))
        // A car that lists no fuel type but has a traction battery is electric.
        assertEquals(Powertrain.ELECTRIC, Powertrain.ofFuelTypes(null, 77_400f))
        assertEquals(Powertrain.UNKNOWN, Powertrain.ofFuelTypes(null, null))
    }

    @Test fun aKnownNamePicksTheModelAndItsPowertrain() {
        val car = VehicleIdentity("Hyundai", "TUCSON", 2026, Powertrain.GASOLINE, 4_640, 1_665)
        assertEquals(VehicleCatalog.TUCSON_GASOLINE.id, VehicleCatalog.match(car)?.id)
        // The emulator reports a generic car: not in the catalog, so the app falls back to its powertrain and size.
        assertNull(VehicleCatalog.match(VehicleIdentity("Hyundai 42dot", "Connect", 2024, Powertrain.ELECTRIC, 4_950, 1_776)))
    }

    @Test fun anElectricCarHasNoEngineOil() {
        val ev = VehicleCatalog.generic(VehicleIdentity("Hyundai 42dot", "Connect", 2024, Powertrain.ELECTRIC, 4_950, 1_776))
        assertFalse(Part.ENGINE_OIL in ev.parts)
        assertEquals(5, ev.parts.size)
        assertTrue(Part.ENGINE_OIL in VehicleCatalog.TUCSON_GASOLINE.parts)
    }

    @Test fun plansFollowTheModelsManual() {
        val tucson = VehicleCatalog.TUCSON_GASOLINE
        assertEquals(10_000L, RecommendedCare.plan(Part.ENGINE_OIL, spec = tucson).intervalKm)
        assertEquals(5_000L, RecommendedCare.plan(Part.ENGINE_OIL, severe = true, spec = tucson).intervalKm)
        val other = tucson.copy(id = "x", engineOil = Interval(15_000, 12), brakeInspect = Interval(20_000))
        assertEquals(15_000L, RecommendedCare.plan(Part.ENGINE_OIL, spec = other).intervalKm)
        assertEquals(20_000L, RecommendedCare.plan(Part.BRAKE_PAD, spec = other).intervalKm)
        assertTrue(RecommendedCare.plan(Part.BRAKE_PAD, spec = tucson).source.contains("투싼"))
    }

    @Test fun partSizesComeFromTheModel() {
        val tucson = VehicleCatalog.TUCSON_GASOLINE
        assertEquals("블레이드 650·400 mm 교체", ServiceCatalog.job(Part.WIPER, ServiceAction.REPLACE, tucson))
        assertEquals("17~19인치 타이어 4개 교체", ServiceCatalog.job(Part.TIRE, ServiceAction.REPLACE, tucson))
        assertEquals("공기압 240 kPa·마모 점검", ServiceCatalog.job(Part.TIRE, ServiceAction.INSPECT, tucson))
        assertEquals("엔진오일 0W-20 4.8 L·필터 교체", ServiceCatalog.job(Part.ENGINE_OIL, ServiceAction.REPLACE, tucson))
        val unknown = VehicleCatalog.generic(null, Powertrain.GASOLINE)
        assertEquals("앞 블레이드 한 쌍 교체", ServiceCatalog.job(Part.WIPER, ServiceAction.REPLACE, unknown))
    }

    @Test fun theCatalogFollowsEachManual() {
        assertEquals(14, VehicleCatalog.models.size)
        // Avante's 1.6 changes oil every 15,000 km; Casper inspects brakes every 20,000 km; electric cars have no oil.
        val avante = VehicleCatalog.match(VehicleIdentity("Hyundai", "AVANTE", 2026, Powertrain.GASOLINE, 4_710, 1_420))!!
        assertEquals(15_000L, RecommendedCare.plan(Part.ENGINE_OIL, spec = avante).intervalKm)
        val casper = VehicleCatalog.match(VehicleIdentity("Hyundai", "CASPER", 2026, Powertrain.GASOLINE, 3_595, 1_575))!!
        assertEquals(20_000L, RecommendedCare.plan(Part.BRAKE_PAD, spec = casper).intervalKm)
        val ioniq = VehicleCatalog.match(VehicleIdentity("Hyundai", "IONIQ 5", 2026, Powertrain.ELECTRIC, 4_655, 1_605))!!
        assertFalse(Part.ENGINE_OIL in ioniq.parts)
        assertEquals("아이오닉 5", ioniq.name)
        // Hybrid and electric pick their own entries, not the gasoline one with the same name.
        assertEquals(Powertrain.HYBRID, VehicleCatalog.match(VehicleIdentity("Hyundai", "Tucson", 2026, Powertrain.HYBRID, null, null))!!.powertrain)
        assertEquals(Powertrain.ELECTRIC, VehicleCatalog.match(VehicleIdentity("Hyundai", "CASPER", 2026, Powertrain.ELECTRIC, null, null))!!.powertrain)
    }

    @Test fun anEngineTheCarDoesNotNameGetsTheShorterOilInterval() {
        val sonata = VehicleCatalog.match(VehicleIdentity("Hyundai", "SONATA", 2026, Powertrain.GASOLINE, 4_910, 1_445))!!
        assertEquals(10_000L, sonata.engineOil?.km)
    }

    @Test fun shapeFromSize() {
        assertEquals(BodyType.COMPACT, VehicleIdentity(null, null, null, Powertrain.GASOLINE, 3_595, 1_575).body)
        assertEquals(BodyType.SEDAN, VehicleIdentity(null, null, null, Powertrain.GASOLINE, 4_910, 1_445).body)
        assertEquals(BodyType.SUV, VehicleIdentity(null, null, null, Powertrain.GASOLINE, 4_640, 1_665).body)
    }
}
