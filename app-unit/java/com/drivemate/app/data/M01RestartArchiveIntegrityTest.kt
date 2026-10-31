package com.drivemate.app.data

import java.io.File
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * C01.2: a saved filename/size/header is not enough after process restart.
 * Synthetic bytes test local integrity only, not an actual UK map installation.
 */
class M01RestartArchiveIntegrityTest {
    @get:Rule val temp = TemporaryFolder()

    @After fun clearProcessTrust() = InstalledMapIntegrity.clear()

    private fun archive(): File {
        val bytes = ByteArray(256)
        "PMTiles".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[7] = 3
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putLong(8, 127L).putLong(16, 8L)
        bytes[127] = 1
        return temp.newFile("drivemate.pmtiles").apply { writeBytes(bytes) }
    }

    private fun sha(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun restartRequiresFullChecksumBeforeSelection() {
        val file = archive()
        val expected = sha(file)
        assertFalse(InstalledMapIntegrity.trusted(file, expected))
        assertTrue(InstalledMapIntegrity.verifyOnIo(file, expected))
        assertTrue(InstalledMapIntegrity.trusted(file, expected))
        InstalledMapIntegrity.clear() // Simulates the next process start.
        assertFalse(InstalledMapIntegrity.trusted(file, expected))
        assertTrue(InstalledMapIntegrity.verifyOnIo(file, expected))
        assertTrue(InstalledMapIntegrity.trusted(file, expected))
    }

    @Test fun sameSizeTamperAfterRestartFailsClosedAndPreservesArchive() {
        val file = archive()
        val expected = sha(file)
        assertTrue(InstalledMapIntegrity.verifyOnIo(file, expected))
        InstalledMapIntegrity.clear()
        val changed = file.readBytes().apply { this[180] = (this[180] + 1).toByte() }
        file.writeBytes(changed)
        assertFalse(InstalledMapIntegrity.verifyOnIo(file, expected))
        assertFalse(InstalledMapIntegrity.trusted(file, expected))
        assertTrue(file.isFile) // Never delete a fallback on an integrity failure.
    }

    @Test fun wrongExpectedDigestCannotBecomeTrusted() {
        val file = archive()
        assertFalse(InstalledMapIntegrity.verifyOnIo(file, "b".repeat(64)))
        assertFalse(InstalledMapIntegrity.trusted(file, "b".repeat(64)))
    }
}
