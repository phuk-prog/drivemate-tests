package com.drivemate.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic bounded fixtures: no real journey, location history or access credential. */
class PublishedRegionCatalogTest {
    private fun fixture(id: String = "town-a", bounds: List<Double> = listOf(0.0, 0.0, 1.0, 1.0)): JSONObject {
        val tag = "map-data-region-$id-test"
        return JSONObject().put("id", id).put("name", "Synthetic town").put("bounds", JSONArray(bounds))
            .put("manifest", JSONObject().put("tag", tag).put("sha256", "a".repeat(64)).put("bytes", 200))
            .put("map", JSONObject().put("tag", tag).put("sha256", "b".repeat(64)).put("bytes", 1000)
                .put("name", "drivemate-v1.pmtiles").put("layout", "drivemate-v1"))
    }
    private fun catalog(vararg regions: JSONObject): String = JSONObject().put("schema", 1).put("coverage", "partial")
        .put("regions", JSONObject().also { values -> regions.forEach { values.put(it.getString("id"), it) } }).toString()

    @Test fun selectionDoesNotInventCoverageOutsidePublishedBounds() {
        val parsed = PublishedRegionCatalog.parse(catalog(fixture()))
        assertNotNull(parsed.select(0.5, 0.5))
        assertNull(parsed.select(2.0, 2.0))
        assertNull(parsed.select(Double.NaN, 0.5))
    }

    @Test fun overlappingRegionsChooseSmallestContainingAreaDeterministically() {
        val parsed = PublishedRegionCatalog.parse(catalog(fixture("county", listOf(-1.0, -1.0, 2.0, 2.0)), fixture()))
        assertEquals("town-a", parsed.select(0.5, 0.5)?.id)
        assertEquals("county", parsed.select(1.5, 1.5)?.id)
    }

    @Test fun rejectsForeignRegionTagUnsafeBoundsAndUnsupportedArchive() {
        val cases = listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("map").put("tag", "map-data-region-other-test") },
            { it.put("bounds", JSONArray(listOf(1.0, 0.0, 0.0, 1.0))) },
            { it.getJSONObject("map").put("name", "../../file") },
            { it.getJSONObject("map").put("layout", "unknown") },
            { it.getJSONObject("manifest").put("bytes", 2.5) },
            { it.getJSONObject("map").put("bytes", 2_000_000_000L) },
        )
        cases.forEach { mutate ->
            val region = fixture().also(mutate)
            assertThrows(IllegalArgumentException::class.java) { PublishedRegionCatalog.parse(catalog(region)) }
        }
    }

    private fun verifiedFixture(assetTag: String = "map-data-region-town-a-test"): Pair<PublishedRegionCatalog.Region, String> {
        val json = JSONObject().put("schema", 1).put("files", JSONObject().put("drivemate-v1.pmtiles",
            JSONObject().put("tag", "map-data-region-town-a-test").put("sha256", "b".repeat(64)).put("bytes", 1000))
            .put("lanes-0_0.json", JSONObject().put("tag", assetTag).put("sha256", "c".repeat(64)).put("bytes", 20))).toString()
        val region = fixture().also {
            it.getJSONObject("manifest").put("bytes", json.toByteArray().size)
                .put("sha256", MapData.sha256(json.toByteArray()))
        }
        return PublishedRegionCatalog.parse(catalog(region)).regions.single() to json
    }

    @Test fun verifiesCatalogToManifestToMapChainAndAllowsSameRegionReuse() {
        val (region, text) = verifiedFixture("map-data-region-town-a-previous")
        val files = MapData.regionalFiles(region, text)
        assertEquals("map-data-region-town-a-previous", files["lanes-0_0.json"]?.tag)
    }

    @Test fun damagedManifestAndCrossRegionCompanionsAreRejected() {
        val (region, text) = verifiedFixture()
        assertThrows(IllegalArgumentException::class.java) { MapData.regionalFiles(region, text + " ") }
        val (otherRegion, crossRegion) = verifiedFixture("map-data-region-other-test")
        assertThrows(IllegalArgumentException::class.java) { MapData.regionalFiles(otherRegion, crossRegion) }
    }

    @Test fun mapEntryMustMatchThePinnedManifest() {
        val (region, text) = verifiedFixture()
        assertThrows(IllegalArgumentException::class.java) {
            MapData.regionalFiles(region.copy(map = region.map.copy(sha256 = "d".repeat(64))), text)
        }
    }

    @Test fun catalogCanPinAnUnchangedMapFromAnEarlierSameRegionGeneration() {
        val value = fixture().also { it.getJSONObject("map").put("tag", "map-data-region-town-a-previous") }
        assertEquals("map-data-region-town-a-previous", PublishedRegionCatalog.parse(catalog(value)).regions.single().map.tag)
    }

    @Test fun partialRolloutChecksForNewRegionsSoonerThanCompletedCoverage() {
        assertTrue(MapData.catalogCheckEvery("partial") < MapData.catalogCheckEvery("complete"))
    }
}
