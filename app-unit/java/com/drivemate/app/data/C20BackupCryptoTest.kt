package com.drivemate.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Category 20.3: cloud backups are sealed with a device key; plain version 1 backups still open unchanged. */
class C20BackupCryptoTest {
    private val plain = """{"format":"DriveMate backup","version":1,"preferences":{}}"""
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val otherKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun sealedBackupOpensBackToTheExactPlainText() {
        val sealed = BackupCrypto.seal(plain, key)
        assertEquals(plain, BackupCrypto.open(sealed) { key })
    }

    @Test fun sealedTextDoesNotContainThePlainPayload() {
        val secret = """{"format":"DriveMate backup","version":1,"preferences":{"home":"SECRET-PLACE"}}"""
        val sealed = BackupCrypto.seal(secret, key)
        assertFalse(sealed.contains("SECRET-PLACE"))
        assertFalse(sealed.contains("DriveMate backup"))
        assertEquals(BackupCrypto.FORMAT, JSONObject(sealed).getString("format"))
    }

    @Test fun eachSealUsesAFreshNonce() {
        assertFalse(BackupCrypto.seal(plain, key) == BackupCrypto.seal(plain, key))
    }

    @Test fun plainVersionOneBackupIsReturnedUnchangedWithoutTheKey() {
        var asked = 0
        val out = BackupCrypto.open(plain) { asked++; key }
        assertEquals(plain, out)
        assertEquals("the key must not be needed for a plain backup", 0, asked)
    }

    @Test fun backupSealedWithAnotherKeyIsRejected() {
        val sealed = BackupCrypto.seal(plain, otherKey)
        try {
            BackupCrypto.open(sealed) { key }
            fail("a backup sealed with another key must not open")
        } catch (expected: IllegalArgumentException) {
            assertFalse(expected.message!!.contains("preferences"))
        }
    }

    @Test fun missingDeviceKeyGivesAClearErrorAndNothingIsOpened() {
        val sealed = BackupCrypto.seal(plain, key)
        try {
            BackupCrypto.open(sealed) { null }
            fail("without the device key the backup must not open")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("another phone"))
        }
    }

    @Test fun tamperedCiphertextIsRejected() {
        val root = JSONObject(BackupCrypto.seal(plain, key))
        val data = java.util.Base64.getDecoder().decode(root.getString("data"))
        data[0] = (data[0].toInt() xor 1).toByte()
        root.put("data", java.util.Base64.getEncoder().encodeToString(data))
        try {
            BackupCrypto.open(root.toString()) { key }
            fail("tampered data must not be restored")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("could not be verified"))
        }
    }

    @Test fun changedHeaderIsRejectedBecauseItIsAuthenticated() {
        val root = JSONObject(BackupCrypto.seal(plain, key))
        root.put("version", 3)
        try {
            BackupCrypto.open(root.toString()) { key }
            fail("a changed envelope version must not open")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message != null)
        }
    }
}
