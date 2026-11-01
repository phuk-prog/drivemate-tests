package com.drivemate.app.data

import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 24.4 (summary part): a price is shown as current only when its copy is recent; dates and unknown ages are always stated. */
class C24FuelSelectionTest {
    private val now = 1_800_000_000_000L
    private val prices = mapOf("E10" to 145.9)

    @Test fun onlineRecentCopyIsCurrentAndDated() {
        val text = FuelStation("Tesco", LatLon(53.4, -2.2), prices, now - 10 * 60_000L).summary(now)
        assertTrue(text, text.contains("feed checked 10 min ago"))
        assertFalse(text, text.contains("not current"))
    }

    @Test fun offlineOldCopyIsNotCurrent() {
        val text = FuelStation("Tesco", LatLon(53.4, -2.2), prices, now - 26 * 3_600_000L).summary(now)
        assertTrue(text, text.contains("old price not current"))
    }

    @Test fun neverFetchedCopyIsNotCurrent() {
        val text = FuelStation("Tesco", LatLon(53.4, -2.2), prices, 0L).summary(now)
        assertTrue(text, text.contains("price age unknown, not current"))
    }

    @Test fun stationWithoutPricesOffersNoSummary() {
        assertEquals("", FuelStation("Tesco", LatLon(53.4, -2.2), emptyMap(), now).summary(now))
    }
}
