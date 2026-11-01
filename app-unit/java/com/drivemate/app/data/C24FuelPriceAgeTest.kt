package com.drivemate.app.data

import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 24.3: a fuel price is always shown with the age of the copy the phone holds. */
class C24FuelPriceAgeTest {
    private val now = 1_800_000_000_000L
    private val prices = mapOf("E10" to 145.9, "B7" to 152.9)
    private fun station(fetchedAt: Long) = FuelStation("Test", LatLon(53.4, -2.2), prices, fetchedAt)

    @Test fun recentCopyShowsPricesWithItsAge() {
        assertEquals("Unleaded 145.9p · Diesel 152.9p · feed checked 30 min ago",
            station(now - 30 * 60_000L).summary(now))
    }

    @Test fun copyOlderThanTwoHoursIsNotPresentedAsCurrent() {
        val text = station(now - 7 * 3_600_000L).summary(now)
        assertTrue(text, text.contains("feed checked 7 h ago, old price not current"))
        assertFalse(text, station(now - 30 * 60_000L).summary(now).contains("not current"))
    }

    @Test fun unknownFetchTimeIsNeverShownAsCurrent() {
        assertEquals("Unleaded 145.9p · Diesel 152.9p · price age unknown, not current",
            station(0L).summary(now))
    }

    @Test fun pricesStillFormatFromPoundsAndPence() {
        val pounds = FuelStation("Test", LatLon(53.4, -2.2), mapOf("E10" to 1.459), now)
        assertEquals("Unleaded 145.9p · feed checked 0 min ago", pounds.summary(now))
    }
}
