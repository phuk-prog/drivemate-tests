package com.drivemate.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Category 20.3: a portable backup is validated as a whole and never silently widens its scope. */
class Cat20BackupSchemaTest {
    private fun pref(type: String, v: Any) = JSONObject().put("t", type).put("v", v)

    private fun backup(prefs: JSONObject, format: String = "DriveMate backup", version: Int = 1): String =
        JSONObject().put("format", format).put("version", version).put("created", 1700000000000L)
            .put("preferences", prefs).toString()

    private fun rejected(text: String) {
        try {
            BackupSchema.parse(text)
            fail("backup should be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message != null)
        }
    }

    @Test fun validBackupKeepsOnlyPortableSettingsAndCreationTime() {
        val prefs = JSONObject()
            .put("beepAlerts", pref("b", true))
            .put("voiceTiming", pref("i", 1))
            .put("sound", pref("s", "ALERTS"))
        val v = BackupSchema.parse(backup(prefs))
        assertEquals(mapOf("beepAlerts" to true, "voiceTiming" to 1, "sound" to "ALERTS"), v.preferences)
        assertEquals(1700000000000L, v.createdAt)
        assertNull(v.learned)
    }

    @Test fun credentialsDeviceConsentAndUnknownKeysAreNotRestored() {
        val prefs = JSONObject()
            .put("apiKey", pref("s", "secret"))
            .put("deviceConsent", pref("b", true))
            .put("unknownFutureKey", pref("i", 3))
            .put("beepAlerts", pref("b", false))
        val v = BackupSchema.parse(backup(prefs))
        assertEquals(setOf("beepAlerts"), v.preferences.keys)
    }

    @Test fun wrongFormatOrVersionIsRejected() {
        rejected(backup(JSONObject(), format = "Other app"))
        rejected(backup(JSONObject(), version = 0))
        rejected(backup(JSONObject(), version = BackupSchema.VERSION + 1))
    }

    @Test fun typeTagsRangesAndEnumsAreValidatedBeforeAnyRestore() {
        rejected(backup(JSONObject().put("voiceTiming", pref("b", true))))
        rejected(backup(JSONObject().put("voiceTiming", pref("i", 5))))
        rejected(backup(JSONObject().put("speedMargin", pref("i", 101))))
        rejected(backup(JSONObject().put("sound", pref("s", "LOUD"))))
        rejected(backup(JSONObject().put("beepAlerts", pref("s", "true"))))
    }

    @Test fun invalidSavedPlaceIsRejected() {
        val badHome = JSONObject().put("name", "Home").put("lat", 95.0).put("lon", 0.0).toString()
        rejected(backup(JSONObject().put("home", pref("s", badHome))))
        val goodHome = JSONObject().put("name", "Home").put("lat", 53.4).put("lon", -2.2).toString()
        assertTrue(BackupSchema.parse(backup(JSONObject().put("home", pref("s", goodHome)))).preferences.containsKey("home"))
    }

    @Test fun learnedSpeedRowsAreValidated() {
        val good = JSONObject().put("format", "DriveMate backup").put("version", 1).put("created", 1L)
            .put("preferences", JSONObject()).put("learnedSpeeds", JSONArray().put(JSONArray().put(1).put(50.0).put(3)))
        assertEquals(1, BackupSchema.parse(good.toString()).learned!!.length())
        val bad = JSONObject(good.toString()).put("learnedSpeeds", JSONArray().put(JSONArray().put(1).put(50.0).put(0)))
        rejected(bad.toString())
    }

    @Test fun oversizeAndMalformedInputGiveFixedErrors() {
        try {
            BackupSchema.parse("x".repeat(BackupSchema.MAX_CHARS + 1))
            fail("oversize backup should be rejected")
        } catch (e: IllegalArgumentException) {
            assertEquals("Backup is too large", e.message)
        }
        try {
            BackupSchema.parse("{not json SECRET-TEXT")
            fail("malformed backup should be rejected")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid or unsupported DriveMate backup", e.message)
            assertFalse(e.message!!.contains("SECRET"))
        }
    }
}
