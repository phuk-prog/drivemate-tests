package com.drivemate.app

import com.drivemate.app.controller.NavigationReadiness
import com.drivemate.app.controller.TripRecovery
import com.drivemate.app.data.Place
import com.drivemate.app.data.Route
import com.drivemate.app.data.RouteStep
import com.drivemate.app.data.TripStore
import com.drivemate.app.nav.LatLon
import org.junit.Assert.*
import org.junit.Test
import android.app.Application
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class TripRecoveryDismissalTest {
    private val now = 1_001_000L
    private val destination = Place("Synthetic destination", "", LatLon(53.48, -2.24))
    private val saved = TripStore.Saved(null, destination, emptyList(), 1_000_000L)

    private fun ready(age: Long? = 0L, accuracy: Double? = 10.0, authoritative: Boolean = true,
                      precise: Boolean = true, services: Boolean = true, settings: Boolean = true,
                      quality: Int? = 100): Boolean =
        NavigationReadiness.canStart(settings, precise, services, age, accuracy, quality, authoritative)

    private fun dismiss(positionReady: Boolean, here: LatLon = destination.pos, accuracy: Double = 10.0) =
        TripRecovery.canDismissAsArrived(saved, here, accuracy, now, positionReady)

    @Test fun staleMissingAndFutureFixesPreserveTrip() {
        for (age in listOf<Long?>(8_001L, 60_000L, null, -1L))
            assertFalse("Unsafe fix age $age must preserve trip", dismiss(ready(age = age)))
    }

    @Test fun untrustedFixPreservesTrip() {
        assertFalse(dismiss(ready(authoritative = false)))
    }

    @Test fun unavailableSettingsPermissionOrServicesPreserveTrip() {
        assertFalse(dismiss(ready(settings = false)))
        assertFalse(dismiss(ready(precise = false)))
        assertFalse(dismiss(ready(services = false)))
    }

    @Test fun missingOrPoorQualityPreservesTrip() {
        assertFalse(dismiss(ready(quality = null)))
        assertFalse(dismiss(ready(quality = 0)))
    }

    @Test fun invalidAccuracyPreservesTrip() {
        for (accuracy in listOf<Double?>(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 50.1))
            assertFalse(dismiss(ready(accuracy = accuracy), accuracy = accuracy ?: Double.NaN))
    }

    @Test fun freshTrustedDestinationFixStillDismissesTrip() {
        for (age in listOf(0L, 8_000L)) for (accuracy in listOf(0.1, 10.0, 50.0))
            assertTrue(dismiss(ready(age = age, accuracy = accuracy), accuracy = accuracy))
    }

    @Test fun freshFixAwayFromDestinationPreservesTrip() {
        assertFalse(dismiss(ready(), here = LatLon(53.50, -2.20)))
    }

    @Test fun invalidSavedTripCannotBeDismissedAsArrived() {
        val expired = TripStore.Saved(null, destination, emptyList(), now - TripRecovery.MAX_AGE_MS - 1)
        assertFalse(TripRecovery.canDismissAsArrived(expired, destination.pos, 10.0, now, ready()))
    }

    private val pendingStop = Place("Synthetic stop", "", LatLon(53.49, -2.24))

    private fun routeWithPendingStop(): Route {
        val start = LatLon(53.50, -2.24)
        return Route(listOf(start, pendingStop.pos, destination.pos), 2_000.0, 120.0,
            listOf(
                RouteStep("Continue", "depart", "", "", 1_000.0, start, duration = 60.0),
                RouteStep("Stop", "stop", "", "Synthetic stop", 1_000.0, pendingStop.pos, duration = 60.0),
                RouteStep("Arrive", "arrive", "", "", 0.0, destination.pos)
            ), "Synthetic route")
    }

    private fun assertReplansPendingStops(trip: TripStore.Saved, accuracy: Double = 10.0) {
        val result = TripRecovery.plan(trip, destination.pos, accuracy, now)
        assertTrue("Unvisited stops must prevent completing the recovered trip", result is TripRecovery.Resume.Replan)
        result as TripRecovery.Resume.Replan
        assertEquals(trip.stops, result.stops)
        assertNull("An ended saved route must not bypass remaining stops", result.fallback)
    }

    @Test fun pendingStopsAtDestinationRequireReplan() {
        val trip = TripStore.Saved(null, destination, listOf(pendingStop), saved.savedAt)
        for (accuracy in listOf(0.1, 10.0, 50.0)) assertReplansPendingStops(trip, accuracy)
    }

    @Test fun pendingStopsKeepTheirOrderAndRepeatedVisits() {
        val second = Place("Another synthetic stop", "", LatLon(53.495, -2.245))
        val trip = TripStore.Saved(null, destination, listOf(pendingStop, second, pendingStop), saved.savedAt)
        assertReplansPendingStops(trip)
    }

    @Test fun savedRouteMustNotBypassPendingStopsNearDestination() {
        val trip = TripStore.Saved(routeWithPendingStop(), destination, listOf(pendingStop), saved.savedAt)
        assertReplansPendingStops(trip)
    }

    @Test fun pendingStopsPreventStartupDismissal() {
        val trip = TripStore.Saved(null, destination, listOf(pendingStop), saved.savedAt)
        assertFalse(TripRecovery.canDismissAsArrived(trip, destination.pos, 10.0, now, positionReady = true))
    }

    @Test fun pendingStopAtDestinationIsNotAssumedVisited() {
        val trip = TripStore.Saved(null, destination, listOf(destination), saved.savedAt)
        assertReplansPendingStops(trip)
    }

    @Test fun savedRouteAwayFromDestinationStillResumesNormally() {
        val route = routeWithPendingStop()
        val trip = TripStore.Saved(route, destination, listOf(pendingStop), saved.savedAt)
        val result = TripRecovery.plan(trip, route.points.first(), 10.0, now)
        assertTrue(result is TripRecovery.Resume.FollowSaved)
        result as TripRecovery.Resume.FollowSaved
        assertSame(route, result.route)
        assertEquals(listOf(pendingStop), result.stops)
        assertEquals(0, result.stopsPassedOnRoute)
    }

    private fun tripFiles(block: (File, TripStore) -> Unit) {
        val directory = File(RuntimeEnvironment.getApplication().cacheDir, "synthetic-trip-persistence")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val file = File(directory, "current-trip.json")
        try { block(file, TripStore(file)) } finally { directory.deleteRecursively() }
    }

    @Test fun clearingStoppedTripRemovesInterruptedStagingFile() = tripFiles { file, store ->
        val staging = File(file.path + ".part")
        staging.writeText("synthetic unfinished trip data")
        assertTrue(store.clear())
        assertFalse("Ended-trip staging data must be removed", staging.exists())
        assertNull(store.load())
    }

    @Test fun failedTripSaveDoesNotRetainPrivateStagingData() = tripFiles { file, store ->
        // A nonempty destination directory deterministically rejects rename without root/permissions tricks.
        assertTrue(file.mkdir())
        File(file, "synthetic obstruction").writeText("occupied")
        assertFalse(store.save(routeWithPendingStop(), destination, listOf(pendingStop)))
        assertFalse("Failed saves must clean their staging data", File(file.path + ".part").exists())
    }

    @Test fun ordinaryTripSaveAndClearPreserveRecoveryUntilCleared() = tripFiles { file, store ->
        val stops = listOf(pendingStop, destination, pendingStop)
        assertTrue(store.save(routeWithPendingStop(), destination, stops))
        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(destination, loaded!!.destination)
        assertEquals(stops, loaded.stops)
        assertEquals(routeWithPendingStop().points, loaded.route!!.points)
        assertFalse(File(file.path + ".part").exists())
        assertTrue(store.clear())
        assertNull(store.load())
        assertFalse(file.exists())
    }

    @Test fun failedStagingWritePreservesPreviousRecoverableTrip() = tripFiles { file, store ->
        assertTrue(store.save(routeWithPendingStop(), destination, listOf(pendingStop)))
        val staging = File(file.path + ".part")
        assertTrue(staging.mkdir())
        File(staging, "synthetic obstruction").writeText("occupied")
        assertFalse(store.save(routeWithPendingStop(), pendingStop, emptyList()))
        assertEquals(destination, store.load()!!.destination)
        assertEquals(listOf(pendingStop), store.load()!!.stops)
    }

    @Test fun clearReportsFailureIfStagingDataCannotBeRemoved() = tripFiles { file, store ->
        val staging = File(file.path + ".part")
        assertTrue(staging.mkdir())
        File(staging, "synthetic obstruction").writeText("occupied")
        assertFalse("Incomplete trip deletion must not report success", store.clear())
    }


    private fun historyTrip(index: Int) = com.drivemate.app.nav.TripSummary(
        "Synthetic journey " + index, 1_700_000_000_000L + index * 1_000L,
        60 + index, 500.0 + index, index,
        listOf(com.drivemate.app.nav.SpeedingMoment("Synthetic road", 30, 34, 10)),
        com.drivemate.app.nav.TripSource.SYNTHETIC)

    private fun historyFixture(block: (android.content.SharedPreferences, com.drivemate.app.nav.TripHistory) -> Unit) {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("synthetic-history", 0)
        assertTrue(prefs.edit().clear().commit())
        try { block(prefs, com.drivemate.app.nav.TripHistory(prefs)) }
        finally { assertTrue(prefs.edit().clear().commit()) }
    }

    private fun insertMalformedHistoryEntry(prefs: android.content.SharedPreferences) {
        val existing = org.json.JSONArray(prefs.getString("tripHistory", "[]"))
        existing.put(1, org.json.JSONObject().put("dest", "Synthetic incomplete entry"))
        assertTrue(prefs.edit().putString("tripHistory", existing.toString()).commit())
    }

    @Test fun malformedHistoryEntryDoesNotHideOtherJourneys() = historyFixture { prefs, history ->
        val older = historyTrip(1)
        val newer = historyTrip(2)
        history.add(older)
        history.add(historyTrip(0))
        history.add(newer)
        insertMalformedHistoryEntry(prefs)
        assertEquals(listOf(newer, older), history.all())
    }

    @Test fun addingJourneyAfterMalformedEntryPreservesValidHistory() = historyFixture { prefs, history ->
        val older = historyTrip(1)
        val newer = historyTrip(2)
        history.add(older)
        history.add(historyTrip(0))
        history.add(newer)
        insertMalformedHistoryEntry(prefs)
        val latest = historyTrip(3)
        history.add(latest)
        assertEquals(listOf(latest, newer, older), history.all())
    }

    @Test fun deletingJourneyAfterMalformedEntryPreservesOtherValidHistory() = historyFixture { prefs, history ->
        val older = historyTrip(1)
        val newer = historyTrip(2)
        history.add(older)
        history.add(historyTrip(0))
        history.add(newer)
        insertMalformedHistoryEntry(prefs)
        history.delete(newer)
        assertEquals(listOf(older), history.all())
    }

    @Test fun historyRoundTripPreservesSourceAndSpeedingSummary() = historyFixture { _, history ->
        val trip = historyTrip(1)
        history.add(trip)
        assertEquals(listOf(trip), history.all())
    }

    @Test fun legacyHistoryWithoutSourceRemainsRecorded() = historyFixture { prefs, history ->
        val trip = historyTrip(1)
        history.add(trip)
        val rows = org.json.JSONArray(prefs.getString("tripHistory", "[]"))
        rows.getJSONObject(0).remove("src")
        assertTrue(prefs.edit().putString("tripHistory", rows.toString()).commit())
        assertEquals(listOf(trip.copy(source = com.drivemate.app.nav.TripSource.RECORDED)), history.all())
    }

    @Test fun historyStillKeepsNewestFiftyJourneys() = historyFixture { _, history ->
        (0..50).forEach { history.add(historyTrip(it)) }
        assertEquals((50 downTo 1).map { historyTrip(it) }, history.all())
    }


    @Test fun unreadableHistoryContainerSurvivesArrivalAdd() = historyFixture { prefs, history ->
        for (raw in listOf("{", "not JSON", "{}")) {
            assertTrue(prefs.edit().putString("tripHistory", raw).commit())
            history.add(historyTrip(1))
            assertEquals("Unreadable history must survive an arrival update", raw, prefs.getString("tripHistory", null))
        }
    }

    @Test fun unreadableHistoryContainerSurvivesSelectiveDelete() = historyFixture { prefs, history ->
        val raw = "{synthetic unreadable history"
        assertTrue(prefs.edit().putString("tripHistory", raw).commit())
        history.delete(historyTrip(1))
        assertEquals(raw, prefs.getString("tripHistory", null))
    }

    @Test fun unreadableHistoryContainerSurvivesRetentionCleanup() = historyFixture { prefs, history ->
        val raw = "{synthetic unreadable history"
        assertTrue(prefs.edit().putString("tripHistory", raw).commit())
        history.deleteOlderThan(30)
        assertEquals(raw, prefs.getString("tripHistory", null))
    }

    @Test fun explicitDeleteAllCanResetUnreadableHistory() = historyFixture { prefs, history ->
        assertTrue(prefs.edit().putString("tripHistory", "{synthetic unreadable history").commit())
        history.deleteOlderThan(0, all = true)
        assertEquals("[]", prefs.getString("tripHistory", null))
        assertTrue(history.all().isEmpty())
    }

    @Test fun wrongTypedHistoryValueSurvivesArrivalAdd() = historyFixture { prefs, history ->
        assertTrue(prefs.edit().putBoolean("tripHistory", true).commit())
        history.add(historyTrip(1))
        assertEquals(true, prefs.all["tripHistory"])
    }

    @Test fun historyEditorFailureIsReportedWithoutThrowing() = historyFixture { prefs, history ->
        val before = historyTrip(1)
        history.add(before)
        val rejecting = object : android.content.SharedPreferences by prefs {
            override fun edit(): android.content.SharedPreferences.Editor {
                val delegate = prefs.edit()
                return object : android.content.SharedPreferences.Editor by delegate {
                    override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor {
                        delegate.putString(key, value)
                        return this
                    }
                    override fun apply() { throw IllegalStateException("Synthetic rejected history write") }
                }
            }
        }
        val guarded = com.drivemate.app.nav.TripHistory(rejecting)
        assertEquals(false, guarded.add(historyTrip(2)) as Any)
        assertEquals(listOf(before), history.all())
    }

    @Test fun invalidHistorySummaryIsReportedWithoutThrowing() = historyFixture { _, history ->
        val before = historyTrip(1)
        history.add(before)
        assertEquals(false, history.add(historyTrip(2).copy(metres = Double.NaN)) as Any)
        assertEquals(listOf(before), history.all())
    }

    @Test fun historyUpdateOutcomesDistinguishAcceptedAndRejectedWrites() = historyFixture { prefs, history ->
        assertEquals(true, history.add(historyTrip(1)) as Any)
        assertTrue(prefs.edit().putString("tripHistory", "{synthetic unreadable history").commit())
        assertEquals(false, history.delete(historyTrip(1)) as Any)
        assertEquals(false, history.deleteOlderThan(30) as Any)
        assertEquals(true, history.deleteOlderThan(0, all = true) as Any)
        assertEquals(true, history.add(historyTrip(2)) as Any)
        assertEquals(listOf(historyTrip(2)), history.all())
    }

}
