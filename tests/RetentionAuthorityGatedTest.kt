package com.drivemate.app

import android.app.Application
import android.content.Context
import android.os.Looper
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.Settings
import com.drivemate.app.data.SettingsStore
import com.drivemate.app.nav.TripHistory
import com.drivemate.app.nav.TripSummary
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Subcategory 1.3.1 reproduction with controlled load timing. The authoritative store is held until the
 * test releases it, so startup runs while only the preference mirror is available. Startup history
 * cleanup must still follow the authoritative keep-days value, in both conflict directions.
 *
 * Needs the optional SettingsStore parameter on NavigationSession (app candidate be573fe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class RetentionAuthorityGatedTest {
    private fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idle()

    /** Returns whether a 30-day-old trip survives startup once the authority has loaded. */
    private fun tripSurvives(mirrorKeepDays: Int, authorityKeepDays: Int): Boolean {
        val app = RuntimeEnvironment.getApplication() as Application
        val prefs = app.getSharedPreferences("drivemate", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().putInt("keepDays", mirrorKeepDays).commit())
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 86_400_000L
        assertTrue(TripHistory(prefs).add(
            TripSummary("Synthetic old trip", thirtyDaysAgo, 600, 4_000.0, 0, emptyList())))
        val release = CompletableDeferred<Unit>()
        val authority = object : SettingsStore {
            override suspend fun load(fallback: Settings): Settings {
                release.await()
                return Settings(keepDays = authorityKeepDays)
            }
            override suspend fun save(settings: Settings) {}
        }
        NavigationSession(app, authority).use { session ->
            try {
                // Startup has run here; the authoritative load is still held.
                idle()
                release.complete(Unit)
                val deadline = System.nanoTime() + 15_000_000_000L
                while (!session.settingsReady && System.nanoTime() < deadline) {
                    idle()
                    Thread.sleep(10)
                }
                assertTrue("Saved settings must load", session.settingsReady)
                return session.pastTrips().any { it.destination == "Synthetic old trip" }
            } finally {
                release.complete(Unit)
            }
        }
    }

    /** Stale mirror would delete history the authority keeps forever. */
    @Test fun authorityKeepForeverSurvivesStaleSevenDayMirror() {
        assertTrue(
            "Authoritative keep-forever must keep a 30-day-old trip while the mirror says 7 days",
            tripSurvives(mirrorKeepDays = 7, authorityKeepDays = 0))
    }

    /** Stale mirror would skip cleanup the authority asks for. */
    @Test fun authoritySevenDayRetentionRemovesTripAgainstKeepForeverMirror() {
        assertFalse(
            "Authoritative 7-day retention must remove a 30-day-old trip while the mirror says keep forever",
            tripSurvives(mirrorKeepDays = 0, authorityKeepDays = 7))
    }
}
