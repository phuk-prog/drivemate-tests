package com.drivemate.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * C01.2 frozen receipt-binding pretest. An installed SHA-named map must not
 * claim completion under a different saved verified digest after restart.
 * This checks local metadata consistency, NOT the archive's actual SHA-256.
 */
class M01InstalledReceiptTest {
    private val first = "a".repeat(64)
    private val second = "b".repeat(64)

    private fun state(version: String?) = mutableMapOf<String, Any>(
        "complete" to true,
        "archive" to "drivemate-$first.pmtiles",
        "layout" to "OWN"
    ).apply { if (version != null) put("version", version) }

    @Test fun mismatchedVerifiedDigestCannotSelectShaNamedArchive() {
        assertNull(MapLayout.installedArchive(state("sha256:$second")))
    }

    @Test fun matchingVerifiedDigestStillSelectsInstalledArchive() {
        assertEquals("drivemate-$first.pmtiles" to MapLayout.OWN,
            MapLayout.installedArchive(state("sha256:$first")))
    }

    @Test fun preReceiptLegacyArchiveRemainsCompatible() {
        assertEquals("drivemate.pmtiles" to MapLayout.LEGACY,
            MapLayout.installedArchive(mapOf("complete" to true)))
    }
}
