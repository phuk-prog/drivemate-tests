package com.drivemate.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** C01.5: actual Android map-reader contract; global data must never replace UK driving. */
class WorldOverviewTest {
    @get:Rule val temp = TemporaryFolder()

    private val sha = "a".repeat(64)
    private val release = "map-data-worldtiles-r4111-a1"
    private val source = "map-data-world-overview-r4100-a1"
    private val publisher = "https://github.com/phuk-prog/drivemate-map/releases/download"

    private fun archiveBytes(): ByteArray =
        "PMTiles".toByteArray(Charsets.US_ASCII) + byteArrayOf(3) + ByteArray(6000) { (it % 123).toByte() }

    private fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun validPointer(mapSha: String, size: Long): JSONObject {
        val sources = JSONObject()
        for (name in listOf("world-land.geojson", "world-countries.geojson", "world-places.geojson"))
            sources.put(name, JSONObject()
                .put("tag", source).put("sha256", sha).put("bytes", 3200))
        return JSONObject()
            .put("schema", 1)
            .put("kind", "low-zoom-world-overview-geometry-only")
            .put("worldwide_routing", false)
            .put("android_app_map_selected", false)
            .put("attribution", "Natural Earth public domain")
            .put("pipeline_sha256", sha)
            .put("tested_sample_count", 6)
            .put("source_assets", sources)
            .put("map", JSONObject()
                .put("tag", release)
                .put("name", "world-overview.pmtiles")
                .put("layout", "world-natural-earth-v1")
                .put("sha256", mapSha)
                .put("bytes", size))
    }

    private fun rejected(root: JSONObject): Boolean =
        runCatching { WorldOverview.parse(root.toString()) }.isFailure

    @Test fun onlyExactWorldPublicationIsAcceptedAndUkMapIsSeparate() {
        val file = archiveBytes()
        val pointer = WorldOverview.parse(validPointer(digest(file), file.size.toLong()).toString())
        assertEquals(release, pointer.archive.tag)
        assertEquals("world-overview.pmtiles", pointer.archive.name)
        assertEquals("$publisher/$release/world-overview.pmtiles",
            WorldOverview.archiveUrl(pointer, publisher))
        assertEquals("map-data-worldtiles/worldtiles.json", WorldOverview.POINTER)
        assertFalse("A world overview release is not an installed countrywide UK map",
            UkJoin.isUkMap(pointer.archive.tag))
        assertThrows(IllegalArgumentException::class.java) {
            WorldOverview.archiveUrl(pointer, "https://unverified.example/download")
        }
    }

    @Test fun strictPointerRefusesFakeFullWorldRoutingOrOtherPublishers() {
        val bytes = archiveBytes()
        val base = validPointer(digest(bytes), bytes.size.toLong())
        for (name in listOf("worldwide_routing", "android_app_map_selected")) {
            val modified = JSONObject(base.toString()).put(name, true)
            assertTrue(name, rejected(modified))
        }
        assertTrue(rejected(JSONObject(base.toString()).put("kind", "worldwide-road-routing")))
        assertTrue(rejected(JSONObject(base.toString()).put("tested_sample_count", 0)))
        assertTrue(rejected(JSONObject(base.toString()).put("schema", 2)))
        assertTrue(rejected(JSONObject(base.toString()).put("attribution", "Unknown")))
        assertTrue(rejected(JSONObject(base.toString()).put("pipeline_sha256", "broken")))
        assertTrue(rejected(JSONObject(base.toString()).put("map",
            JSONObject(base.getJSONObject("map").toString()).put("tag", "map-data-ukmap-r55-a1"))))
        assertTrue(rejected(JSONObject(base.toString()).put("map",
            JSONObject(base.getJSONObject("map").toString()).put("layout", "drivemate-v1"))))
        val incomplete = JSONObject(base.toString())
        incomplete.getJSONObject("source_assets").remove("world-countries.geojson")
        assertTrue(rejected(incomplete))
        val unexpected = JSONObject(base.toString())
        unexpected.getJSONObject("source_assets").put("unverified-extra.geojson",
            JSONObject().put("tag", source).put("sha256", sha).put("bytes", 3200))
        assertTrue("Foreign source layers cannot sneak into a world publication", rejected(unexpected))
        val foreign = JSONObject(base.toString())
        foreign.getJSONObject("source_assets").getJSONObject("world-land.geojson").put("tag", release)
        assertTrue(rejected(foreign))
    }

