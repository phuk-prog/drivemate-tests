package com.drivemate.tests.cat10

import android.app.Application
import android.location.Location
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.controller.NavigationSession
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the real session ingress: rejected measurements cannot overwrite the last position. */
@RunWith(AndroidJUnit4::class)
class GpsAcceptanceScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun call(session: NavigationSession, name: String, value: Any?) {
        NavigationSession::class.java.declaredMethods.single { it.name == name && it.parameterCount == 1 }
            .apply { isAccessible = true }.invoke(session, value)
    }
    private fun fix() = Location("gps").apply {
        latitude = 53.0; longitude = -2.0; accuracy = 3f; speed = 10f
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(); time = System.currentTimeMillis()
    }
    @Test fun q10_1_2_malformedAndStaleFixesNeverReplaceRetainedSessionPosition() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        lateinit var session: NavigationSession
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        try {
            compose.runOnIdle {
                val retained = fix()
                call(session, "setLocation", retained)
                val rejected = listOf(
                    fix().apply { latitude = Double.NaN },
                    fix().apply { speed = Float.NaN },
                    fix().apply { speed = -1f },
                    fix().apply { speed = 101f },
                    fix().apply { longitude = 181.0 },
                    fix().apply { bearing = Float.NaN },
                    fix().apply { accuracy = Float.POSITIVE_INFINITY },
                    fix().apply { elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - 9_000_000_000L },
                )
                rejected.forEach {
                    call(session, "onLocation", it)
                    assertSame("Invalid fix must not become session position", retained, session.location)
                }
            }
        } finally { compose.runOnIdle { session.close() } }
    }
}
