package com.drivemate.app.data

import com.drivemate.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 3 / J27-01: compiled APK configuration, not a mocked lane policy.
 *
 * Every runner builds the very same Android BuildConfig as a customer build.
 * Without this setting OwnMap.loadAlong cannot download lanes-<square>.json,
 * road limits or other map guidance and a DMN route has no replacement lane
 * arrows. This test deliberately FAILS on the previous default blank URL.
 */
class MapPublisherBuildContractTest {
    @Test fun bundledBuildHasAnOwnedPublishedMapEndpointForRealLaneSquares() {
        val url = BuildConfig.MAP_RELEASES_BASE_URL
        assertTrue(
            "MAP_PUBLISHER_MISSING: an empty MAP_RELEASES_BASE_URL makes lane/limit " +
                "squares impossible to fetch, regardless of synthetic lane test passes",
            url.isNotBlank()
        )
        assertEquals(
            "Map files are published from the owner's drivemate-map GitHub releases; " +
                "do not point lane downloads to another repo or unverified endpoint",
            "https://github.com/phuk-prog/drivemate-map/releases/download",
            url
        )
        assertTrue("MapReleaseSource must be enabled in the actual build", MapReleaseSource.configured)
    }
}
