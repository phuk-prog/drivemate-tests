package com.drivemate.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The UK join (owner, 26 Oct 2026: "Join regions"): guidance squares come from one UK set joined
 * from every region, so a route into the next county or a square on a county line is complete.
 */
class UkJoinTest {
    private val sha = "a".repeat(64)
    private fun manifest(tag: String = "map-data-ukjoin-r7-a1", kind: String = "uk-join") =
        """{"schema":1,"kind":"$kind","regions":{"cheshire":"x"},"files":{
           "lanes-106_-5.json":{"sha256":"$sha","bytes":10,"tag":"$tag","sources":"s"},
           "road-details-106_-5.json.gz":{"sha256":"$sha","bytes":11,"tag":"$tag-part002"},
           "LICENSE-ODbL.txt":{"sha256":"$sha","bytes":12,"tag":"$tag"}}}"""
    private fun pointerFor(text: String, tag: String = "map-data-ukjoin-r7-a1") =
        """{"schema":1,"built_utc":"2026-10-27T00:10:00+00:00","regions":{},"manifest":{"tag":"$tag",
           "sha256":"${MapData.sha256(text.toByteArray())}","bytes":${text.toByteArray().size}}}"""

    @Test fun guidanceSquaresAndUkWideFilesAreJoined() {
        for (name in listOf("lanes-106_-5.json", "limits-106_-5.json", "roadinfo-105_-6.json",
                "road-details-106_-5.json.gz", "places-211_-10.json.gz", "cameras-uk.json", "charge-zones-uk.json"))
            assertTrue(name, UkJoin.joinable(name))
        for (name in listOf("drivemate-v1.pmtiles", "manifest.json", "feature-packs-105_-5.jsonl.gz", "LICENSE-ODbL.txt"))
            assertFalse(name, UkJoin.joinable(name))
    }

    @Test fun manifestMustBeTheOneThePointerNames() {
        val text = manifest()
        val files = UkJoin.files(text, UkJoin.pointer(pointerFor(text)))
        assertEquals(setOf("lanes-106_-5.json", "road-details-106_-5.json.gz"), files.keys)
        assertEquals("map-data-ukjoin-r7-a1-part002", files.getValue("road-details-106_-5.json.gz").tag)
        val other = UkJoin.pointer(pointerFor(manifest(tag = "map-data-ukjoin-r8-a1")))
        assertTrue("a changed manifest is refused", runCatching { UkJoin.files(text, other) }.isFailure)
    }

    @Test fun onlyUkJoinReleasesAreAccepted() {
        val region = manifest(tag = "map-data-region-cheshire-r1-a1")
        assertTrue(runCatching { UkJoin.files(region, UkJoin.pointer(pointerFor(region))) }.isFailure)
        val notJoin = manifest(kind = "region")
        assertTrue(runCatching { UkJoin.files(notJoin, UkJoin.pointer(pointerFor(notJoin))) }.isFailure)
        assertTrue("the pointer names the first release, not a shard",
            runCatching { UkJoin.pointer(pointerFor(manifest(), tag = "map-data-ukjoin-r7-a1-part002")) }.isFailure)
        assertTrue(runCatching { UkJoin.pointer(pointerFor(manifest(), tag = "map-data-region-x-r1-a1")) }.isFailure)
    }

    @Test fun ukSetWinsForGuidanceRegionKeepsTheRest() {
        val uk = mapOf("lanes-106_-5.json" to MapData.Asset(sha, 1, "map-data-ukjoin-r7-a1"))
        val regional = mapOf(
            "lanes-106_-5.json" to MapData.Asset(sha, 2, "map-data-region-cheshire-r1-a1"),
            "limits-106_-5.json" to MapData.Asset(sha, 3, "map-data-region-cheshire-r1-a1"),
            "drivemate-v1.pmtiles" to MapData.Asset(sha, 4, "map-data-region-cheshire-r1-a1"))
        assertEquals(1L, UkJoin.pick("lanes-106_-5.json", uk, regional)!!.bytes)
        assertEquals("not joined yet: the region's own", 3L, UkJoin.pick("limits-106_-5.json", uk, regional)!!.bytes)
        assertEquals(4L, UkJoin.pick("drivemate-v1.pmtiles", uk, regional)!!.bytes)
        assertEquals("no join published: as before", 2L, UkJoin.pick("lanes-106_-5.json", null, regional)!!.bytes)
        assertEquals(1L, UkJoin.pick("lanes-106_-5.json", uk, null)!!.bytes)
        assertNull(UkJoin.pick("lanes-1_1.json", uk, regional))
    }

    private fun mapPointer(tag: String = "map-data-ukmap-r9-a1", name: String = "drivemate-v1.pmtiles", layout: String = "drivemate-v1") =
        """{"schema":1,"regions":{},"map":{"tag":"$tag","name":"$name","layout":"$layout","sha256":"$sha","bytes":900000000}}"""

    /** The one UK map: only a uk-map release in the regional layout. */
    @Test fun ukMapPointerIsStrict() {
        val map = UkJoin.map(mapPointer())
        assertEquals("map-data-ukmap-r9-a1", map.tag)
        assertEquals(900_000_000L, map.bytes)
        assertTrue(UkJoin.isUkMap("map-data-ukmap-r9-a1"))
        assertFalse(UkJoin.isUkMap("map-data-region-cheshire-r1-a1"))
        for (bad in listOf(mapPointer(tag = "map-data-region-x-r1-a1"), mapPointer(name = "drivemate.pmtiles"),
                mapPointer(layout = "legacy"), mapPointer(tag = "map-data-ukmap-r9-a1-part002")))
            assertTrue(bad, runCatching { UkJoin.map(bad) }.isFailure)
    }
}
