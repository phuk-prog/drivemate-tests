package com.drivemate.app

import com.drivemate.app.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * Launches the main activity the way the app does at startup on Android 9 (API 28), through
 * onCreate, the map view and controller, and the first Compose frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = DriveMateApp::class, shadows = [ShadowNativeConnectivityListener::class, ShadowTileServerOptions::class, ShadowFileSource::class])
class MainActivityStartupTest {
    @Test fun mainActivityStartsThroughFirstFrameOnApi28() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }
}
