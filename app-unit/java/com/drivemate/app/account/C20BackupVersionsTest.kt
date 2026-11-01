package com.drivemate.app.account

import org.junit.Assert.assertEquals
import org.junit.Test

/** Category 20.4: each upload is a new version; the newest three are kept and older ones are trimmed. */
class C20BackupVersionsTest {
    private fun v2(ms: Long) = "f$ms" to BackupVersions.nameFor(ms)

    @Test fun namesSortByTimeAsWritten() {
        assertEquals("drivemate-backup-v2-1700000000000.json", BackupVersions.nameFor(1700000000000L))
        assertEquals("drivemate-backup-v2-0000000000001.json", BackupVersions.nameFor(1L))
        val newest = BackupVersions.newestFirst(listOf(v2(1700000000000L), v2(1700000300000L), v2(1699999999999L)))
        assertEquals(listOf("f1700000300000", "f1700000000000", "f1699999999999"), newest.map { it.first })
    }

    @Test fun legacySingleFileRanksBelowEveryVersion() {
        val legacy = "legacy" to "drivemate-backup-v1.json"
        val newest = BackupVersions.newestFirst(listOf(legacy, v2(1L)))
        assertEquals("f1", newest.first().first)
        assertEquals("legacy", newest.last().first)
    }

    @Test fun unknownNamesAreIgnoredNeverTrimmedOrRestored() {
        val files = listOf("other" to "notes.txt", v2(5L))
        assertEquals(listOf("f5"), BackupVersions.newestFirst(files).map { it.first })
        assertEquals(emptyList<String>(), BackupVersions.toRemove(files))
    }

    @Test fun onlyVersionsBeyondTheNewestThreeAreRemoved() {
        val files = listOf(v2(1L), v2(2L), v2(3L), v2(4L), "legacy" to "drivemate-backup-v1.json")
        assertEquals(listOf("f1", "legacy"), BackupVersions.toRemove(files))
        assertEquals(3, BackupVersions.KEEP)
    }
}
