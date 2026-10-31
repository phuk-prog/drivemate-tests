package com.drivemate.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * C01.2 frozen pretest: a persisted SHA-verified archive length receipt must
 * be bounded and well typed. This is NOT a full post-restart SHA-256 check.
 */
class M01InstalledSizeReceiptTest {
    private val digest = "a".repeat(64)
    private fun state(receipt: Any?) = mutableMapOf<String, Any>(
        "complete" to true,
        "archive" to "drivemate-$digest.pmtiles",
        "layout" to "OWN",
        "version" to "sha256:$digest"
    ).apply { if (receipt != null) put("archiveBytes", receipt) }

    @Test fun invalidOrForgedSizeReceiptCannotSelectInstalledArchive() {
        assertNull(MapLayout.installedArchive(state(0L)))
        assertNull(MapLayout.installedArchive(state(-1L)))
        assertNull(MapLayout.installedArchive(state(1_950_000_001L)))
        assertNull(MapLayout.installedArchive(state("132")))
    }

    @Test fun boundedReceiptAndPreReceiptArchiveRemainCompatible() {
        val expected = "drivemate-$digest.pmtiles" to MapLayout.OWN
        assertEquals(expected, MapLayout.installedArchive(state(1_782_378_542L)))
        assertEquals(expected, MapLayout.installedArchive(state(null)))
    }
}
