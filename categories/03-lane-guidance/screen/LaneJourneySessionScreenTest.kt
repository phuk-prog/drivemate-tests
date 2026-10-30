package com.drivemate.tests.cat03

import android.app.Application
import android.location.Location
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.Lane
import com.drivemate.app.data.Route
import com.drivemate.app.data.RouteStep
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LanePolicy
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.NavigationEngine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the production NavigationSession + navigation engine with a complete controlled
 * journey, simulated GPS fixes and route replacement. No mock lane-policy function replaces
 * the live session; the app's actual nav output is read.
 *
 * Route geometry and lane arrows are controlled fixtures, not verified UK road truth.
 * Physical drives and provider lane-continuity evidence remain separate acceptance gaps.
 */
@RunWith(AndroidJUnit4::class)
class LaneJourneySessionScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun call(session: NavigationSession, name: String, vararg args: Any?): Any? =
        NavigationSession::class.java.declaredMethods.single {
            it.name == name && it.parameterCount == args.size
        }.apply { isAccessible = true }.invoke(session, *args)

    private fun field(session: NavigationSession, name: String): Any? =
        NavigationSession::class.java.getDeclaredField(name).apply { isAccessible = true }.get(session)

    private val laneA = listOf(
        Lane(listOf("left", "straight"), true, preferred = false),
        Lane(listOf("straight"), true, preferred = false),
    )
    private val laneB = listOf(
        Lane(listOf("straight"), true, preferred = false),
        Lane(listOf("right"), false, preferred = false),
    )

    private data class Drive(val route: Route, val start: LatLon, val a: LatLon, val b: LatLon, val end: LatLon)

    private fun drive(name: String, first: List<Lane> = laneA, second: List<Lane> = laneB): Drive {
        val start = LatLon(53.40400, -2.17200)
        val a = Geo.offset(start, 0.0, 400.0)
        val b = Geo.offset(a, 90.0, 500.0)
        val end = Geo.offset(b, 0.0, 400.0)
        val route = Route(
            listOf(start, a, b, end), 1300.0, 180.0,
            listOf(
                RouteStep("Set off", "depart", "straight", "King Street", 400.0, start, leftDriving = true),
                RouteStep("Turn right", "turn", "right", "Queen Street", 500.0, a,
                    leftDriving = true, lanes = first),
                RouteStep("Turn left", "turn", "left", "Dale Street", 400.0, b,
                    leftDriving = true, lanes = second),
                RouteStep("Arrive", "arrive", "", "", 0.0, end, leftDriving = true),
            ), name
        )
        return Drive(route, start, a, b, end)
    }

    private fun fix(at: LatLon): Location = Location("gps").apply {
        latitude = at.lat; longitude = at.lon
        accuracy = 5f
        speed = 0f // prevent lead extrapolation: simulate a stationary replay at each snapshot
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    private fun assertStrip(session: NavigationSession, at: LatLon, expected: List<Int>, where: String) {
        call(session, "updateNavigationInternal", fix(at))
        val guidance = session.nav
        assertNotNull("NavigationSession must publish guidance at $where", guidance)
        assertEquals(where, expected, LanePolicy.usable(guidance!!.lanes))
        if (expected.isNotEmpty()) assertEquals("The full session must report lanes actually visible at $where",
            com.drivemate.app.nav.LanePresentationStatus.VISIBLE, session.lanePresentationStatus)
        assertTrue("No preference without verified next-junction links at $where",
            LanePolicy.preferred(guidance.lanes).isEmpty())
    }

    @Test fun q3_3_6_7_27_completeJourneyAdvancesAndClearsLaneStrip() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        lateinit var session: NavigationSession
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        try {
            compose.runOnIdle {
                val trip = drive("Category 3 replay")
                // Same production state path as a newly accepted route. It initializes the engine,
                // resets previous lane display state and schedules ordinary optional road data.
                call(session, "setRouteInternal", trip.route)
                val engine = field(session, "engine") as NavigationEngine
                engine.speedLimits = List(trip.route.points.size - 1) { 30.0 * 1.609344 }
                call(session, "setPositionReliable", true)

                // The first decision is within its 30-mph lane-onset window.
                assertStrip(session, Geo.offset(trip.start, 0.0, 290.0), listOf(0, 1), "approach first turn")
                // Once the first decision is behind, stale lane information must not linger.
                assertStrip(session, Geo.offset(trip.a, 90.0, 40.0), listOf(0, 1), "first crossing provisional")
                assertStrip(session, Geo.offset(trip.a, 90.0, 44.0), emptyList(), "between junctions confirmed")
                // The second turn has a different set of lane restrictions and markings.
                assertStrip(session, Geo.offset(trip.a, 90.0, 405.0), listOf(0), "approach second turn")
                // Passed second decision, before arrival: no old advice remains.
                assertStrip(session, Geo.offset(trip.b, 0.0, 100.0), listOf(0), "second crossing provisional")
                assertStrip(session, Geo.offset(trip.b, 0.0, 104.0), emptyList(), "after confirmed second turn")
            }
        } finally { compose.runOnIdle { session.close() } }
    }

    @Test fun q3_7_27_routeReplacementDoesNotReusePriorDecisionLanes() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        lateinit var session: NavigationSession
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        try {
            compose.runOnIdle {
                val first = drive("Old route")
                call(session, "setRouteInternal", first.route)
                (field(session, "engine") as NavigationEngine).speedLimits =
                    List(first.route.points.size - 1) { 30.0 * 1.609344 }
                call(session, "setPositionReliable", true)
                assertStrip(session, Geo.offset(first.start, 0.0, 290.0), listOf(0, 1), "old route approach")
                val rerouted = drive("New route", first = laneB, second = laneA)
                call(session, "setRouteInternal", rerouted.route)
                (field(session, "engine") as NavigationEngine).speedLimits =
                    List(rerouted.route.points.size - 1) { 30.0 * 1.609344 }
                call(session, "setPositionReliable", true)
                assertStrip(session, Geo.offset(rerouted.start, 0.0, 290.0), listOf(0), "new route approach")
                // A third approach farther from the new decision must not use the old latch.
                val third = drive("Farther changed route", first = laneA)
                call(session, "setRouteInternal", third.route)
                (field(session, "engine") as NavigationEngine).speedLimits =
                    List(third.route.points.size - 1) { 30.0 * 1.609344 }
                call(session, "setPositionReliable", true)
                assertStrip(session, third.start, emptyList(), "outside new route onset")
                assertStrip(session, Geo.offset(third.start, 0.0, 290.0), listOf(0, 1),
                    "new-route eligible lane approach before location loss")
                // Exercise the REAL reliability update, not Kotlin's reflective
                // setPositionReliable property setter which does not run the UI invalidation.
                // A NavigationSession without a trusted tracker fix must clear live lane claims.
                call(session, "updatePositionReliability")
                assertFalse("Untrusted tracker status must revoke lane reliability", session.positionReliable)
                assertEquals("Loss of trusted measured position must invalidate visible lane status",
                    com.drivemate.app.nav.LanePresentationStatus.POSITION_UNCERTAIN,
                    session.lanePresentationStatus)
                assertTrue("Any previously visible strip must be removed after GPS becomes unreliable",
                    session.nav?.lanes.orEmpty().isEmpty())
            }
        } finally { compose.runOnIdle { session.close() } }
    }
}
