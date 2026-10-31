package com.drivemate.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * C01.2 frozen selection pretest, based on the normative PMTiles v3 127-byte
 * header and the first-16KiB root-directory rule.
 * This checks a cheap selection preflight, NOT full archive SHA-256 or tile decoding.
 */
class M01InstalledPmtilesRootTest {
    @get:Rule val temp = TemporaryFolder()

    private fun candidate(rootOffset: Long, rootLength: Long, size: Int = 132): File {
        val header = ByteArray(size)
        "PMTiles".toByteArray(Charsets.US_ASCII).copyInto(header)
        header[7] = 3
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(8, rootOffset).putLong(16, rootLength)
        return temp.newFile().apply { writeBytes(header) }
    }

    @Test fun signatureWithoutRootDirectoryCannotHidePublishedMap() {
        assertFalse(VerifiedMapArchive.plausibleInstalled(candidate(0, 0)))
    }

    @Test fun rootDirectoryPastEndOfArchiveCannotBeSelected() {
        assertFalse(VerifiedMapArchive.plausibleInstalled(candidate(127, 20)))
    }

    @Test fun rootDirectoryOutsideFirst16KiBCannotBeSelected() {
        assertFalse(VerifiedMapArchive.plausibleInstalled(candidate(16_380, 10, 20_000)))
    }

    @Test fun boundedRootDirectoryPassesOnlyCheapPreflight() {
        assertTrue(VerifiedMapArchive.plausibleInstalled(candidate(127, 5)))
    }
}
