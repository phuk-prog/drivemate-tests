package com.drivemate.app.data

import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 24.3: every price names the provider whose feed it came from, and a missing name is never guessed. */
class C24ProviderProvenanceTest {
    private val now = 1_800_000_000_000L
    private val prices = mapOf("E10" to 145.9)

    @Test fun summaryStartsWithTheProviderName() {
        val text = FuelStation("Tesco", LatLon(53.4, -2.2), prices, now - 60_000L).summary(now)
        assertTrue(text, text.startsWith("Tesco · Unleaded 145.9p"))
    }

    @Test fun blankProviderNameIsSaidNotInvented() {
        val text = FuelStation("  ", LatLon(53.4, -2.2), prices, now - 60_000L).summary(now)
        assertTrue(text, text.startsWith("Provider not named · Unleaded 145.9p"))
    }

    @Test fun stationWithNoPricesShowsNoSummaryAtAll() {
        assertEquals("", FuelStation("Tesco", LatLon(53.4, -2.2), emptyMap(), now).summary(now))
    }

    @Test fun providerNameDoesNotHideTheAgeWarning() {
        val text = FuelStation("Asda", LatLon(53.4, -2.2), prices, now - 5 * 3_600_000L).summary(now)
        assertTrue(text, text.contains("feed checked 5 h ago, old price not current"))
        assertFalse(text, text.contains("Provider not named"))
    }
}
