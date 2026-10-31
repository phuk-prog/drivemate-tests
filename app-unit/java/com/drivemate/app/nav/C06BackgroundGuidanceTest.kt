package com.drivemate.app.nav

import com.drivemate.app.nav.BackgroundGuidance.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C06.3: the driver is told when Android will not keep guidance running in the background. */
class C06BackgroundGuidanceTest {
    private fun s(navigating: Boolean = true, failed: Boolean = false, active: Boolean = true,
                  notifications: Boolean = true, seconds: Double = 10.0) =
        BackgroundGuidance.status(navigating, failed, active, notifications, seconds)

    @Test fun runningForegroundServiceWithNotificationsIsReady() {
        assertEquals(Status.READY, s())
        assertNull(BackgroundGuidance.message(Status.READY))
    }

    @Test fun refusedStartIsBlockedImmediatelyAndTheDriverIsTold() {
        assertEquals(Status.BLOCKED, s(failed = true, active = false, seconds = 0.0))
        assertTrue(BackgroundGuidance.message(Status.BLOCKED)!!.contains("Keep DriveMate open"))
    }

    @Test fun serviceGetsFiveSecondsToStartThenCountsAsBlocked() {
        assertEquals(Status.STARTING, s(active = false, seconds = 4.9))
        assertNull("no false alarm while Android is still starting it", BackgroundGuidance.message(Status.STARTING))
        assertEquals(Status.BLOCKED, s(active = false, seconds = 5.0))
    }

    @Test fun notificationsOffStillGuidesButSaysTheLockScreenWontShowTheTurn() {
        assertEquals(Status.NO_NOTIFICATIONS, s(notifications = false))
        assertTrue(BackgroundGuidance.message(Status.NO_NOTIFICATIONS)!!.contains("notifications"))
    }

    @Test fun refusalOutranksMissingNotifications() {
        assertEquals(Status.BLOCKED, s(failed = true, notifications = false))
    }

    @Test fun notNavigatingIsStoppedWhateverTheServiceState() {
        for (failed in listOf(true, false)) for (active in listOf(true, false))
            assertEquals(Status.STOPPED, s(navigating = false, failed = failed, active = active))
        assertNull(BackgroundGuidance.message(Status.STOPPED))
    }
}
