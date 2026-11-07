package com.drivemate.app

import androidx.datastore.preferences.core.edit
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.drivemate.app.controller.PersonalDataController
import com.drivemate.app.data.Settings
import com.drivemate.app.data.SettingsStore
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Startup with a failing compatibility copy (preference mirror): loading must not throw, must keep the
 * authoritative settings, and must report a problem. Startup previously threw IllegalStateException here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class StartupMirrorFailureTest {
    @Test fun startupMirrorFailureKeepsLoadedSettingsAndReportsProblem() = runBlocking {
        val base = RuntimeEnvironment.getApplication()
        val actual = base.getSharedPreferences("drivemate", Context.MODE_PRIVATE)
        assertTrue(actual.edit().clear().commit())
        val hooked = hookedPreferences(actual) { false }
        val context = contextWithPreferences(base, hooked)
        val loaded = Settings(avoidTolls = true)
        val authority = object : SettingsStore {
            override suspend fun load(fallback: Settings) = loaded
            override suspend fun save(settings: Settings) {}
        }
        val problems = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = PersonalDataController(context, scope, { problems.incrementAndGet() }, authority)
        try {
            withTimeout(15_000) { controller.loadSettings(Settings()) }
            assertEquals(loaded, controller.currentSettings())
            assertTrue("A failing compatibility copy must be reported", problems.get() >= 1)
        } finally {
            scope.cancel()
            withTimeout(15_000) { scope.coroutineContext[Job]!!.join() }
        }
    }
    @Test fun legacyAppearanceBackupRemainsPortableWithoutChangingActiveSettings() {
        val base = RuntimeEnvironment.getApplication() as Application
        val prefs = base.getSharedPreferences("drivemate", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        val values = org.json.JSONObject()
            .put("theme", org.json.JSONObject().put("t", "s").put("v", "BRIGHT"))
            .put("browseBuildings3d", org.json.JSONObject().put("t", "b").put("v", true))
            .put("autoZoom", org.json.JSONObject().put("t", "b").put("v", false))
        val text = org.json.JSONObject().put("format", "DriveMate backup").put("version", 1)
            .put("preferences", values).toString()
        val learned = com.drivemate.app.nav.LearnedSpeeds(base)
        try {
            com.drivemate.app.data.BackupManager.restore(base, learned, text)
            assertEquals("Retired appearance choices must not change active settings",
                Settings(autoZoom = false), Settings.load(prefs))
            assertTrue(Settings(autoZoom = false, avoidTolls = true).save(prefs, synchronous = true))
            val exported = com.drivemate.app.data.BackupSchema.parse(
                com.drivemate.app.data.BackupManager.export(base, learned)).preferences
            assertEquals("BRIGHT", exported["theme"])
            assertEquals(true, exported["browseBuildings3d"])
            assertEquals(false, exported["autoZoom"])
            assertEquals(true, exported["avoidTolls"])
        } finally { learned.close() }
    }

    @Test fun legacyDatastoreAppearanceValuesDoNotOverrideActiveChoices() = runBlocking {
        val base = RuntimeEnvironment.getApplication() as Application
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = com.drivemate.app.data.SettingsRepository(base, scope)
            // Use the process-owned store already used by the app. A second factory/file can
            // pass in isolation while the full suite still reads the delegated singleton.
            @Suppress("UNCHECKED_CAST")
            val store = repository.javaClass.getDeclaredField("store").let { field ->
                field.isAccessible = true
                field.get(repository) as androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
            }
            store.edit { values ->
                values.clear()
                values[androidx.datastore.preferences.core.booleanPreferencesKey("_ready")] = true
                values[androidx.datastore.preferences.core.stringPreferencesKey("theme")] = "BRIGHT"
                values[androidx.datastore.preferences.core.booleanPreferencesKey("browseBuildings3d")] = true
                values[androidx.datastore.preferences.core.booleanPreferencesKey("autoZoom")] = false
            }
            assertEquals("Legacy DataStore fields must not become active choices",
                Settings(autoZoom = false), withTimeout(15_000) { repository.load(Settings()) })
        } finally { scope.cancel() }
    }

}


/** Preferences whose editors forward every write to [actual]; [commitWith] decides what commit() reports. */
internal fun hookedPreferences(actual: SharedPreferences, commitWith: (SharedPreferences.Editor) -> Boolean): SharedPreferences =
    object : SharedPreferences by actual {
        override fun edit(): SharedPreferences.Editor {
            val delegate = actual.edit()
            return object : SharedPreferences.Editor by delegate {
                override fun putString(key: String, value: String?): SharedPreferences.Editor {
                    delegate.putString(key, value); return this
                }
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                    delegate.putBoolean(key, value); return this
                }
                override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                    delegate.putInt(key, value); return this
                }
                override fun commit(): Boolean = commitWith(delegate)
            }
        }
    }

/** A context whose "drivemate" preferences are [prefs]; everything else passes through. */
internal fun contextWithPreferences(base: Context, prefs: SharedPreferences): Context =
    object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            if (name == "drivemate") prefs else super.getSharedPreferences(name, mode)
    }
