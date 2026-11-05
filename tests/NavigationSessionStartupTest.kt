package com.drivemate.app

import android.app.Application
import com.drivemate.app.controller.NavigationSession
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Creates the navigation session the way the app does at startup, on Android 9 (API 28).
 * The app's minimum is API 26, so this must construct on API 28 without a missing platform class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class NavigationSessionStartupTest {
    @Test fun navigationSessionConstructsOnApi28() {
        val app = RuntimeEnvironment.getApplication() as Application
        NavigationSession(app).use { session ->
            assertNotNull(session)
        }
    }
}
