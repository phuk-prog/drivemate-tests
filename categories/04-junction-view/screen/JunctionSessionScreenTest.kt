package com.drivemate.tests.cat04

import android.app.Application
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.*
import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Integration of the real session, planner and route engine with synthetic route/fix data.
 * Reflection stays here: no test hooks or synthetic navigation modes are shipped in the app. */
@RunWith(AndroidJUnit4::class)
class JunctionSessionScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun call(session: NavigationSession, name: String, vararg args: Any?): Any? =
        NavigationSession::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(session, *args)
    private fun engine(session: NavigationSession, engine: NavigationEngine) {
        NavigationSession::class.java.getDeclaredField("engine").apply { isAccessible = true }.set(session, engine)
    }
    @Test fun q4_1_2_9_10_11_sessionDismissRerouteDisableAndArrival() {
        lateinit var session: NavigationSession
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        try {
            compose.runOnIdle {
                val origin = LatLon(53.0, -2.0)
                val junction = Geo.offset(origin, 0.0, 500.0)
                val end = Geo.offset(junction, 315.0, 400.0)
                val lanes = listOf(Lane(listOf("slight left"), true), Lane(listOf("straight"), false))
                val step = RouteStep("Take the exit", "off ramp", "slight left", "A6", 400.0, junction,
                    leftDriving = true, lanes = lanes, bearingBefore = 0, bearingAfter = 315)
                val route = Route(listOf(origin, junction, end), 900.0, 60.0,
                    listOf(RouteStep("Continue", "depart", "straight", "M60", 500.0, origin, leftDriving = true),
                        step, RouteStep("Arrive", "arrive", "", "", 0.0, end)), "Synthetic category 4")
                val first = NavigationEngine(route)
                val progress = first.update(Geo.offset(origin, 0.0, 250.0), 0.0, 20.0, 3.0)
                assertEquals(1, progress.nextStep)
                engine(session, first)
                fun plan(e: NavigationEngine) = call(session, "junctionPlanFor", e, progress, step, lanes, false, "slight left", true, false) as JunctionPlan?
                fun update(e: NavigationEngine, p: JunctionPlan?) = call(session, "updateImmersive", e, progress, p, true,
                    SharedProgress.of(progress, SystemClock.elapsedRealtime(), 0, 20.0, 3.0), GuidanceLevel.LANE_DETAIL)
                session.updateSettings(session.settings.copy(junctionView = true))
                assertNotNull(plan(first))
                update(first, plan(first))
                assertNotNull(session.immersiveMap)
                assertTrue(session.dismissJunctionView())
                assertNull(session.immersiveMap)
                assertNull(plan(first))
                update(first, null)
                assertNull(session.immersiveMap)
                val reroute = NavigationEngine(route)
                engine(session, reroute)
                assertNotNull(plan(reroute))
                update(reroute, plan(reroute))
                assertNotNull(session.immersiveMap)
                call(session, "updateImmersive", reroute, progress, null, true,
                    SharedProgress.of(progress, SystemClock.elapsedRealtime(), 0, 20.0, 90.0), GuidanceLevel.PLAIN_ARROW)
                assertNotNull("Held for a moment of weak position", session.immersiveMap)
                NavigationSession::class.java.getDeclaredField("plainArrowSince").apply { isAccessible = true }
                    .set(session, SystemClock.elapsedRealtime() - 3000L)
                call(session, "updateImmersive", reroute, progress, null, true,
                    SharedProgress.of(progress, SystemClock.elapsedRealtime(), 0, 20.0, 90.0), GuidanceLevel.PLAIN_ARROW)
                assertNull("No junction view after the 3-second hold", session.immersiveMap)
                update(reroute, plan(reroute))
                assertNotNull(session.immersiveMap)
                session.updateSettings(session.settings.copy(junctionView = false))
                assertNull(session.immersiveMap)
                assertNull(session.immersiveUpcoming)
                update(reroute, plan(reroute))
                assertNull(session.immersiveMap)
                session.updateSettings(session.settings.copy(junctionView = true))
                update(reroute, plan(reroute))
                assertNotNull(session.immersiveMap)
                NavigationSession::class.java.getDeclaredField("progress").apply { isAccessible = true }
                    .set(session, progress.copy(nextStep = 2))
                assertTrue(session.dismissJunctionView())
                val dismissal = NavigationSession::class.java.getDeclaredField("junctionDismissal").apply { isAccessible = true }
                    .get(session) as JunctionDismissal
                assertTrue("Dismiss the current chained step, not its previous exit", dismissal.hidden(reroute, 2))
                assertFalse(dismissal.hidden(reroute, 1))
                NavigationSession::class.java.getDeclaredField("progress").apply { isAccessible = true }.set(session, progress)
                update(reroute, plan(reroute))
                call(session, "setNav", com.drivemate.app.NavUi(step, "Arrived", 0.0, null, 0.0, 0.0, arrived = true))
                update(reroute, plan(reroute))
                assertNull(session.immersiveMap)
            }
        } finally { compose.runOnIdle { session.close() } }
    }
}
