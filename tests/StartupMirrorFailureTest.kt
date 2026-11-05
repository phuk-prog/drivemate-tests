package com.drivemate.app

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
        val hooked = object : SharedPreferences by actual {
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
                    override fun commit(): Boolean = false
                }
            }
        }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "drivemate") hooked else super.getSharedPreferences(name, mode)
        }
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
}
