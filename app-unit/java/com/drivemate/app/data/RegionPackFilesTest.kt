package com.drivemate.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The optional download of every published region, checked against the real catalogue (2026-10-24). */
class RegionPackFilesTest {
    @get:Rule val folder = TemporaryFolder()
    private val catalog = PublishedRegionCatalog.parse(
        javaClass.classLoader!!.getResource("region-catalog-2026-10-24.json")!!.readText())
    private fun region(id: String) = catalog.regions.single { it.id == id }
    private val olderCheshire = "map-data-region-cheshire-r39000000000-a1~" + "a".repeat(64) + ".pmtiles"

    private fun file(name: String, modified: Long = 1_000L) = File(folder.root, name).apply { writeText("x"); setLastModified(modified) }

    @Test fun everyPublishedRegionIsWantedOnAFreshPhone() {
        assertEquals(catalog.regions.size, RegionPackFiles.missing(catalog, emptySet()).size)
        val have = setOf(RegionPackFiles.fileName(region("cheshire")))
        assertFalse(RegionPackFiles.missing(catalog, have).any { it.id == "cheshire" })
        assertEquals(catalog.regions.size - 1, RegionPackFiles.missing(catalog, have).size)
    }

    @Test fun fileNameCarriesTheRegionsOwnReleaseTag() {
        val cheshire = region("cheshire")
        val f = file(RegionPackFiles.fileName(cheshire))
        assertEquals(cheshire.map.tag, RegionPackFiles.tagOf(f))
        assertEquals("map-data-region-cheshire-r39000000000-a1", RegionPackFiles.tagOf(file(olderCheshire)))
        assertNull(RegionPackFiles.tagOf(file("drivemate-" + "b".repeat(64) + ".pmtiles")))
    }

    @Test fun currentVersionIsPreferredAndAnOlderOneStillDrawsOffline() {
        val cheshire = region("cheshire")
        val old = file(olderCheshire, 5_000L)
        assertEquals(old, RegionPackFiles.choose(cheshire, listOf(old)))
        val current = file(RegionPackFiles.fileName(cheshire), 1_000L)
        assertEquals(current, RegionPackFiles.choose(cheshire, listOf(old, current)))
        // Another region's file is never used for this one.
        assertNull(RegionPackFiles.choose(region("lancashire"), listOf(old, current)))
    }

    @Test fun similarlyNamedRegionsAreNotConfused() {
        // "manchester" must not pick up a "greater-manchester" file, nor the reverse.
        val gm = file(RegionPackFiles.fileName(region("greater-manchester")))
        assertNull(RegionPackFiles.choose(region("manchester"), listOf(gm)))
        val m = file(RegionPackFiles.fileName(region("manchester")))
        assertEquals(gm, RegionPackFiles.choose(region("greater-manchester"), listOf(m, gm)))
    }

    @Test fun olderVersionIsDeletedOnlyOnceItsReplacementIsComplete() {
        val current = RegionPackFiles.fileName(region("cheshire"))
        assertTrue(RegionPackFiles.stale(catalog, setOf(olderCheshire)).isEmpty())
        assertEquals(setOf(olderCheshire), RegionPackFiles.stale(catalog, setOf(olderCheshire, current)))
    }

    @Test fun regionNoLongerPublishedIsDeletedAndOtherFilesAreLeftAlone() {
        val gone = "map-data-region-atlantis-r1-a1~" + "c".repeat(64) + ".pmtiles"
        val other = "drivemate-" + "d".repeat(64) + ".pmtiles"
        assertEquals(setOf(gone), RegionPackFiles.stale(catalog, setOf(gone, other, ".map-update-1.tmp")))
    }

    @Test fun everyPublishedTagIsRecognised() {
        for (r in catalog.regions) {
            assertEquals(r.id, r.map.tag, RegionPackFiles.tagOf(File(RegionPackFiles.fileName(r))))
            assertTrue(r.id, RegionPackFiles.stale(catalog, setOf(RegionPackFiles.fileName(r))).isEmpty())
        }
    }
}
