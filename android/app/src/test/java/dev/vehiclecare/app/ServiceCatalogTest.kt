package dev.vehiclecare.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ServiceCatalogTest {
    private val now = LocalDateTime.of(2026, 10, 30, 21, 0)

    @Test fun jobsThatNeedEquipmentStayInAShop() {
        assertEquals(listOf(ServiceMode.SHOP), ServiceCatalog.modes(Part.BRAKE_PAD, ServiceAction.REPLACE))
        assertEquals(listOf(ServiceMode.SHOP), ServiceCatalog.modes(Part.HVAC, ServiceAction.INSPECT))
        assertEquals(listOf(ServiceMode.SHOP), ServiceCatalog.modes(Part.TIRE, ServiceAction.INSPECT))
        assertEquals(listOf(ServiceMode.VISIT, ServiceMode.SHOP), ServiceCatalog.modes(Part.TIRE, ServiceAction.ROTATE))
        assertEquals(listOf(ServiceMode.VISIT, ServiceMode.SHOP), ServiceCatalog.modes(Part.ENGINE_OIL, ServiceAction.REPLACE))
        assertTrue(ServiceMode.SELF in ServiceCatalog.modes(Part.WIPER, ServiceAction.REPLACE))
    }

    @Test fun aShopsTagsBelongToTheJobOnScreen() {
        Part.entries.forEach { part ->
            ServiceMode.entries.forEach { mode ->
                val tags = ServiceCatalog.offers(part, part.defaultAction, mode, null, now).flatMap { it.tags }
                if (part != Part.ENGINE_OIL) assertTrue("$part $mode", tags.none { "폐유" in it })
                if (part != Part.TIRE) assertTrue("$part $mode", tags.none { "타이어" in it || "얼라인먼트" in it })
            }
        }
        val oil = ServiceCatalog.offers(Part.ENGINE_OIL, ServiceAction.REPLACE, ServiceMode.VISIT, null, now)
        assertTrue(oil.any { "친환경 폐유 처리" in it.tags })
    }

    @Test fun shopsSitAroundTheCarAndAreSentToNavigationWithCoordinates() {
        val origin = 37.4127 to 127.0947
        val shops = ServiceCatalog.offers(Part.ENGINE_OIL, ServiceAction.REPLACE, ServiceMode.SHOP, origin, now)
        assertEquals(6, shops.size)
        shops.forEach { assertTrue(it.lat != null && it.distanceKm!! in 0.5..6.0) }
        assertTrue(shops[0].total != shops[1].total)
        val visits = ServiceCatalog.offers(Part.ENGINE_OIL, ServiceAction.REPLACE, ServiceMode.VISIT, origin, now)
        assertTrue(visits.all { it.lat == null && it.lines.any { l -> l.name == "출장" } })
        // Slots start tomorrow.
        assertTrue(visits.all { it.slots.first().toLocalDate() == now.toLocalDate().plusDays(1) })
    }

    @Test fun aBookingKeepsTheShopForARouteLater() {
        val shop = ServiceCatalog.offers(Part.TIRE, ServiceAction.ROTATE, ServiceMode.SHOP, 37.5 to 127.0, now).first()
        val b = Booking.of(shop, shop.slots[1])
        val back = Booking.fromJson(JSONObject(b.toJson().toString()))
        assertEquals(b, back)
        assertEquals(shop.lat, back.offer.lat)
        assertEquals(30_000, b.total)
    }

    @Test fun distanceIsAboutRight() {
        // One hundredth of a degree of latitude is about 1.1 km.
        assertEquals(1.113, ServiceCatalog.km(37.5, 127.0, 37.51, 127.0), 0.01)
    }
}
