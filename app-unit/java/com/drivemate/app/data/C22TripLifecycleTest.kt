package com.drivemate.app.data

import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.TripHistory
import com.drivemate.app.nav.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Category 22.1, 22.2 and 22.5: trip save/clear boundaries, live-route privacy, safe deletion and retention. */
class C22TripLifecycleTest {
    @get:Rule val folder = TemporaryFolder()

    private val here = LatLon(53.40, -2.20)
    private val there = LatLon(53.41, -2.21)
    private val dest = Place("Work", "1 Test Street", there)
    private val step = RouteStep("Head north", "depart", "", "North Road", 1000.0, here)
    private val route = Route(listOf(here, there), 1000.0, 120.0, listOf(step), "test")

    @Test fun savedTripReloadsWithRouteDestinationAndStops() {
        val store = TripStore(folder.newFile("current-trip.json"))
        assertTrue(store.save(route, dest, listOf(Place("Shop", "", here))))
        val saved = assertNotNull(store.load())
        assertEquals("Work", saved.destination.name)
        assertEquals(1, saved.stops.size)
        assertEquals(2, saved.route!!.points.size)
        assertTrue(saved.savedAt > 0)
    }

    @Test fun liveTrafficRouteIsNeverWrittenToDisk() {
        val file = folder.newFile("current-trip.json")
        val store = TripStore(file)
        val live = route.copy(live = true)
        assertTrue(store.save(live, dest, emptyList()))
        assertNull("TomTom live route must not be persisted", store.load()!!.route)
        assertFalse(file.readText().contains("\"route\""))
    }

    @Test fun clearRemovesTheTripAndCorruptFilesAreIgnored() {
        val file = folder.newFile("current-trip.json")
        val store = TripStore(file)
        store.save(route, dest, emptyList())
        assertTrue(store.clear())
        assertNull(store.load())
        file.writeText("{not json")
        assertNull("corrupt trip data must not be offered", store.load())
    }

    @Test fun historyDeletesOnlyTheChosenTrip() {
        val history = TripHistory(MemoryPreferences())
        val a = trip("Work", 1_000L)
        val b = trip("Gym", 2_000L)
        history.add(a); history.add(b)
        history.delete(a)
        assertEquals(listOf(b), history.all())
    }

    @Test fun historyKeepsOnlyTheLatestFiftyTrips() {
        val history = TripHistory(MemoryPreferences())
        for (i in 1..60) history.add(trip("Stop $i", i * 1_000L))
        val all = history.all()
        assertEquals(50, all.size)
        assertEquals("Stop 60", all.first().destination)
        assertEquals("Stop 11", all.last().destination)
    }

    @Test fun retentionRemovesOnlyTripsOlderThanTheWindow() {
        val history = TripHistory(MemoryPreferences())
        val now = System.currentTimeMillis()
        history.add(trip("Old", now - 40 * 86_400_000L))
        history.add(trip("Recent", now - 2 * 86_400_000L))
        history.deleteOlderThan(30)
        assertEquals(listOf("Recent"), history.all().map { it.destination })
        history.deleteOlderThan(1, all = true)
        assertTrue(history.all().isEmpty())
    }

    private fun trip(name: String, start: Long) = TripSummary(name, start, 600, 5_000.0, 0, emptyList())
}
