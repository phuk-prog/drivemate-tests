package com.drivemate.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Category 20.3: cloud backups are sealed with the device key by default, or with a recovery passphrase so they
 * restore on another phone. Plain version 1 backups still open unchanged.
 */
class C20BackupCryptoTest {
    private val plain = """{"format":"DriveMate backup","version":1,"preferences":{}}"""
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val otherKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val passphrase = "correct horse battery"
    // Lower than the shipped default so the JVM check stays quick; the envelope records the count it used.
    private val fastIterations = 100_000

    private fun open(text: String, device: SecretKey? = null, pass: CharArray? = null) =
        BackupCrypto.open(text, { device }, { pass })

    @Test fun sealedBackupOpensBackToTheExactPlainText() {
        assertEquals(plain, open(BackupCrypto.seal(plain, key), device = key))
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

    @Test fun plainVersionOneBackupIsReturnedUnchangedWithoutAnyKey() {
        var deviceAsked = 0
        var passAsked = 0
        val out = BackupCrypto.open(plain, { deviceAsked++; key }, { passAsked++; null })
        assertEquals(plain, out)
        assertEquals(0, deviceAsked + passAsked)
    }

    @Test fun backupSealedWithAnotherKeyIsRejected() {
        try {
            open(BackupCrypto.seal(plain, otherKey), device = key)
            fail("a backup sealed with another key must not open")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("could not be verified"))
        }
    }

    @Test fun missingDeviceKeyGivesAClearErrorAndNothingIsOpened() {
        try {
            open(BackupCrypto.seal(plain, key), device = null)
            fail("without the device key the backup must not open")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("recovery passphrase"))
        }
    }

    @Test fun tamperedCiphertextIsRejected() {
        val root = JSONObject(BackupCrypto.seal(plain, key))
        val data = java.util.Base64.getDecoder().decode(root.getString("data"))
        data[0] = (data[0].toInt() xor 1).toByte()
        root.put("data", java.util.Base64.getEncoder().encodeToString(data))
        try {
            open(root.toString(), device = key)
            fail("tampered data must not be restored")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("could not be verified"))
        }
    }

    @Test fun changedHeaderIsRejectedBecauseItIsAuthenticated() {
        val root = JSONObject(BackupCrypto.seal(plain, key))
        root.put("version", 3)
        try {
            open(root.toString(), device = key)
            fail("a changed envelope version must not open")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message != null)
        }
    }

    @Test fun passphraseBackupRestoresWithoutAnyDeviceKey() {
        val sealed = BackupCrypto.sealWithPassphrase(plain, passphrase.toCharArray(), fastIterations)
        assertFalse(sealed.contains("DriveMate backup"))
        assertEquals("passphrase", JSONObject(sealed).getString("key"))
        assertEquals(plain, open(sealed, device = null, pass = passphrase.toCharArray()))
    }

    @Test fun wrongPassphraseIsRejected() {
        val sealed = BackupCrypto.sealWithPassphrase(plain, passphrase.toCharArray(), fastIterations)
        try {
            open(sealed, pass = "not the passphrase".toCharArray())
            fail("a wrong passphrase must not restore")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Check the passphrase"))
        }
    }

    @Test fun passphraseBackupWithoutPassphraseAsksForIt() {
        val sealed = BackupCrypto.sealWithPassphrase(plain, passphrase.toCharArray(), fastIterations)
        try {
            open(sealed, device = key, pass = null)
            fail("a passphrase backup must not open with the device key alone")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Enter that passphrase"))
        }
    }

    @Test fun passphraseHeaderIsAuthenticated() {
        val root = JSONObject(BackupCrypto.sealWithPassphrase(plain, passphrase.toCharArray(), fastIterations))
        root.put("iterations", fastIterations + 1)
        try {
            open(root.toString(), pass = passphrase.toCharArray())
            fail("changed key-derivation settings must not open")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("could not be verified"))
        }
    }

    @Test fun passphraseNeededAndWrongPassphraseHaveTheirOwnTypes() {
        val sealed = BackupCrypto.sealWithPassphrase(plain, passphrase.toCharArray(), fastIterations)
        try {
            open(sealed)
            fail("no passphrase must give PassphraseNeededException")
        } catch (expected: BackupCrypto.PassphraseNeededException) {
            // expected
        }
        try {
            open(sealed, pass = "wrong passphrase here".toCharArray())
            fail("a wrong passphrase must give WrongPassphraseException")
        } catch (expected: BackupCrypto.WrongPassphraseException) {
            // expected
        }
    }

    @Test fun damagedDeviceBackupIsNeverReportedAsAWrongPassphrase() {
        // The app asks for a passphrase only on WrongPassphraseException, so a device-key failure must not be one.
        val root = JSONObject(BackupCrypto.seal(plain, key))
        val data = java.util.Base64.getDecoder().decode(root.getString("data"))
        data[0] = (data[0].toInt() xor 1).toByte()
        root.put("data", java.util.Base64.getEncoder().encodeToString(data))
        try {
            open(root.toString(), device = key)
            fail("tampered device-key data must not restore")
        } catch (expected: BackupCrypto.WrongPassphraseException) {
            fail("a device-key failure must not ask for a passphrase")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("could not be verified"))
        }
    }

    @Test fun shortPassphraseIsRefused() {
        try {
            BackupCrypto.sealWithPassphrase(plain, "short".toCharArray(), fastIterations)
            fail("a short recovery passphrase must be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("at least ${BackupCrypto.MIN_PASSPHRASE_CHARS}"))
        }
    }
}
