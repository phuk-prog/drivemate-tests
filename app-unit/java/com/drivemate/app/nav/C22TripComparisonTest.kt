package com.drivemate.app.nav

import com.drivemate.app.data.MemoryPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 22.3: the GPS distance and the planned route are compared with numbers, and the estimate is labelled. */
class C22TripComparisonTest {
    private val t0 = 1_800_000_000_000L

    @Test fun longerGpsDistanceGivesAPositivePercentage() {
        val r = requireNotNull(RouteComparison.of(5_200.0, 4_900.0))
        assertEquals(6, r.percent)
        assertTrue(RouteComparison.text(r).contains("difference +6%"))
        assertTrue(RouteComparison.text(r).contains("GPS measured 5.2 km"))
        assertTrue(RouteComparison.text(r).contains("planned route estimate 4.9 km"))
    }

    @Test fun shorterGpsDistanceGivesANegativePercentage() {
        val r = requireNotNull(RouteComparison.of(4_500.0, 4_900.0))
        assertEquals(-8, r.percent)
        assertTrue(RouteComparison.text(r).contains("difference -8%"))
    }

    @Test fun missingOrUnusableDistancesGiveNoComparison() {
        assertNull(RouteComparison.of(5_000.0, null))
        assertNull(RouteComparison.of(5_000.0, 0.0))
        assertNull(RouteComparison.of(0.0, 4_900.0))
        assertNull(RouteComparison.of(Double.NaN, 4_900.0))
    }

    @Test fun recordedTripShowsTheComparisonAsAnEstimateLine() {
        val lines = TripFigures.lines(TripSummary("Work", t0, 600, 5_200.0, 0, emptyList(), TripSource.RECORDED, plannedMetres = 4_900.0))
        assertTrue(lines.any { it.startsWith("GPS measured 5.2 km; planned route estimate 4.9 km") })
    }

    @Test fun plannedOnlyTripHasNoMeasuredComparison() {
        val lines = TripFigures.lines(TripSummary("Work", t0, 600, 5_200.0, 0, emptyList(), TripSource.PLANNED, plannedMetres = 4_900.0))
        assertTrue(lines.none { it.startsWith("GPS measured") })
    }

    @Test fun plannedDistanceSurvivesTheHistoryAndOldEntriesReadAsUnknown() {
        val prefs = MemoryPreferences()
        TripHistory(prefs).add(TripSummary("Work", t0, 600, 5_200.0, 0, emptyList(), plannedMetres = 4_900.0))
        TripHistory(prefs).add(TripSummary("Gym", t0 + 1, 300, 1_000.0, 0, emptyList()))
        val byName = TripHistory(prefs).all().associateBy { it.destination }
        assertEquals(4_900.0, byName.getValue("Work").plannedMetres!!, 1e-9)
        assertNull(byName.getValue("Gym").plannedMetres)
    }

    @Test fun finishedTripCarriesThePlannedDistance() {
        val stats = TripStats("Work", t0).also { it.plannedMetres = 4_900.0 }
        assertEquals(4_900.0, stats.finish(t0 + 600_000).plannedMetres!!, 1e-9)
    }
}
