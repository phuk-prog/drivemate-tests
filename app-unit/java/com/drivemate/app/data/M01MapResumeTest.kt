package com.drivemate.app.data

import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verify real PMTiles resume rules before a partial download can be appended. */
class M01MapResumeTest {
    @get:Rule val temp = TemporaryFolder()
    private val full = 1_781_936_174L
    private val have = 550_000L
    private val rest = full - have

    @Test fun fullResponseReplacesAnOldPartialFile() {
        assertEquals(full, MapResume.total(200, null, 0, full, full))
        assertEquals(full, MapResume.total(200, null, 0, -1, full))
    }

    @Test fun matchingPartialContentRangeMayResume() {
        val range = "bytes $have-${full - 1}/$full"
        assertEquals(full, MapResume.total(206, range, have, rest, full))
    }

    @Test fun rejectsWrongOffsetsLengthsAndChangingPublishedVersions() {
        val bad = listOf(
            "bytes 0-${full - have - 1}/$full",
            "bytes ${have + 1}-${full - 1}/$full",
            "bytes $have-${full - 1}/${full + 1}",
            "bytes $have-${full}/$full",
            "bytes rubbish",
            null,
        )
        for (header in bad) {
            assertTrue("Untrusted resume range $header", runCatching {
                MapResume.total(206, header, have, rest, full)
            }.isFailure)
        }
        assertTrue(runCatching { MapResume.total(206, "bytes $have-${full - 1}/$full", have, rest - 1, full) }.isFailure)
        assertTrue(runCatching { MapResume.total(200, null, 0, full - 1, full) }.isFailure)
        assertTrue(runCatching { MapResume.total(503, null, have, rest, full) }.isFailure)
        assertTrue(runCatching { MapResume.total(206, "bytes 0-${full - 1}/$full", -1, full, full) }.isFailure)
    }
    @Test fun oversizedResumeCheckpointIsDiscardedInsteadOfPermanentlyRetried() {
        val bad = temp.newFile("oversized.pmtiles.part")
        bad.writeBytes(ByteArray(8))
        assertEquals(0L, MapResume.reusableCheckpoint(bad, 7L))
        assertFalse(bad.exists())
        val valid = temp.newFile("valid.pmtiles.part")
        valid.writeBytes(ByteArray(3))
        assertEquals(3L, MapResume.reusableCheckpoint(valid, 7L))
        assertTrue(valid.isFile)
    }

}
