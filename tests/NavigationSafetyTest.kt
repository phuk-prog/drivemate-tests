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
    // These cases catch malformed fixes triggering a reroute, not just a changed threshold.
    @Test fun invalidOffRouteEvidenceNeverTriggersRerouting() {
        val rule = com.drivemate.app.nav.OffRouteRule
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(rule.clearlyOff(bad, 5.0, false, false, 10.0))
            assertFalse(rule.clearlyOff(200.0, bad, false, false, 10.0))
            assertFalse(rule.clearlyOff(200.0, 5.0, false, false, bad))
            assertFalse(rule.turnedOffWithoutMap(bad, 5.0, 10.0, false))
            assertFalse(rule.turnedOffWithoutMap(200.0, bad, 10.0, false))
            assertFalse(rule.turnedOffWithoutMap(200.0, 5.0, bad, false))
            assertFalse(rule.wrongTurn(10.0, false, bad, 5.0, 180.0, listOf(0.0)))
            assertFalse(rule.wrongTurn(10.0, false, 200.0, bad, 180.0, listOf(0.0)))
            assertFalse(rule.wrongTurn(bad, false, 200.0, 5.0, 180.0, listOf(0.0)))
        }
        assertTrue(rule.clearlyOff(200.0, 5.0, false, false, 10.0))
        assertTrue(rule.turnedOffWithoutMap(200.0, 5.0, 10.0, false))
    }

    // Matching the road after a bend must prevent a spurious gyro wrong turn.
    @Test fun wrongTurnRequiresDisagreementWithEveryUpcomingBearing() {
        val rule = com.drivemate.app.nav.OffRouteRule
        assertFalse(rule.wrongTurn(10.0, false, 50.0, 5.0, 90.0, listOf(0.0, 90.0)))
        assertTrue(rule.wrongTurn(10.0, false, 50.0, 5.0, 180.0, listOf(0.0, 90.0)))
        assertFalse(rule.wrongTurn(10.0, true, 50.0, 5.0, 180.0, listOf(0.0)))
        assertFalse(rule.wrongTurn(0.0, false, 50.0, 5.0, 180.0, listOf(0.0)))
        assertFalse(rule.wrongTurn(10.0, false, 50.0, 5.0, null, listOf(0.0)))
        assertFalse(rule.wrongTurn(10.0, false, 50.0, 5.0, 180.0, emptyList()))
        assertFalse(rule.wrongTurn(10.0, false, 50.0, 5.0, Double.NaN, listOf(0.0)))
        assertFalse(rule.wrongTurn(10.0, false, 50.0, 5.0, 180.0, listOf(Double.NaN)))
    }

    @Test fun fasterGpsSamplingCannotShortenOffRouteConfirmation() {
        val rule = com.drivemate.app.nav.OffRouteRule
        assertFalse(rule.lastedLongEnough(4, 1.5))
        assertTrue(rule.lastedLongEnough(4, 3.0))
        assertTrue(rule.lastedLongEnough(1, 0.0))
        assertFalse(rule.lastedLongEnough(0, 10.0))
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertFalse(rule.lastedLongEnough(4, bad))
    }

    @Test fun headingAgreementHandlesNorthWrapAndUnusableFixes() {
        val rule = com.drivemate.app.nav.OffRouteRule
        assertTrue(rule.headingAgrees(359.0, 10.0, 1.0))
        assertTrue(rule.headingAgrees(1.0, 10.0, 359.0))
        assertFalse(rule.headingAgrees(180.0, 10.0, 0.0))
        assertFalse(rule.headingAgrees(0.0, 0.0, 0.0))
        assertFalse(rule.headingAgrees(null, 10.0, 0.0))
        assertFalse(rule.headingAgrees(Double.NaN, 10.0, 0.0))
        assertFalse(rule.headingAgrees(0.0, Double.POSITIVE_INFINITY, 0.0))
        assertFalse(rule.headingAgrees(0.0, 10.0, Double.NaN))
    }

    @Test fun failedReroutesBackOffAndSuccessRestoresNormalRetry() {
        val policy = com.drivemate.app.nav.ReroutePolicy
        assertTrue(policy.mayReroute(7_001, 1_000, 0))
        assertFalse(policy.mayReroute(7_001, 1_000, 1))
        assertTrue(policy.mayReroute(13_001, 1_000, 1))
        assertFalse(policy.mayReroute(13_001, 1_000, 2))
        assertTrue(policy.mayReroute(25_001, 1_000, 2))
        assertFalse(policy.mayReroute(61_000, 1_000, Int.MAX_VALUE))
        assertTrue(policy.mayReroute(61_001, 1_000, Int.MAX_VALUE))
        assertTrue(policy.mayReroute(7_001, 1_000, 0))
    }

    @Test fun rerouteRejectsInvalidOrBackwardsClockEvidence() {
        val policy = com.drivemate.app.nav.ReroutePolicy
        assertFalse(policy.mayReroute(-1, 0, 0))
        assertFalse(policy.mayReroute(10_000, -1, 0))
        assertFalse(policy.mayReroute(1_000, 10_000, 0))
        assertFalse(policy.mayReroute(7_000, 1_000, 0))
        assertTrue(policy.mayReroute(7_001, 1_000, Int.MIN_VALUE))
    }

    @Test fun stoppedFixAloneNeverAuthorizesReplayWhileDriving() {
        val policy = com.drivemate.app.nav.ReplaySafety
        assertFalse(policy.allowed(false, false, 0.0, 0L))
        assertFalse(policy.allowed(true, true, 0.0, 0L))
        assertFalse(policy.allowed(false, true, 0.0, 0L))
        assertTrue(policy.allowed(true, false, 0.0, 0L))
    }

    @Test fun replayRequiresFreshFiniteStationaryEvidence() {
        val policy = com.drivemate.app.nav.ReplaySafety
        for (speed in listOf(null, -0.1, 0.5, 10.0, Double.NaN,
                             Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
            assertFalse(policy.allowed(true, false, speed, 0L))
        for (age in listOf(null, -1L, 5_001L, Long.MAX_VALUE))
            assertFalse(policy.allowed(true, false, 0.0, age))
        assertTrue(policy.allowed(true, false, 0.49, 5_000L))
    }

    @Test fun autoZoomChangesTheActualCameraTargetAndCanBeDisabled() {
        val camera = com.drivemate.app.map.AdaptiveDrivingCamera()
        val cruise = camera.update(30.0, 0L, 5_000.0, false, false, true)
        val fixed = camera.update(30.0, 1_000L, 5_000.0, false, false, false)
        val enabled = camera.update(30.0, 2_000L, 5_000.0, false, false, true)
        assertTrue("Fast-road Auto-zoom must show more road", cruise.zoom < fixed.zoom)
        assertEquals(cruise.zoom, enabled.zoom, 0.001)
        val turn = camera.update(30.0, 3_000L, 100.0, true, false, true)
        assertTrue("Approaching a turn must zoom closer", turn.zoom > fixed.zoom)
        val disabledAtTurn = camera.update(30.0, 4_000L, 100.0, true, false, false)
        assertEquals(fixed.zoom, disabledAtTurn.zoom, 0.001)
    }

    @Test fun reenabledAutoZoomUsesCurrentSpeedInsteadOfTheOldRoadBand() {
        val camera = com.drivemate.app.map.AdaptiveDrivingCamera()
        val fast = camera.update(30.0, 0L, 5_000.0, false, false, true)
        camera.update(0.0, 1_000L, 5_000.0, false, false, false)
        val slow = camera.update(0.0, 2_000L, 5_000.0, false, false, true)
        assertTrue(slow.zoom > fast.zoom)
        assertEquals(0.0, camera.smoothedSpeed, 0.001)
    }

    @Test fun staleCameraSamplesCannotUndoTheCurrentTarget() {
        val camera = com.drivemate.app.map.AdaptiveDrivingCamera()
        val target = camera.update(30.0, 2_000L, 5_000.0, false, false, true)
        assertEquals(target, camera.update(0.0, 1_000L, 100.0, true, true, false))
        assertEquals(target, camera.update(0.0, 2_000L, 100.0, true, true, false))
        assertEquals(target, camera.update(0.0, -1L, 100.0, true, true, false))
    }

    private fun preferenceRoute(seconds: Double, turns: Int, sideStreet: Boolean): com.drivemate.app.data.Route {
        val a = LatLon(53.48, -2.24)
        fun step(type: String, metres: Double, ref: String) = com.drivemate.app.data.RouteStep(
            "Synthetic instruction", type, if (type == "turn") "left" else "straight",
            "Synthetic road", metres, a, leftDriving = true, duration = metres / 8.0, ref = ref)
        val middle = if (turns == 0) listOf(step("continue", 2_000.0, if (sideStreet) "" else "A1"))
            else List(turns) { step("turn", 2_000.0 / turns, if (sideStreet) "" else "A1") }
        return com.drivemate.app.data.Route(listOf(a, LatLon(53.50, -2.20)), 4_000.0, seconds,
            listOf(step("depart", 1_000.0, "A1")) + middle + step("arrive", 1_000.0, "A1"), "Synthetic")
    }

    @Test fun routePreferencesChangeSelectionOnlyWithinTheirTimeAllowance() {
        val fast = preferenceRoute(600.0, 5, true)
        val simple = preferenceRoute(720.0, 0, false)
        val format = com.drivemate.app.nav.Format(false)
        val choices = com.drivemate.app.controller.RouteChoices
        assertSame(simple, choices.order(listOf(fast, simple), com.drivemate.app.data.Settings(preferSimple = true), format).routes.first())
        assertSame(fast, choices.order(listOf(fast, simple.copy(duration = 721.0)), com.drivemate.app.data.Settings(preferSimple = true), format).routes.first())
        val mainRoad = simple.copy(duration = 780.0)
        assertSame(mainRoad, choices.order(listOf(fast, mainRoad), com.drivemate.app.data.Settings(avoidRatRuns = true), format).routes.first())
        assertSame(fast, choices.order(listOf(fast, mainRoad.copy(duration = 781.0)), com.drivemate.app.data.Settings(avoidRatRuns = true), format).routes.first())
    }

    @Test fun explicitPreferencesOverrideAnUnsuitableLearnedFirstRoute() {
        val learned = preferenceRoute(650.0, 5, true)
        val fastMainRoad = preferenceRoute(600.0, 0, false)
        val format = com.drivemate.app.nav.Format(false)
        val choices = com.drivemate.app.controller.RouteChoices
        assertSame(learned, choices.order(listOf(learned, fastMainRoad), com.drivemate.app.data.Settings(), format).routes.first())
        for (settings in listOf(com.drivemate.app.data.Settings(preferSimple = true), com.drivemate.app.data.Settings(avoidRatRuns = true)))
            assertSame(fastMainRoad, choices.order(listOf(learned, fastMainRoad), settings, format).routes.first())
    }

    @Test fun preferencesDoNotInventComparisonsBetweenLiveAndUntimedRoutes() {
        val live = preferenceRoute(700.0, 5, true).copy(live = true,
            trafficCheckedAt = System.currentTimeMillis(), comparisonReliable = true)
        val untimed = preferenceRoute(600.0, 0, false)
        assertSame(live, com.drivemate.app.controller.RouteChoices.order(listOf(live, untimed),
            com.drivemate.app.data.Settings(preferSimple = true, avoidRatRuns = true),
            com.drivemate.app.nav.Format(false)).routes.first())
    }

    private class PreferenceRoutingProvider(val answer: com.drivemate.app.data.Router.Result) : com.drivemate.app.controller.RoutingProvider {
        var alternatives = false
        var stops: List<LatLon> = emptyList()
        var avoid: Set<String> = emptySet()
        var failure: RuntimeException? = null
        override fun route(from: LatLon, to: LatLon, heading: Float?, alternatives: Boolean,
                           stops: List<LatLon>, avoid: Set<String>): com.drivemate.app.data.Router.Result {
            this.alternatives = alternatives; this.stops = stops; this.avoid = avoid
            failure?.let { throw it }
            return answer
        }
        override fun options(from: LatLon, to: LatLon, avoid: Set<String>, heading: Float?) =
            throw AssertionError("Rerouting must preserve intermediate stops")
    }

    @Test fun rerouteAppliesPreferencesWithoutDroppingStopsOrAvoidances() {
        val fast = preferenceRoute(600.0, 5, true)
        val simple = preferenceRoute(660.0, 0, false)
        val provider = PreferenceRoutingProvider(com.drivemate.app.data.Router.Result(listOf(fast, simple)))
        val controller = com.drivemate.app.controller.RoutePlanningController(provider)
        val stops = listOf(LatLon(53.49, -2.23), LatLon(53.495, -2.22))
        val settings = com.drivemate.app.data.Settings(preferSimple = true, avoidTolls = true, avoidFerries = true)
        val answer = controller.preferredRoute(fast.points.first(), fast.points.last(), null, stops, settings, com.drivemate.app.nav.Format(false))
        assertSame(simple, answer.routes.first())
        assertTrue(provider.alternatives)
        assertEquals(stops, provider.stops)
        assertEquals(setOf("toll", "ferry"), provider.avoid)
        controller.preferredRoute(fast.points.first(), fast.points.last(), null, stops,
            settings.copy(preferSimple = false), com.drivemate.app.nav.Format(false))
        assertFalse(provider.alternatives)
    }

    @Test fun cancelledPreferenceRerouteCannotReturnAChoice() {
        val fast = preferenceRoute(600.0, 5, true)
        val provider = PreferenceRoutingProvider(com.drivemate.app.data.Router.Result(listOf(fast)))
        provider.failure = java.util.concurrent.CancellationException("Synthetic cancelled request")
        val controller = com.drivemate.app.controller.RoutePlanningController(provider)
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            controller.preferredRoute(fast.points.first(), fast.points.last(), null, emptyList(),
                com.drivemate.app.data.Settings(preferSimple = true), com.drivemate.app.nav.Format(false))
        }
    }

}
