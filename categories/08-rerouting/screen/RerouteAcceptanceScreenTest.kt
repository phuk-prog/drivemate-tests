package com.drivemate.tests.cat08

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.FasterRoute
import com.drivemate.app.Mode
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.Route
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.NavigationEngine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production session must revalidate when Switch is pressed, not just when an offer appeared. */
@RunWith(AndroidJUnit4::class)
class RerouteAcceptanceScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun call(session: NavigationSession, name: String, value: Any?) {
        NavigationSession::class.java.declaredMethods.single { it.name == name && it.parameterCount == 1 }
            .apply { isAccessible = true }.invoke(session, value)
    }
    @Test fun q8_3_4_staleTrafficAndUncertainPositionCannotReplaceCurrentRoute() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        lateinit var session: NavigationSession
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        try {
            compose.runOnIdle {
                val points = listOf(LatLon(53.0, -2.0), LatLon(53.02, -2.0))
                val route = Route(points, 2200.0, 300.0, emptyList(), "Synthetic current")
                val original = NavigationEngine(route)
                val engine = NavigationSession::class.java.getDeclaredField("engine").apply { isAccessible = true }
                engine.set(session, original)
                call(session, "setMode", Mode.NAVIGATING)
                call(session, "setPositionReliable", true)
                val alternative = route.copy(summary = "Synthetic faster", duration = 180.0, live = true,
                    trafficCheckedAt = System.currentTimeMillis() - 6 * 60_000L, comparisonReliable = true)
                fun reject(candidate: Route, reliable: Boolean) {
                    call(session, "setPositionReliable", reliable)
                    call(session, "setFasterRoute", FasterRoute(candidate, 120.0, Long.MAX_VALUE))
                    session.takeFasterRoute()
                    assertNull("Rejected offer is dismissed", session.fasterRoute)
                    assertSame("Current engine survives rejected Switch", original, engine.get(session))
                }
                reject(alternative, true)
                reject(alternative.copy(trafficCheckedAt = System.currentTimeMillis()), false)
                call(session, "setMode", Mode.BROWSE)
                reject(alternative.copy(trafficCheckedAt = System.currentTimeMillis()), true)
            }
        } finally { compose.runOnIdle { session.close() } }
    }
}
