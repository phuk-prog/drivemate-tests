package com.drivemate.app.data

import com.drivemate.app.data.MemoryPreferences
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.TripHistory
import com.drivemate.app.nav.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Category 22.5: the current trip and past trips survive a restart, and cleared or expired ones stay gone. */
class C22TripRetentionTest {
    @get:Rule val folder = TemporaryFolder()

    private val route = Route(listOf(LatLon(53.40, -2.20), LatLon(53.41, -2.21)), 1000.0, 120.0, emptyList(), "test")
    private val dest = Place("Work", "1 Test Street", LatLon(53.41, -2.21))

    @Test fun currentTripSurvivesARestartUntilItIsCleared() {
        val file = folder.newFile("current-trip.json")
        assertEquals(true, TripStore(file).save(route, dest, emptyList()))
        assertEquals("Work", assertNotNull(TripStore(file).load()).destination.name)
        assertEquals(true, TripStore(file).clear())
        assertNull(TripStore(file).load())
    }

    @Test fun pastTripsSurviveARestartAndExpiredOnesAreRemoved() {
        val prefs = MemoryPreferences()
        val now = System.currentTimeMillis()
        TripHistory(prefs).add(TripSummary("Old", now - 40 * 86_400_000L, 600, 5_000.0, 0, emptyList()))
        TripHistory(prefs).add(TripSummary("Recent", now - 2 * 86_400_000L, 600, 5_000.0, 0, emptyList()))
        // A fresh TripHistory on the same stored preferences stands in for an app restart.
        TripHistory(prefs).deleteOlderThan(30)
        assertEquals(listOf("Recent"), TripHistory(prefs).all().map { it.destination })
    }
}
