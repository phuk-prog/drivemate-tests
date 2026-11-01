package com.drivemate.app.account

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Category 20.4: a failed cloud backup never clears local data; a retry that succeeds does. */
class C20BackupRetryTest {
    private val validBackup = """{"format":"DriveMate backup","version":1,"preferences":{}}"""

    @Test fun failedUploadKeepsLocalDataAndSkipsClearing() {
        var cleared = 0
        val session = AccountBackupSession(
            exportData = { validBackup },
            restoreData = {},
            clearLocalData = { cleared++ },
        )
        try {
            runBlocking { session.backupAndClear(upload = { throw java.io.IOException("offline") }) }
            fail("the failed upload must surface to the caller")
        } catch (expected: java.io.IOException) {
            // expected
        }
        assertEquals("local data must not be cleared after a failed upload", 0, cleared)
    }

    @Test fun retryAfterFailureClearsOnlyOnceTheUploadSucceeds() {
        var cleared = 0
        var attempts = 0
        val session = AccountBackupSession(
            exportData = { validBackup },
            restoreData = {},
            clearLocalData = { cleared++ },
        )
        // The app retries the upload itself (3 attempts in total). Fail every one of them in the first call.
        val upload: suspend (String) -> Unit = {
            attempts++
            if (attempts <= 3) throw java.io.IOException("offline")
        }
        runCatching { runBlocking { session.backupAndClear(upload = upload) } }
        assertEquals(3, attempts)
        assertEquals(0, cleared)
        runBlocking { session.backupAndClear(upload = upload) }
        assertEquals(4, attempts)
        assertEquals(1, cleared)
    }

    @Test fun unparseableExportIsNeverUploadedOrCleared() {
        var uploaded = false
        var cleared = 0
        val session = AccountBackupSession(
            exportData = { "{not json" },
            restoreData = {},
            clearLocalData = { cleared++ },
        )
        runCatching { runBlocking { session.backupAndClear(upload = { uploaded = true }) } }
        assertTrue(!uploaded)
        assertEquals(0, cleared)
    }
}
