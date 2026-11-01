package com.drivemate.app.nav

import com.drivemate.app.data.MemoryPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/** Category 22.1 and 22.2: duplicate and partial trips are kept or removed exactly, never merged or invented. */
class C22TripEdgeCasesTest {
    private val origin = LatLon(53.40, -2.20)

    @Test fun deletingOneTripRemovesEveryCopyWithTheSameStartAndDestination() {
        val history = TripHistory(MemoryPreferences())
        val copy = TripSummary("Work", 5_000L, 600, 4_000.0, 0, emptyList())
        history.add(copy)
        history.add(copy)
        history.add(TripSummary("Gym", 6_000L, 300, 1_000.0, 0, emptyList()))
        history.delete(copy)
        assertEquals(listOf("Gym"), history.all().map { it.destination })
    }

    @Test fun samePlaceAtADifferentStartTimeIsKeptAsSeparateTrip() {
        val history = TripHistory(MemoryPreferences())
        history.add(TripSummary("Work", 5_000L, 600, 4_000.0, 0, emptyList()))
        history.add(TripSummary("Work", 9_000L, 600, 4_000.0, 0, emptyList()))
        history.delete(TripSummary("Work", 5_000L, 600, 4_000.0, 0, emptyList()))
        assertEquals(listOf(9_000L), history.all().map { it.startedAt })
    }

    @Test fun tripWithNoAccurateFixesRecordsNoDistanceAndNoSpeeding() {
        val stats = TripStats("Work", 0L)
        stats.fix(origin, 80f, 1_000L, over = true, speed = 40, limit = 30, road = "A6")
        stats.fix(LatLon(origin.lat + 0.001, origin.lon), 80f, 60_000L, over = true, speed = 40, limit = 30, road = "A6")
        val trip = stats.finish(120_000L)
        assertEquals(0.0, trip.metres, 0.0)
        assertEquals(120, trip.seconds)
    }

    @Test fun partialTripKeepsItsRealDurationWithoutInventingDistance() {
        val stats = TripStats("Work", 0L)
        stats.fix(origin, 5f, 1_000L, over = false, speed = 0, limit = null, road = "")
        val trip = stats.finish(300_000L)
        assertEquals(300, trip.seconds)
        assertEquals(TripSource.RECORDED, trip.source)
    }
}
