package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 22.3 and 22.4: distances use only accurate fixes; diagnostics hold no locations or free text. */
class C22TripStatsTest {
    private val origin = LatLon(53.40, -2.20)
    private fun east(metres: Double) = LatLon(origin.lat, origin.lon + metres / (111_195.0 * Math.cos(Math.toRadians(origin.lat))))

    @Test fun distanceIgnoresPoorAccuracyFixes() {
        val stats = TripStats("Work", 0L)
        stats.fix(origin, 5f, 0L, over = false, speed = 0, limit = null, road = "")
        stats.fix(east(100.0), 5f, 10_000L, over = false, speed = 0, limit = null, road = "")
        stats.fix(east(400.0), 80f, 20_000L, over = false, speed = 0, limit = null, road = "")
        val trip = stats.finish(30_000L)
        assertEquals(100.0, trip.metres, 1.0)
        assertEquals(30, trip.seconds)
        assertEquals(TripSource.RECORDED, trip.source)
    }

    @Test fun shortCreepOverTheLimitIsNotListed() {
        val stats = TripStats("Work", 0L)
        stats.fix(origin, 5f, 1_000L, over = true, speed = 32, limit = 30, road = "Hillgate")
        stats.fix(origin, 5f, 5_000L, over = true, speed = 32, limit = 30, road = "Hillgate")
        stats.fix(origin, 5f, 6_000L, over = false, speed = 28, limit = 30, road = "Hillgate")
        assertTrue(stats.finish(7_000L).speeding.isEmpty())
    }

    @Test fun sustainedSpeedingIsListedWithTopSpeed() {
        val stats = TripStats("Work", 0L)
        stats.fix(origin, 5f, 1_000L, over = true, speed = 34, limit = 30, road = "Hillgate")
        stats.fix(origin, 5f, 13_000L, over = true, speed = 38, limit = 30, road = "Hillgate")
        stats.fix(origin, 5f, 14_000L, over = false, speed = 28, limit = 30, road = "Hillgate")
        val moment = stats.finish(15_000L).speeding.single()
        assertEquals(38, moment.top)
        assertEquals(12, moment.seconds)
    }

    @Test fun driveDiagnosticsHoldNoLocationsOrText() {
        val totals = DriveDiagnosticTotals()
        totals.fix(4.0, 80)
        totals.mark()
        val text = totals.summary()
        assertTrue(text.contains("GPS fixes: 1"))
        assertTrue(text.contains("No location trace, destination, spoken instructions or problem text is stored."))
        assertFalse(Regex("\\d{2}\\.\\d{3,}").containsMatchIn(text))
    }
}
