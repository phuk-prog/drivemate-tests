package com.drivemate.tests.cat06

import android.app.Application
import android.location.Location
import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.Mode
import com.drivemate.app.NavUi
import com.drivemate.app.car.CarBridge
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.ui.Speedometer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Draws the production speedometer with synthetic location state, not a replacement UI. */
@RunWith(AndroidJUnit4::class)
class SpeedLimitDisplayScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun call(session: NavigationSession, name: String, value: Any?) {
        NavigationSession::class.java.declaredMethods.single { it.name == name && it.parameterCount == 1 }
            .apply { isAccessible = true }.invoke(session, value)
    }
    private fun fix(provider: String, ageMs: Long) = Location(provider).apply {
        latitude = 53.0; longitude = -2.0; accuracy = 3f; speed = 30f
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - ageMs * 1_000_000
    }
    @Test fun q6_1_2_5_unknownLimitAndUnmeasuredSpeedNeverLookLikeZeroOrSpeeding() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        lateinit var session: NavigationSession
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        val carTime = CarBridge::class.java.getDeclaredField("carSpeedAt").apply { isAccessible = true }
        val previousCarTime = carTime.getLong(CarBridge)
        try {
            compose.runOnIdle {
                carTime.setLong(CarBridge, 0L)
                call(session, "setMode", Mode.NAVIGATING)
                call(session, "setNav", NavUi(null, "Continue", 1000.0, null, 2000.0, 120.0))
                call(session, "setLocation", fix("gps", 6000))
            }
            compose.setContent { Speedometer(session, Modifier, hideWhenStill = false) }
            compose.onNodeWithContentDescription("Speed limit unknown").assertIsDisplayed()
            compose.onNodeWithContentDescription("Your speed unknown ${session.format.speedUnit}").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("over the limit", substring = true).assertCountEquals(0)
            compose.runOnIdle { call(session, "setLocation", fix("estimate", 0)) }
            compose.onNodeWithContentDescription("Your speed unknown ${session.format.speedUnit}").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("over the limit", substring = true).assertCountEquals(0)
            compose.runOnIdle {
                call(session, "setNav", NavUi(null, "Continue", 1000.0, null, 2000.0, 120.0, speedLimit = 48.28032))
                call(session, "setLocation", fix("gps", 0))
            }
            compose.onNodeWithContentDescription("Speed limit ${session.format.limit(48.28032)}").assertIsDisplayed()
            compose.onNodeWithContentDescription("over the limit", substring = true).assertIsDisplayed()
        } finally {
            compose.runOnIdle { session.close(); carTime.setLong(CarBridge, previousCarTime) }
        }
    }
}
