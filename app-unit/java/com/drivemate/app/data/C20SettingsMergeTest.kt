package com.drivemate.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

/** Category 20.2: restoring with Merge keeps this phone's existing values and only adds what is missing. */
class C20SettingsMergeTest {
    @Test fun mergeKeepsThisPhonesExistingHomeAndAddsMissingWork() {
        val out = BackupRestorePlan.values(
            BackupRestorePlan.Mode.MERGE,
            current = mapOf("home" to "Home A"),
            backup = mapOf("home" to "Home B", "work" to "Work B"),
        )
        assertEquals("Home A", out["home"])
        assertEquals("Work B", out["work"])
    }

    @Test fun mergeUnionsListsWithThisPhoneEntriesFirst() {
        val out = BackupRestorePlan.values(
            BackupRestorePlan.Mode.MERGE,
            current = mapOf("favorites" to """["Gym"]"""),
            backup = mapOf("favorites" to """["Gym","Park"]"""),
        )
        assertEquals("""["Gym","Park"]""", out["favorites"])
    }

    @Test fun replaceTakesTheBackupAsItIs() {
        val out = BackupRestorePlan.values(
            BackupRestorePlan.Mode.REPLACE,
            current = mapOf("home" to "Home A"),
            backup = mapOf("home" to "Home B"),
        )
        assertEquals("Home B", out["home"])
    }
}
