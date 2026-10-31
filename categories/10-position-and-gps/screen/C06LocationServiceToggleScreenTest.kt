package com.drivemate.tests.cat10

import android.Manifest
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.nav.LocationTracker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * C06.3/C06.4 on the emulator: the phone's location service is switched off during navigation and
 * back on, with the real LocationTracker. Guidance must stop trusting position and tell the driver,
 * then recover only through fresh state once location returns. Permission revocation is not run
 * here: Android kills the app process on revoke (real-phone revoke is in Physical drive checks).
 */
@RunWith(AndroidJUnit4::class)
class C06LocationServiceToggleScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var tracker: LocationTracker

    private fun shell(command: String) {
        val fd = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { val b = ByteArray(512); while (it.read(b) > 0) {} }
    }

    private fun locationEnabled(on: Boolean) {
        shell("cmd location set-location-enabled $on")
        val deadline = System.currentTimeMillis() + 10_000
        val manager = context.getSystemService(android.location.LocationManager::class.java)
        while (manager.isLocationEnabled != on && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals("emulator location service switched", on, manager.isLocationEnabled)
    }

    private fun onMain(block: () -> Unit) {
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { try { block() } finally { done.countDown() } }
        assertTrue(done.await(10, TimeUnit.SECONDS))
    }

    @Before fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        locationEnabled(true)
        onMain { tracker = LocationTracker(context, onLocation = {}) }
    }

    @After fun tearDown() {
        onMain { tracker.stop() }
        locationEnabled(true)
    }

    @Test fun locationServiceOffDuringNavigationIsReportedAndNeverTrustedThenRecovers() {
        onMain { tracker.start(LocationTracker.Mode.NAVIGATION) }
        onMain { tracker.refreshAvailability() }
        assertNotEquals(LocationTracker.Status.LOCATION_DISABLED, tracker.state.value.status)
        assertTrue(tracker.state.value.precisePermission)

        locationEnabled(false)
        onMain { tracker.refreshAvailability() }
        val off = tracker.state.value
        assertEquals(LocationTracker.Status.LOCATION_DISABLED, off.status)
        assertFalse("services reported off", off.servicesEnabled)
        assertFalse("no position may be treated as authoritative", off.authoritativeFix)
        assertFalse("no lane-level guidance from a switched-off location service", off.guidanceReliable)
        assertEquals("Turn on your phone’s location service", off.message)

        locationEnabled(true)
        onMain { tracker.refreshAvailability() }
        val on = tracker.state.value
        assertNotEquals(LocationTracker.Status.LOCATION_DISABLED, on.status)
        assertTrue(on.servicesEnabled)
        // Recovery needs a fresh fix: switching back on alone does not make an old position trusted.
        if (!on.authoritativeFix) assertFalse(on.guidanceReliable)
    }

    @Test fun startingNavigationWithLocationOffDoesNotRunTheTracker() {
        locationEnabled(false)
        onMain { tracker.start(LocationTracker.Mode.NAVIGATION) }
        onMain { tracker.refreshAvailability() }
        val s = tracker.state.value
        assertEquals(LocationTracker.Status.LOCATION_DISABLED, s.status)
        assertFalse(s.authoritativeFix)
        assertFalse(s.guidanceReliable)
    }
}
