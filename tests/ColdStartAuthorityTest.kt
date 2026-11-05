package com.drivemate.app

import android.app.Application
import android.content.Context
import com.drivemate.app.controller.PersonalDataController
import com.drivemate.app.data.Settings
import com.drivemate.app.data.SettingsStore
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Restart persistence, cold start (subcategory 1.3.1): the preference mirror and the authoritative store
 * disagree. While the authority is still loading, the controller exposes the mirror placeholder; once the
 * authority has loaded, the controller must expose the authoritative value.
 *
 * Characterization test: it locks the current load contract. It does not reproduce the retention
 * ordering hypothesis (H1), which needs NavigationSession-level seeding or an app-side loaded signal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ColdStartAuthorityTest {
    @Test fun authorityWinsOverConflictingMirrorAfterLoad() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("drivemate", Context.MODE_PRIVATE)
        assertEquals(true, prefs.edit().clear().putInt("keepDays", 7).commit())
        val release = CompletableDeferred<Unit>()
        val authority = object : SettingsStore {
            override suspend fun load(fallback: Settings): Settings {
                release.await()
                return Settings(keepDays = 0)
            }
            override suspend fun save(settings: Settings) {}
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = PersonalDataController(app, scope, { }, authority)
        try {
            // Authority still pending: the exposed value is the mirror placeholder.
            assertEquals(7, controller.currentSettings().keepDays)
            release.complete(Unit)
            withTimeout(15_000) { controller.loadSettings(Settings()) }
            // Authority loaded: the exposed value is the authoritative one.
            assertEquals(0, controller.currentSettings().keepDays)
        } finally {
            scope.cancel()
            withTimeout(15_000) { scope.coroutineContext[Job]!!.join() }
        }
    }
}
