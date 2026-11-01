package com.drivemate.app.account

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Category 20.5: a restore never loses local data when the backup is missing or unreadable, and a good backup restores exactly. */
class C20LifecycleRestoreTest {
    private val validBackup = """{"format":"DriveMate backup","version":1,"preferences":{}}"""

    private fun session(restored: MutableList<String>, cleared: MutableList<Int>) = AccountBackupSession(
        exportData = { validBackup },
        restoreData = { restored += it },
        clearLocalData = { cleared.add(1); Unit },
    )

    @Test fun missingBackupLeavesLocalDataUntouched() {
        val restored = mutableListOf<String>()
        val cleared = mutableListOf<Int>()
        runBlocking { session(restored, cleared).restore(requireBackup = false) { null } }
        assertTrue(restored.isEmpty())
        assertTrue(cleared.isEmpty())
    }

    @Test fun requiredBackupMissingFailsWithoutRestoring() {
        val restored = mutableListOf<String>()
        try {
            runBlocking { session(restored, mutableListOf()).restore(requireBackup = true) { null } }
            fail("a required recovery backup that is missing must fail")
        } catch (expected: IllegalStateException) {
            // expected
        }
        assertTrue(restored.isEmpty())
    }

    @Test fun unreadableBackupIsNeverRestored() {
        val restored = mutableListOf<String>()
        runCatching { runBlocking { session(restored, mutableListOf()).restore { "{not json" } } }
        assertTrue(restored.isEmpty())
    }

    @Test fun goodBackupRestoresTheExactText() {
        val restored = mutableListOf<String>()
        runBlocking { session(restored, mutableListOf()).restore { validBackup } }
        assertEquals(listOf(validBackup), restored)
        assertFalse(restored.isEmpty())
    }
}