    @Test fun schemaAndSixContinentReceiptRequireExactJsonIntegers() {
        val b = archiveBytes()
        val valid = validPointer(digest(b), b.size.toLong())
        for (wrong in listOf("6", 6.5, true, JSONObject.NULL)) {
            assertTrue("Coerced six-continent receipt must not pass: $wrong",
                rejected(JSONObject(valid.toString()).put("tested_sample_count", wrong)))
        }
        for (wrong in listOf("1", 1.5, true, JSONObject.NULL)) {
            assertTrue("Coerced schema must not pass: $wrong",
                rejected(JSONObject(valid.toString()).put("schema", wrong)))
        }
        assertFalse(rejected(valid))
    }

    @Test fun worldSourceAssetsCannotMixImmutableReleaseGenerations() {
        val b = archiveBytes()
        val mixed = validPointer(digest(b), b.size.toLong())
        mixed.getJSONObject("source_assets").getJSONObject("world-land.geojson")
            .put("tag", "map-data-world-overview-r4101-a1")
        assertTrue("One world candidate must not combine independently dated source snapshots",
            rejected(mixed))
    }

    @Test fun incompleteOrOversizedPointerIsRejectedWithoutUsingNetwork() {
        val b = archiveBytes()
        val valid = validPointer(digest(b), b.size.toLong())
        assertTrue(rejected(JSONObject(valid.toString()).put("map",
            JSONObject(valid.getJSONObject("map").toString()).put("bytes", 0))))
        val giant = valid.toString() + " ".repeat(65536)
        assertTrue(runCatching { WorldOverview.parse(giant) }.isFailure)
        assertTrue(rejected(JSONObject(valid.toString()).put("map",
            JSONObject(valid.getJSONObject("map").toString()).put("sha256", "unknown"))))
    }

    @Test fun fileNeedsMatchingHashSizeAndPmtilesHeaderBeforePreview() {
        val b = archiveBytes()
        val file = File(temp.root, "candidate world overview.pmtiles").apply { writeBytes(b) }
        val pointer = WorldOverview.parse(validPointer(digest(b), b.size.toLong()).toString())
        assertTrue(WorldOverview.verifiedFile(pointer, file))
        val broken = b.copyOf()
        broken[128] = (broken[128] + 1).toByte()
        file.writeBytes(broken)
        assertFalse(WorldOverview.verifiedFile(pointer, file))
        assertTrue(runCatching { WorldOverview.previewStyle(pointer, file, false) }.isFailure)
        file.writeBytes(b)
        assertTrue(WorldOverview.verifiedFile(pointer, file))
        file.writeBytes(ByteArray(b.size))
        assertFalse("Correct length alone must never be accepted", WorldOverview.verifiedFile(pointer, file))
        assertFalse(WorldOverview.verifiedFile(pointer, File(temp.root, "missing.pmtiles")))
    }

    @Test fun previewStyleUsesLocalWorldLayersAndNoUkGuidanceOrRoadClaims() {
        val b = archiveBytes()
        val file = File(temp.root, "world preview.pmtiles").apply { writeBytes(b) }
        val pointer = WorldOverview.parse(validPointer(digest(b), b.size.toLong()).toString())
        val day = WorldOverview.previewStyle(pointer, file, night = false)
        val night = WorldOverview.previewStyle(pointer, file, night = true)
        assertEquals(8, day.getInt("version"))
        assertEquals(1, day.getJSONObject("sources").length())
        val source = day.getJSONObject("sources").getJSONObject("world")
        assertEquals("vector", source.getString("type"))
        assertTrue(source.getString("url").startsWith("pmtiles://file:///"))
        assertTrue("Spaces in file path should be URI escaped", source.getString("url").contains("%20"))
        assertEquals("Natural Earth (public domain)", source.getString("attribution"))
        val layers = day.getJSONArray("layers")
        assertEquals(listOf("world-background", "world-land", "world-borders", "world-places"),
            (0 until layers.length()).map { layers.getJSONObject(it).getString("id") })
        assertEquals("world_land", layers.getJSONObject(1).getString("source-layer"))
        assertEquals("world_borders", layers.getJSONObject(2).getString("source-layer"))
        assertEquals("world_places", layers.getJSONObject(3).getString("source-layer"))
        assertFalse(day.getJSONObject("metadata").getBoolean("drivemate:worldwide_routing"))
        assertEquals(true, day.getJSONObject("metadata").getBoolean("drivemate:uk_navigation_unchanged"))
        assertTrue(day.toString().contains("world-preview").not())
        assertTrue(day.getJSONObject("sources").has("drivemate").not())
        assertFalse(day.getJSONArray("layers").toString().contains("road"))
        assertFalse(night.getJSONArray("layers").getJSONObject(0).getJSONObject("paint")
            .getString("background-color") == day.getJSONArray("layers")
            .getJSONObject(0).getJSONObject("paint").getString("background-color"))
    }
}
