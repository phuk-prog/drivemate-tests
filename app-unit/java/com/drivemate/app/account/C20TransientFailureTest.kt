package com.drivemate.app.account

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Category 20.4: only transient failures are retried, and validating a backup never runs on the caller's thread. */
class C20TransientFailureTest {
    private val validBackup = """{"format":"DriveMate backup","version":1,"preferences":{}}"""

    private fun session(
        cleared: MutableList<Int>,
        parse: (String) -> Unit = {},
    ) = AccountBackupSession(
        exportData = { validBackup },
        restoreData = {},
        clearLocalData = { cleared.add(1) },
        pauseBeforeRetry = {},
        parse = parse,
    )

    @Test fun refusedUploadIsNotRetriedAndKeepsLocalData() {
        var attempts = 0
        val cleared = mutableListOf<Int>()
        try {
            runBlocking {
                session(cleared).backupAndClear(upload = { attempts++; throw DriveHttpException(401) })
            }
            fail("a refused upload must surface to the caller")
        } catch (expected: DriveHttpException) {
            assertEquals(401, expected.code)
        }
        assertEquals("a permission refusal will not succeed on retry", 1, attempts)
        assertTrue(cleared.isEmpty())
    }

    @Test fun serverErrorIsRetriedUntilItSucceeds() {
        var attempts = 0
        val cleared = mutableListOf<Int>()
        runBlocking {
            session(cleared).backupAndClear(upload = {
                attempts++
                if (attempts < 3) throw DriveHttpException(503)
            })
        }
        assertEquals(3, attempts)
        assertEquals(1, cleared.size)
    }

    @Test fun networkDropIsRetried() {
        var attempts = 0
        runBlocking {
            session(mutableListOf()).backupAndClear(upload = {
                attempts++
                if (attempts == 1) throw IOException("reset by peer")
            })
        }
        assertEquals(2, attempts)
    }

    @Test fun classifiesTransientAndFinalFailures() {
        assertTrue(BackupFailures.isTransient(IOException("offline")))
        for (code in listOf(408, 429, 500, 503)) assertTrue("$code is transient", BackupFailures.isTransient(DriveHttpException(code)))
        for (code in listOf(400, 401, 403, 404)) assertFalse("$code is final", BackupFailures.isTransient(DriveHttpException(code)))
        assertFalse(BackupFailures.isTransient(IllegalStateException("Cloud backup is too large")))
    }

    @Test fun validatingAnExportRunsOffTheCallersThread() {
        val callerThread = Thread.currentThread().name
        var parsedOn = ""
        runBlocking {
            session(mutableListOf(), parse = { parsedOn = Thread.currentThread().name })
                .backupAndClear(upload = {})
        }
        assertTrue("validation ran on $parsedOn, caller was $callerThread", parsedOn.isNotEmpty() && parsedOn != callerThread)
    }

    @Test fun validatingARestoreRunsOffTheCallersThread() {
        val callerThread = Thread.currentThread().name
        var parsedOn = ""
        runBlocking {
            session(mutableListOf(), parse = { parsedOn = Thread.currentThread().name })
                .restore { validBackup }
        }
        assertTrue("validation ran on $parsedOn, caller was $callerThread", parsedOn.isNotEmpty() && parsedOn != callerThread)
    }
}
