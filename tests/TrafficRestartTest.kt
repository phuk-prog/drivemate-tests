package com.drivemate.app

import android.app.Application
import android.content.Context
import com.drivemate.app.controller.NavigationSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Restart persistence for the traffic setting (subcategory 1.3): a change is still there after the
 * navigation session is closed and created again, and an unstored value falls back to the one default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TrafficRestartTest {
    private fun app() = RuntimeEnvironment.getApplication() as Application

    @Test fun trafficChangeSurvivesRestart() {
        val app = app()
        assertTrue(app.getSharedPreferences("drivemate", Context.MODE_PRIVATE).edit().clear().commit())
        NavigationSession(app).use { first ->
            assertEquals(true, first.showTraffic)
            first.toggleTraffic()
            assertEquals(false, first.showTraffic)
        }
        NavigationSession(app).use { second ->
            assertEquals("Traffic must be off after restart", false, second.showTraffic)
        }
    }

    @Test fun unstoredTrafficUsesTheDefault() {
        val app = app()
        assertTrue(app.getSharedPreferences("drivemate", Context.MODE_PRIVATE).edit().clear().commit())
        NavigationSession(app).use { session ->
            assertEquals(true, session.showTraffic)
        }
    }
}
