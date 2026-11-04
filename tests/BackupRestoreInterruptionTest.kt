package com.drivemate.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.drivemate.app.controller.PersonalDataController
import com.drivemate.app.data.BackupSchema
import com.drivemate.app.data.Settings
import com.drivemate.app.data.SettingsRepository
import com.drivemate.app.data.SettingsStore
import com.drivemate.app.nav.LearnedSpeeds
import kotlinx.coroutines.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Synthetic fixtures only. Exercises production restore/startup code with Android preferences,
 * SQLite and SettingsRepository. An Error bypasses exception rollback at explicit commit
 * boundaries; controller/SQLite recreation models restart, not an actual OS process kill.
 * The process-singleton DataStore remains alive, so physical durability needs device testing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class BackupRestoreInterruptionTest {
    private enum class Cut { NONE, AFTER_PREFERENCES, BEFORE_AUTHORITY, AFTER_AUTHORITY, REJECT_AUTHORITY, RECOVERY_PREFERENCES }
    private class AbruptStop : Error()

    private class Fixture {
        val oldSettings = Settings(avoidTolls = false)
        private val newParking = "1.0,1.0"
        private val oldParking = "0.0,0.0"
        private val oldRows = "[[1,10.0,3]]"
        private val newRows = "[[2,20.0,4]]"
        private var cut = Cut.NONE
        private var armed = false
        private var stopped = CompletableDeferred<Unit>()
        private var sessionScope = scope()
        private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, error ->
                if (error is AbruptStop) stopped.complete(Unit)
                else stopped.completeExceptionally(error)
            })
        private val base = RuntimeEnvironment.getApplication()
        private val actual = base.getSharedPreferences("drivemate", Context.MODE_PRIVATE)
        private val hooked = object : SharedPreferences by actual {
            override fun edit(): SharedPreferences.Editor {
                val delegate = actual.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun commit(): Boolean {
                        val saved = delegate.commit()
                        val stopAfterIncoming = cut == Cut.AFTER_PREFERENCES &&
                            actual.getBoolean("avoidTolls", false) &&
                            actual.getString("parking", null) == newParking
                        val stopDuringRecovery = cut == Cut.RECOVERY_PREFERENCES &&
                            !actual.getBoolean("avoidTolls", false) &&
                            actual.getString("parking", null) == oldParking
                        if (saved && armed && (stopAfterIncoming || stopDuringRecovery)) {
                            armed = false
                            throw AbruptStop()
                        }
                        return saved
                    }
                }
            }
        }
        private val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "drivemate") hooked else super.getSharedPreferences(name, mode)
        }
        private val durable = SettingsRepository(context, sessionScope)
        private val authority = object : SettingsStore {
            override suspend fun load(fallback: Settings): Settings = durable.load(fallback)
            override suspend fun save(settings: Settings) {
                if (armed && cut == Cut.BEFORE_AUTHORITY) {
                    armed = false
                    throw AbruptStop()
                }
                if (armed && cut == Cut.REJECT_AUTHORITY) {
                    armed = false
                    throw IllegalStateException("Synthetic authority rejection")
                }
                durable.save(settings)
                if (armed && cut == Cut.AFTER_AUTHORITY) {
                    armed = false
                    throw AbruptStop()
                }
            }
        }
        private var learned = LearnedSpeeds(context)
        private var controller = controller()
        private fun controller() = PersonalDataController(context, sessionScope, {}, authority)

        suspend fun initialize() {
            controller.loadSettings(oldSettings)
            durable.save(oldSettings)
            assertTrue(oldSettings.save(actual, synchronous = true))
            assertTrue(actual.edit().putString("parking", oldParking).commit())
            learned.importJson(JSONArray(oldRows))
            restart()
            assertState(old = true)
        }

        private fun backup() = JSONObject()
            .put("format", "DriveMate backup")
            .put("version", BackupSchema.VERSION)
            .put("preferences", JSONObject()
                .put("avoidTolls", JSONObject().put("t", "b").put("v", true))
                .put("parking", JSONObject().put("t", "s").put("v", newParking)))
            .put("learnedSpeeds", JSONArray(newRows)).toString()

        suspend fun restore(point: Cut = Cut.NONE): Boolean {
            cut = point
            armed = true
            return withTimeout(15_000) { controller.restoreCloud(backup(), learned) }
        }

        suspend fun interrupt(point: Cut) {
            cut = point
            armed = true
            val pending = sessionScope.async { controller.restoreCloud(backup(), learned) }
            withTimeout(15_000) { stopped.await() }
            sessionScope.cancel()
            withTimeout(15_000) { sessionScope.coroutineContext[Job]!!.join() }
            pending.cancel()
            assertFalse("Interruption hook was not consumed", armed)
        }

        suspend fun invalidRestore(): Boolean =
            withTimeout(15_000) { controller.restoreCloud("{}", learned) }

        private suspend fun recreate() {
            sessionScope.cancel()
            withTimeout(15_000) { sessionScope.coroutineContext[Job]!!.join() }
            learned.close()
            learned = LearnedSpeeds(context)
            stopped = CompletableDeferred()
            sessionScope = scope()
            controller = controller()
        }

        suspend fun restart() {
            recreate()
            withTimeout(15_000) { controller.loadSettings(oldSettings) }
        }

        suspend fun interruptRecovery(point: Cut) {
            cut = point
            armed = true
            recreate()
            val pending = sessionScope.async { controller.loadSettings(oldSettings) }
            withTimeout(15_000) { stopped.await() }
            sessionScope.cancel()
            withTimeout(15_000) { sessionScope.coroutineContext[Job]!!.join() }
            pending.cancel()
            assertFalse("Interruption hook was not consumed", armed)
        }

        suspend fun checkCorruptCheckpoint() {
            val checkpoint = File(context.noBackupFilesDir, "portable-restore.rollback")
            checkpoint.writeText("{")
            try {
                try {
                    restart()
                    fail("Corrupt recovery checkpoint was ignored")
                } catch (_: IllegalArgumentException) {
                    // Rejected before the portable stores or settings authority are changed.
                }
                assertFalse(withTimeout(15_000) { controller.restoreCloud(backup(), learned) })
                assertState(old = true)
                assertTrue("Corrupt checkpoint must remain available for recovery", checkpoint.exists())
                assertEquals("{", checkpoint.readText())
            } finally {
                checkpoint.delete()
            }
        }

        fun assertState(old: Boolean) {
            assertEquals("Authoritative setting", !old, controller.currentSettings().avoidTolls)
            assertEquals("Preference mirror", !old, actual.getBoolean("avoidTolls", false))
            assertEquals("Portable personal data", if (old) oldParking else newParking,
                actual.getString("parking", null))
            assertEquals("Learned-speed rows", JSONArray(if (old) oldRows else newRows).toString(), learned.exportJson().toString())
        }

        suspend fun assertCoherentRestart() {
            restart()
            // An interrupted operation may recover either complete state, never a mixture.
            assertState(old = !controller.currentSettings().avoidTolls)
        }

        suspend fun close() {
            sessionScope.cancel()
            withTimeout(15_000) { sessionScope.coroutineContext[Job]!!.join() }
            learned.close()
        }
    }

    private fun scenario(block: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture()
        try { f.initialize(); block(f) } finally { f.close() }
    }

    @Test fun successfulRestoreSurvivesControllerAndDatabaseRecreation() = scenario { f ->
        assertTrue(f.restore())
        f.restart()
        f.assertState(old = false)
    }

    @Test fun ordinaryAuthorityFailureRollsBackEveryPortableStore() = scenario { f ->
        assertFalse(f.restore(Cut.REJECT_AUTHORITY))
        f.restart()
        f.assertState(old = true)
    }

    @Test fun interruptionAfterPreferencesRecoversOneCompleteState() = scenario { f ->
        f.interrupt(Cut.AFTER_PREFERENCES)
        f.assertCoherentRestart()
    }

    @Test fun interruptionAfterLearnedSpeedsRecoversOneCompleteState() = scenario { f ->
        f.interrupt(Cut.BEFORE_AUTHORITY)
        f.assertCoherentRestart()
    }

    @Test fun interruptionAfterAuthorityCommitRecoversOneCompleteState() = scenario { f ->
        f.interrupt(Cut.AFTER_AUTHORITY)
        f.assertCoherentRestart()
    }

    @Test fun invalidBackupDoesNotModifyAnyStore() = scenario { f ->
        assertFalse(f.invalidRestore())
        f.restart()
        f.assertState(old = true)
    }

    @Test fun interruptedPreferenceRecoveryCanRetryWithoutLosingOriginalState() = scenario { f ->
        f.interrupt(Cut.BEFORE_AUTHORITY)
        f.interruptRecovery(Cut.RECOVERY_PREFERENCES)
        f.restart()
        f.assertState(old = true)
        f.restart()
        f.assertState(old = true)
    }

    @Test fun interruptedRecoveryBeforeAuthorityCanRetryWithoutLosingOriginalState() = scenario { f ->
        f.interrupt(Cut.BEFORE_AUTHORITY)
        f.interruptRecovery(Cut.BEFORE_AUTHORITY)
        f.restart()
        f.assertState(old = true)
        f.restart()
        f.assertState(old = true)
    }

    @Test fun interruptedRecoveryAfterAuthorityCanRetryWithoutLosingOriginalState() = scenario { f ->
        f.interrupt(Cut.BEFORE_AUTHORITY)
        f.interruptRecovery(Cut.AFTER_AUTHORITY)
        f.restart()
        f.assertState(old = true)
        f.restart()
        f.assertState(old = true)
    }

    @Test fun corruptCheckpointBlocksRestoreWithoutChangingExistingData() = scenario { f ->
        f.checkCorruptCheckpoint()
    }


    private suspend fun settingsWriteFailureFixture(block: suspend (
        PersonalDataController, (Boolean, Boolean) -> Unit, () -> Settings, Context
    ) -> Unit) {
        val base = RuntimeEnvironment.getApplication()
        val actual = base.getSharedPreferences("drivemate", Context.MODE_PRIVATE)
        assertTrue(actual.edit().clear().commit())
        var rejectMirror = false
        var rejectAuthority = false
        var authoritative = Settings()
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
                    override fun commit(): Boolean = if (rejectMirror) false else delegate.commit()
                }
            }
        }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "drivemate") hooked else super.getSharedPreferences(name, mode)
        }
        val authority = object : SettingsStore {
            override suspend fun load(fallback: Settings) = authoritative
            override suspend fun save(settings: Settings) {
                check(!rejectAuthority) { "Synthetic rejected authority write" }
                authoritative = settings
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = PersonalDataController(context, scope, {}, authority)
        try {
            withTimeout(15_000) { controller.loadSettings(Settings()) }
            block(controller, { mirror, store -> rejectMirror = mirror; rejectAuthority = store },
                { authoritative }, context)
        } finally {
            scope.cancel()
            withTimeout(15_000) { scope.coroutineContext[Job]!!.join() }
            assertTrue(actual.edit().clear().commit())
        }
    }

    private suspend fun rejectedSettingsWrite(controller: PersonalDataController, settings: Settings) {
        val rejected = CompletableDeferred<Unit>()
        controller.saveSettings(settings,
            onSaved = { rejected.completeExceptionally(AssertionError("Expected a rejected partial write")) },
            onFailed = { rejected.complete(Unit) })
        withTimeout(15_000) { rejected.await() }
    }

    @Test fun committedSettingsMirrorCanBeRepairedByFlush() = runBlocking {
        settingsWriteFailureFixture { controller, reject, authority, context ->
            val changed = Settings(avoidTolls = true)
            reject(true, false)
            rejectedSettingsWrite(controller, changed)
            assertEquals(changed, authority())
            assertEquals(changed, controller.currentSettings())
            assertFalse(Settings.load(context.getSharedPreferences("drivemate", 0)).avoidTolls)
            assertTrue(runCatching { withTimeout(15_000) { controller.flush() } }
                .exceptionOrNull() is IllegalStateException)
            reject(false, false)
            withTimeout(15_000) { controller.flush() }
            assertTrue(Settings.load(context.getSharedPreferences("drivemate", 0)).avoidTolls)
        }
    }

    @Test fun cloudBackupRetriesMirrorAfterCommittedSettingsWrite() = runBlocking {
        settingsWriteFailureFixture { controller, reject, authority, context ->
            val changed = Settings(avoidMotorways = true)
            reject(true, false)
            rejectedSettingsWrite(controller, changed)
            assertEquals(changed, authority())
            reject(false, false)
            val learned = LearnedSpeeds(context)
            try {
                val text = withTimeout(15_000) { controller.exportCloud(learned) }
                assertTrue(JSONObject(text).getJSONObject("preferences")
                    .getJSONObject("avoidMotorways").getBoolean("v"))
            } finally { learned.close() }
        }
    }

    @Test fun rejectedAuthoritativeSettingsStillBlockFlushAndBackup() = runBlocking {
        settingsWriteFailureFixture { controller, reject, authority, context ->
            val before = authority()
            reject(false, true)
            rejectedSettingsWrite(controller, Settings(avoidTolls = true))
            assertEquals(before, authority())
            assertEquals(before, controller.currentSettings())
            reject(false, false)
            assertTrue(runCatching { withTimeout(15_000) { controller.flush() } }.exceptionOrNull() is IllegalStateException)
            val learned = LearnedSpeeds(context)
            try {
                assertTrue(runCatching { withTimeout(15_000) { controller.exportCloud(learned) } }
                    .exceptionOrNull() is IllegalStateException)
            } finally { learned.close() }
        }
    }

    @Test fun successfulSettingsRetryClearsAuthoritativeFailure() = runBlocking {
        settingsWriteFailureFixture { controller, reject, authority, _ ->
            val changed = Settings(avoidTolls = true)
            reject(false, true)
            rejectedSettingsWrite(controller, changed)
            reject(false, false)
            val accepted = CompletableDeferred<Unit>()
            controller.saveSettings(changed, onSaved = { accepted.complete(Unit) },
                onFailed = { accepted.completeExceptionally(AssertionError("Retry must succeed")) })
            withTimeout(15_000) { accepted.await(); controller.flush() }
            assertEquals(changed, authority())
        }
    }

}
