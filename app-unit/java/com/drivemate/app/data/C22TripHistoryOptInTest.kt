package com.drivemate.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Category 22.1/22.2: trip history and commute suggestions are opt-in; the switch is a restorable boolean. */
class C22TripHistoryOptInTest {
    private fun pref(type: String, v: Any) = JSONObject().put("t", type).put("v", v)

    private fun backup(prefs: JSONObject): String =
        JSONObject().put("format", "DriveMate backup").put("version", 1).put("created", 1700000000000L)
            .put("preferences", prefs).toString()

    @Test fun optInIsASavedBooleanChoice() {
        assertTrue("tripHistoryOptIn" in BackupSchema.settingKeys)
        val v = BackupSchema.parse(backup(JSONObject().put("tripHistoryOptIn", pref("b", true))))
        assertEquals(mapOf("tripHistoryOptIn" to true), v.preferences)
    }

    @Test fun optInWithAWrongTypeIsRejected() {
        try {
            BackupSchema.parse(backup(JSONObject().put("tripHistoryOptIn", pref("i", 1))))
            fail("a non-boolean opt-in must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message != null)
        }
    }

    @Test fun backupWithoutTheSwitchLeavesTripHistoryOff() {
        val v = BackupSchema.parse(backup(JSONObject().put("beepAlerts", pref("b", true))))
        assertFalse(v.preferences.containsKey("tripHistoryOptIn"))
    }
}
