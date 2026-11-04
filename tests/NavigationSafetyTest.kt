package com.drivemate.app

import com.drivemate.app.data.RouteRequest
import com.drivemate.app.map.LocationComponentPermissionPolicy
import com.drivemate.app.nav.ArrivalPolicy
import com.drivemate.app.nav.LatLon
import org.junit.Assert.*
import org.junit.Test

class NavigationSafetyTest {
    @Test fun revokedPermissionNeverEnablesLocation() {
        for (reliable in listOf(false, true))
            assertFalse(LocationComponentPermissionPolicy.enabled(false, reliable))
    }
    @Test fun unreliablePositionNeverEnablesLocation() {
        assertFalse(LocationComponentPermissionPolicy.enabled(true, false))
        assertTrue(LocationComponentPermissionPolicy.enabled(true, true))
    }
    @Test fun estimatedOrUnsafePositionNeverCompletesTrip() {
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, true))
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, false, false))
    }
    @Test fun passingDestinationBeforeTurnaroundDoesNotCompleteTrip() {
        assertFalse(ArrivalPolicy.arrived(500.0, 50.0, 5.0, false))
        assertTrue(ArrivalPolicy.arrived(10.0, 5.0, 5.0, false))
    }
    @Test fun nonFiniteArrivalEvidenceNeverCompletesTrip() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(ArrivalPolicy.arrived(bad, 0.0, 0.0, false))
            assertFalse(ArrivalPolicy.arrived(0.0, bad, 0.0, false))
            assertFalse(ArrivalPolicy.arrived(0.0, 0.0, bad, false))
        }
    }
    @Test fun malformedCoordinatesAreRejected() {
        for (p in listOf(LatLon(Double.NaN, 0.0), LatLon(0.0, Double.POSITIVE_INFINITY),
            LatLon(90.01, 0.0), LatLon(0.0, -180.01))) assertFalse(RouteRequest.valid(p))
        assertTrue(RouteRequest.valid(LatLon(53.48, -2.24)))
    }
    @Test fun invalidHeadingsAreDroppedAndFiniteHeadingsNormalized() {
        assertNull(RouteRequest.heading(Float.NaN))
        assertNull(RouteRequest.heading(Float.POSITIVE_INFINITY))
        assertNull(RouteRequest.heading(null))
        assertEquals(270f, RouteRequest.heading(-90f)!!, 0f)
        assertEquals(90f, RouteRequest.heading(450f)!!, 0f)
    }
    @Test fun invalidTripPointCountsAndSamePlaceAreRejected() {
        val a = LatLon(53.48, -2.24)
        val b = LatLon(53.50, -2.20)
        for (points in listOf(emptyList(), listOf(a), listOf(a, a), List(28) { if (it % 2 == 0) a else b })) {
            try { RouteRequest.check(points); fail("Invalid trip was accepted") }
            catch (_: IllegalArgumentException) {}
        }
        RouteRequest.check(listOf(a, b))
    }
    @Test fun routeBudgetsUseElapsedTimeAndDoNotBecomeNegative() {
        var now = 100L
        val budget = RouteRequest.Budget(firstMs = 1000, totalMs = 2000, clock = { now })
        now = 600L
        assertEquals(500L, budget.leftForFirst())
        assertEquals(1500L, budget.leftForExtras())
        now = 3100L
        assertEquals(0L, budget.leftForFirst())
        assertEquals(0L, budget.leftForExtras())
    }

    @Test fun mergeRestorePreservesExistingSettingsAndAddsMissingValues() {
        val current = mapOf<String, Any?>("avoidTolls" to true, "voiceTiming" to 2)
        val backup = mapOf<String, Any>("avoidTolls" to false, "voiceTiming" to 0, "night" to 1)
        val result = com.drivemate.app.data.BackupRestorePlan.values(
            com.drivemate.app.data.BackupRestorePlan.Mode.MERGE, current, backup)
        assertEquals(true, result["avoidTolls"])
        assertEquals(2, result["voiceTiming"])
        assertEquals(1, result["night"])
        assertEquals(mapOf<String, Any?>("avoidTolls" to true, "voiceTiming" to 2), current)
    }

    @Test fun replaceRestoreDropsValuesMissingFromBackup() {
        val current = mapOf<String, Any?>("avoidTolls" to true, "voiceTiming" to 2)
        val backup = mapOf<String, Any>("night" to 1)
        val result = com.drivemate.app.data.BackupRestorePlan.values(
            com.drivemate.app.data.BackupRestorePlan.Mode.REPLACE, current, backup)
        assertEquals(backup, result)
        assertFalse(result.containsKey("avoidTolls"))
        assertFalse(result.containsKey("voiceTiming"))
    }

    @Test fun mergeKeepsLearnedSpeedsWhileReplaceImportsThem() {
        assertFalse(com.drivemate.app.data.BackupRestorePlan.importLearned(
            com.drivemate.app.data.BackupRestorePlan.Mode.MERGE))
        assertTrue(com.drivemate.app.data.BackupRestorePlan.importLearned(
            com.drivemate.app.data.BackupRestorePlan.Mode.REPLACE))
    }

    @Test fun invalidRecoveryAccuracyCannotCompleteSavedTrip() {
        val destination = com.drivemate.app.data.Place("Test destination", "", LatLon(53.48, -2.24))
        val saved = com.drivemate.app.data.TripStore.Saved(null, destination, emptyList(), 1_000_000L)
        for (accuracy in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, 51.0)) {
            val result = com.drivemate.app.controller.TripRecovery.plan(saved, destination.pos, accuracy, 1_001_000L)
            assertTrue("Invalid accuracy must preserve a recoverable trip",
                result is com.drivemate.app.controller.TripRecovery.Resume.Replan)
        }
    }

    @Test fun validRecoveryAccuracyStillRecognizesDestination() {
        val destination = com.drivemate.app.data.Place("Test destination", "", LatLon(53.48, -2.24))
        val saved = com.drivemate.app.data.TripStore.Saved(null, destination, emptyList(), 1_000_000L)
        for (accuracy in listOf(0.1, 10.0, 50.0)) {
            assertEquals(com.drivemate.app.controller.TripRecovery.Resume.AlreadyThere,
                com.drivemate.app.controller.TripRecovery.plan(saved, destination.pos, accuracy, 1_001_000L))
        }
    }

    @Test fun optionalRoutingWorkCannotExtendTotalBudget() {
        var now = 0L
        val budget = RouteRequest.Budget(firstMs = 1_000, totalMs = 2_000, clock = { now })
        now = 1_750
        assertEquals(250L, budget.leftForExtras())
        now = 2_001
        assertEquals(0L, budget.leftForExtras())
    }

    @Test fun expiredDmnRequestDoesNotOpenRoadData() {
        val dmn = com.drivemate.app.dmn.Dmn
        val original = dmn.source
        var opens = 0
        try {
            dmn.forget()
            dmn.source = { opens++; throw java.io.IOException("Synthetic unavailable data") }
            assertTrue(dmn.route(listOf(LatLon(53.48, -2.24), LatLon(53.49, -2.23)),
                null, deadlineNanos = System.nanoTime() - 1).isEmpty())
            assertEquals(0, opens)
        } finally {
            dmn.source = original
            dmn.forget()
        }
    }

    private fun shortRoadRouter(): com.drivemate.app.dmn.DmnRouter {
        val p = com.drivemate.app.dmn.DmnPack(
            lat = doubleArrayOf(53.48, 53.481, 53.482),
            lon = doubleArrayOf(-2.24, -2.24, -2.24),
            nodeId = longArrayOf(1, 2, 3),
            wayId = longArrayOf(10),
            wayRoundabout = booleanArrayOf(false),
            wayName = arrayOf("Synthetic road"),
            wayRef = arrayOf(""),
            wayHighway = arrayOf("residential"),
            from = intArrayOf(0, 1), to = intArrayOf(1, 2),
            metres = floatArrayOf(100f, 100f), kph = floatArrayOf(30f, 30f),
            access = byteArrayOf(0, 0), startAccess = byteArrayOf(0, 0),
            finishAccess = byteArrayOf(0, 0), restricted = booleanArrayOf(false, false),
            way = intArrayOf(0, 0), banPairs = longArrayOf(), sequences = emptyList(),
            uturnNode = booleanArrayOf(false, false, false), metadata = "synthetic test")
        return com.drivemate.app.dmn.DmnRouter(p)
    }

    @Test fun cancelledSearchStopsEvenOnShortRoadGraph() {
        try {
            shortRoadRouter().plan(0, 1, stop = { true })
            fail("Cancelled search returned a route")
        } catch (e: com.drivemate.app.dmn.DmnRouter.NoRoute) {
            assertEquals("Cancelled", e.message)
        }
    }

    @Test fun interruptedSearchStopsEvenOnShortRoadGraph() {
        val router = shortRoadRouter()
        try {
            Thread.currentThread().interrupt()
            try {
                router.plan(0, 1)
                fail("Interrupted search returned a route")
            } catch (e: com.drivemate.app.dmn.DmnRouter.NoRoute) {
                assertEquals("Cancelled", e.message)
            }
        } finally { Thread.interrupted() }
    }

    @Test fun uncancelledShortRoadSearchStillFindsRoute() {
        val route = shortRoadRouter().plan(0, 1)
        assertArrayEquals(intArrayOf(1), route.edges)
        assertTrue(route.metres > 0)
        assertTrue(route.seconds.isFinite() && route.seconds > 0)
    }
}
