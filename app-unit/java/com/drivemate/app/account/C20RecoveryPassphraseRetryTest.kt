package com.drivemate.app.account

import com.drivemate.app.data.BackupCrypto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** Category 20.3: a passphrase backup restores with the stored passphrase, or after the user types one. */
class C20RecoveryPassphraseRetryTest {
    private val backupText = """{"format":"DriveMate backup","version":1,"preferences":{}}"""

    /** A fake cloud that opens only with [right]; it reports the same errors the real envelope code does. */
    private fun cloud(right: String): suspend (CharArray?) -> String {
        return { passphrase: CharArray? ->
            when {
                passphrase == null -> throw BackupCrypto.PassphraseNeededException()
                String(passphrase) == right -> backupText
                else -> throw BackupCrypto.WrongPassphraseException()
            }
        }
    }

    @Test fun storedPassphraseRestoresWithoutAskingAnyone() {
        var asked = 0
        val out = runBlocking {
            RecoveryPassphraseRetry.run(stored = "right phrase here".toCharArray(), attempt = cloud("right phrase here"),
                ask = { asked++; null })
        }
        assertEquals(backupText, out)
        assertEquals(0, asked)
    }

    @Test fun noStoredPassphraseAsksOnceWithoutSayingItWasWrong() {
        val asks = mutableListOf<Boolean>()
        val out = runBlocking {
            RecoveryPassphraseRetry.run(stored = null, attempt = cloud("typed right one"),
                ask = { wrong -> asks += wrong; "typed right one".toCharArray() })
        }
        assertEquals(backupText, out)
        assertEquals(listOf(false), asks)
    }

    @Test fun wrongStoredPassphraseFallsBackToTheUserAndSaysSo() {
        val asks = mutableListOf<Boolean>()
        val out = runBlocking {
            RecoveryPassphraseRetry.run(stored = "old phrase here!".toCharArray(), attempt = cloud("new phrase here!"),
                ask = { wrong -> asks += wrong; "new phrase here!".toCharArray() })
        }
        assertEquals(backupText, out)
        assertEquals(listOf(true), asks)
    }

    @Test fun aWrongTypedPassphraseIsAskedAgain() {
        val asks = mutableListOf<Boolean>()
        val typed = ArrayDeque(listOf("first wrong try", "second right try"))
        val out = runBlocking {
            RecoveryPassphraseRetry.run(stored = null, attempt = cloud("second right try"),
                ask = { wrong -> asks += wrong; typed.removeFirst().toCharArray() })
        }
        assertEquals(backupText, out)
        assertEquals(listOf(false, true), asks)
    }

    @Test fun cancelRestoresNothing() {
        try {
            runBlocking { RecoveryPassphraseRetry.run(stored = null, attempt = cloud("never used here"), ask = { null }) }
            fail("cancelling must stop the restore")
        } catch (expected: IllegalStateException) {
            assertEquals("Restore cancelled; nothing was restored.", expected.message)
        }
    }

    @Test fun stopsAfterTheLimitOfWrongPassphrases() {
        var asks = 0
        try {
            runBlocking {
                RecoveryPassphraseRetry.run(stored = null, attempt = cloud("the real one"), ask = { asks++; "a guess".toCharArray() })
            }
            fail("too many wrong passphrases must stop the restore")
        } catch (expected: IllegalStateException) {
            assertEquals("Too many wrong passphrases; nothing was restored.", expected.message)
        }
        assertEquals(RecoveryPassphraseRetry.MAX_TRIES, asks)
    }

    @Test fun deviceKeyFailureIsNotRetriedWithAPassphrase() {
        var asked = 0
        try {
            runBlocking {
                RecoveryPassphraseRetry.run<String>(stored = null,
                    attempt = { throw IllegalArgumentException("Encrypted backup could not be verified; nothing was restored.") },
                    ask = { asked++; "anything here x".toCharArray() })
            }
            fail("a damaged device-key backup must fail")
        } catch (expected: IllegalArgumentException) {
            assertEquals(0, asked)
        }
    }

    @Test fun noBackupIsNotAnError() {
        var asked = 0
        val out = runBlocking { RecoveryPassphraseRetry.run<String?>(stored = null, attempt = { null }, ask = { asked++; null }) }
        assertNull(out)
        assertEquals(0, asked)
    }
}
