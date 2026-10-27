package com.drivemate.tests.cat03

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.NavUi
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.Lane
import com.drivemate.app.data.Route
import com.drivemate.app.data.RouteStep
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.GuidanceFallback
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.NavigationEngine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production lane-to-speech selection in NavigationSession, including one-time delivery,
 * a new decision, missing data and a replaced route. This checks the utterance handed to
 * Voice, NOT a recording from real car speakers or Bluetooth volume measurements.
 */
@RunWith(AndroidJUnit4::class)
class LaneSpeechSessionScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun call(session: NavigationSession, name: String, value: Any?): Any? =
        NavigationSession::class.java.declaredMethods.single { it.name == name && it.parameterCount == 1 }
            .apply { isAccessible = true }.invoke(session, value)
    private fun spoken(session: NavigationSession): String =
        NavigationSession::class.java.declaredMethods.single { it.name == "pendingLaneWords" && it.parameterCount == 0 }
            .apply { isAccessible = true }.invoke(session) as String
    private fun set(session: NavigationSession, name: String, value: Any?) {
        NavigationSession::class.java.getDeclaredField(name).apply { isAccessible = true }.set(session, value)
    }
    private fun engine(): NavigationEngine {
        val start = LatLon(53.4040, -2.1720)
        val turn = Geo.offset(start, 0.0, 400.0)
        val end = Geo.offset(turn, 90.0, 350.0)
        val route = Route(
            listOf(start, turn, end), 750.0, 90.0,
            listOf(
                RouteStep("Set off", "depart", "straight", "King Street", 400.0, start),
                RouteStep("Turn right", "turn", "right", "Queen Street", 350.0, turn),
                RouteStep("Arrive", "arrive", "", "", 0.0, end),
            ), "Category 3 spoken test"
        )
        return NavigationEngine(route)
    }
    private fun publish(session: NavigationSession, lanes: List<Lane>, key: String) {
        set(session, "visibleLaneDecisionKey", key)
        call(session, "setNav", NavUi(
            step = null, instruction = "Turn right", distanceToNext = 100.0,
            then = null, remaining = 300.0, remainingSeconds = 30.0, lanes = lanes
        ))
    }

    @Test fun q3_3_7_14_laneSpeechIsOncePerShownDecisionAndResetsOnlyForNewDecisionOrRoute() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        lateinit var session: NavigationSession
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        try {
            compose.runOnIdle {
                set(session, "engine", engine())
                call(session, "setPositionReliable", true)
                (NavigationSession::class.java.getDeclaredField("guidanceFallback")
                    .apply { isAccessible = true }.get(session) as GuidanceFallback).reset()
                val left = listOf(
                    Lane(listOf("left"), true, preferred = false),
                    Lane(listOf("straight"), false, preferred = false),
                )
                val right = listOf(
                    Lane(listOf("left"), false, preferred = false),
                    Lane(listOf("right"), true, preferred = false),
                )

                publish(session, left, "turn-1")
                assertEquals("Use the left lane", spoken(session))
                assertEquals("No duplicate on repeated GPS fixes", "", spoken(session))
                publish(session, emptyList(), "turn-2")
                assertEquals("Missing strip must not produce invented lane speech", "", spoken(session))
                publish(session, right, "turn-2")
                assertEquals("Use the right lane", spoken(session))
                assertEquals("", spoken(session))

                publish(session, left, "turn-3")
                call(session, "setPositionReliable", false)
                assertEquals("Unreliable location suppresses lane speech", "", spoken(session))
                call(session, "setPositionReliable", true)
                assertEquals("Use the left lane", spoken(session))

                // An entirely new route may reuse the same manoeuvre number.
                set(session, "engine", engine())
                publish(session, left, "turn-1")
                assertEquals("Use the left lane", spoken(session))
                assertEquals("", spoken(session))
            }
        } finally { compose.runOnIdle { session.close() } }
    }
}
