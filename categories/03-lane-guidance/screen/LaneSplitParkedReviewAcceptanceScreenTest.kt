package com.drivemate.tests.cat03

import android.app.Application
import android.location.Location
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.Lane
import com.drivemate.app.data.LaneCorrections
import com.drivemate.app.data.LaneReports
import com.drivemate.app.data.LaneSpot
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
 * Actual Android navigation-session report / review lifecycle with controlled UK-area
 * geometry. It does not assert that the synthetic approach reflects real UK lane markings.
 * All corrections remain on the phone; no map publication is permitted.
 */
@RunWith(AndroidJUnit4::class)
class LaneSplitParkedReviewAcceptanceScreenTest {
    @get:Rule val compose = createComposeRule()

    private val splitLanes = listOf(
        Lane(listOf("left"), false, preferred = false),
        Lane(listOf("straight"), true, preferred = false)
    )

    private fun call(session: NavigationSession, name: String, vararg args: Any?): Any? =
        NavigationSession::class.java.declaredMethods.single {
            it.name == name && it.parameterCount == args.size
        }.apply { isAccessible = true }.invoke(session, *args)

    private fun resetStorage(app: Application) {
        app.getSharedPreferences("drivemate", android.content.Context.MODE_PRIVATE)
            .edit().remove("laneReviewReports").remove("laneCorrections")
            .remove("laneCorrectionConflicts").apply()
    }

    private fun position(at: LatLon, speedMps: Float, ageMs: Long = 0): Location =
        Location("gps").apply {
            latitude = at.lat
            longitude = at.lon
            speed = speedMps
            accuracy = 5f
            time = System.currentTimeMillis() - ageMs
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - ageMs * 1_000_000L
        }

    private fun syntheticRoute(): Pair<Route, LatLon> {
        val start = LatLon(53.4040, -2.1720)
        val split = Geo.offset(start, 0.0, 300.0)
        val turn = Geo.offset(start, 0.0, 600.0)
        val end = Geo.offset(turn, 90.0, 300.0)
        val route = Route(
            listOf(start, split, turn, end), 900.0, 130.0,
            listOf(
                RouteStep("Depart", "depart", "straight", "Approach Road", 600.0,
                    start, leftDriving = true,
                    laneSpots = listOf(LaneSpot(split, splitLanes))),
                RouteStep("Turn right", "turn", "right", "Following Road", 300.0,
                    turn, leftDriving = true, lanes = listOf(
                        Lane(listOf("right"), true), Lane(listOf("straight"), false))),
                RouteStep("Arrive", "arrive", "", "", 0.0, end, leftDriving = true)
            ), "Controlled earlier-split lane report"
        )
        return route to Geo.offset(start, 0.0, 160.0)
    }

    @Test fun q3_16_20_reportUsesDisplayedEarlierSplitAndRetainsItsLaneLayout() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        resetStorage(app)
        lateinit var session: NavigationSession
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        try {
            compose.runOnIdle {
                val (route, at) = syntheticRoute()
                call(session, "setRouteInternal", route)
                val eng = NavigationSession::class.java.getDeclaredField("engine")
                    .apply { isAccessible = true }.get(session) as NavigationEngine
                eng.speedLimits = List(route.points.size - 1) { 30.0 * 1.609344 }
                call(session, "setPositionReliable", true)
                val gps = position(at, 0f)
                call(session, "setLocation", gps)
                call(session, "updateNavigationInternal", gps)
                assertEquals("Displayed decision must be the earlier split, not the eventual turn",
                    splitLanes.size, session.nav?.lanes?.size)

                session.flagLaneCorrectionForReview()
                val reports = session.pendingLaneReports()
                assertEquals(1, reports.size)
                val report = reports.single()
                assertEquals("Earlier split must be separately identifiable",
                    "lane-spot|straight", report.maneuver)
                assertEquals(0, report.exit)
                assertTrue("Review marker belongs to the earlier split, not the turn",
                    Geo.distance(report.at, route.points[1]) < 3)
                assertEquals("Preserve observed lane layout for after-drive review",
                    splitLanes.map { it.indications }, report.lanes.map { it.indications })
                assertEquals(splitLanes.map { it.valid }, report.lanes.map { it.valid })
                assertEquals(LanePolicy.signature(splitLanes), report.sourceSignature)
                assertTrue("A voice report never silently approves lanes", session.laneCorrections().isEmpty())
                session.flagLaneCorrectionForReview()
                assertEquals("Same split report should not multiply on repeated commands",
                    1, session.pendingLaneReports().size)
            }
        } finally { compose.runOnIdle { session.close() } }
    }

    @Test fun q3_16_17_20_parkedApprovalMustUseExistingPrivateReportAndFreshStoppedPosition() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        resetStorage(app)
        lateinit var session: NavigationSession
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        try {
            compose.runOnIdle {
                val at = LatLon(53.4040, -2.1720)
                val lanes = listOf(listOf("left"), listOf("straight"))
                val source = LanePolicy.signature(splitLanes)
                val report = LaneReports.Report("cat03-split-review", at, 90.0, 90.0,
                    "lane-spot|straight", 0, source, splitLanes, missing = false)
                val store = LaneReports(app.getSharedPreferences("drivemate", 0))
                store.add(report)
                val usable = setOf(1)

                call(session, "setLocation", position(at, 12f))
                assertFalse(session.canEditLaneGuidance())
                session.approveLaneReport(report, lanes, usable, null)
                assertTrue(session.laneCorrections().isEmpty())
                assertEquals(1, session.pendingLaneReports().size)

                call(session, "setLocation", position(at, 0f, ageMs = 8_000))
                assertFalse(session.canEditLaneGuidance())
                session.approveLaneReport(report, lanes, usable, null)
                assertTrue(session.laneCorrections().isEmpty())

                call(session, "setLocation", position(at, 0f))
                assertTrue(session.canEditLaneGuidance())
                session.approveLaneReport(report, lanes, usable, null)
                assertEquals(1, session.laneCorrections().size)
                assertTrue(session.pendingLaneReports().isEmpty())
                val saved = session.laneCorrections().single()
                assertEquals(report.maneuver, saved.maneuver)
                assertEquals(report.sourceSignature, saved.sourceSignature)
                assertEquals(report.heading, saved.heading, 0.001)
                assertNotNull(LaneCorrections(app.getSharedPreferences("drivemate", 0))
                    .find(at, 90.0, "lane-spot|straight", 0, 90.0, source))
                assertNull("The correction must not override a different direction",
                    LaneCorrections(app.getSharedPreferences("drivemate", 0))
                        .find(at, 270.0, "lane-spot|straight", 0, 90.0, source))

                session.deleteLaneCorrection(saved.id)
                assertTrue(session.laneCorrections().isEmpty())
                // A stale dialog or deleted report object must not re-approve deleted guidance.
                session.approveLaneReport(report, lanes, usable, null)
                assertTrue("Deleted review must not be revived by an old approval callback",
                    session.laneCorrections().isEmpty())
                assertTrue(session.pendingLaneReports().isEmpty())
            }
        } finally { compose.runOnIdle { session.close() } }
    }
}
