package com.drivemate.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 18.3: a failing weather or hazard source is shown as failing, and a stale success is not shown as current. */
class Cat18SourceHealthTest {
    @Test fun weatherOutageIsReportedAndRecovers() {
        val url = "https://api.open-meteo.com/v1/forecast"
        assertEquals("Weather", SourceHealth.label(url))
        SourceHealth.failure(url, 40, IllegalStateException("down"))
        val failing = SourceHealth.summary().lines().single { it.contains("Weather") }
        assertTrue(failing.startsWith("⚠ Weather"))
        SourceHealth.success(url, 25)
        val ok = SourceHealth.summary().lines().single { it.contains("Weather") }
        assertTrue(ok.startsWith("✓ Weather"))
    }

    @Test fun staleSuccessIsNotShownAsCurrent() {
        val url = "https://api.postcodes.io/postcodes/M11AE"
        SourceHealth.success(url, 10)
        val later = System.currentTimeMillis() + 31 * 60_000L
        val line = SourceHealth.summary(later).lines().single { it.contains("Postcodes") }
        assertTrue(line.startsWith("• Postcodes"))
    }

    @Test fun unknownHostsAndBadUrlsHaveFixedLabels() {
        assertEquals("Network", SourceHealth.label("not a url"))
        assertEquals("example.com", SourceHealth.label("https://example.com/x"))
    }
}
