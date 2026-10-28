package com.drivemate.tests.cat03

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.data.Settings
import com.drivemate.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cat 3 J27 readiness PRETEST on the actual Android settings repository.
 *
 * The app's NavigationSession currently constructs a PersonalDataController, which
 * constructs a SettingsRepository via PreferenceDataStoreFactory.create. A screen
 * replacement can briefly overlap sessions. This test proves or rejects a
 * competing DataStore-instance hypothesis without setting settingsReady=true,
 * mocking the settings repository, or weakening the existing lane journey test.
 *
 * It checks ONLY repository readiness. It says nothing about actual lane strips.
 */
@RunWith(AndroidJUnit4::class)
class LaneSettingsReadinessScreenTest {
    @Test fun q3_overlappingSessionsCanReadTheSameSettingsWithoutDataStoreConflict() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val fallback = Settings.load(app.getSharedPreferences("drivemate", Context.MODE_PRIVATE))
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // These two concrete repositories share DriveMate's on-device settings
            // file as overlapping app sessions do; no fake DataStore is used.
            val first = SettingsRepository(app, firstScope)
            val second = SettingsRepository(app, secondScope)
            val both = withTimeout(15_000) {
                coroutineScope {
                    val a = async(Dispatchers.IO) { first.load(fallback) }
                    val b = async(Dispatchers.IO) { second.load(fallback) }
                    a.await() to b.await()
                }
            }
            assertEquals("Both overlapping sessions must observe identical settings", both.first, both.second)
        } finally {
            firstScope.cancel()
            secondScope.cancel()
        }
    }
}
