package com.drivemate.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Category 20.3: the recovery passphrase set in Settings must be at least 12 characters and typed twice the same. */
class C20RecoveryPassphraseTest {
    @Test fun elevenCharactersAreRefusedAndTwelveAreAccepted() {
        assertEquals("Use at least 12 characters.", RecoveryPassphrase.problem("abcdefghijk", "abcdefghijk"))
        assertNull(RecoveryPassphrase.problem("abcdefghijkl", "abcdefghijkl"))
    }

    @Test fun entriesMustMatch() {
        assertEquals("The two entries do not match yet.", RecoveryPassphrase.problem("correct horse battery", "correct horse batter"))
        assertNull(RecoveryPassphrase.problem("correct horse battery", "correct horse battery"))
    }

    @Test fun onlySpacesAreRefused() {
        assertEquals("Use letters or numbers, not only spaces.", RecoveryPassphrase.problem("            ", "            "))
    }

    @Test fun lengthCountsCharactersNotBytes() {
        // Twelve characters, several of them multi-byte, is still acceptable.
        assertNull(RecoveryPassphrase.problem("ééééééééééée", "ééééééééééée"))
    }
}
