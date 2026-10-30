package com.drivemate.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** C01.5: one-map preview composition; UK live map/navigation are never rewritten. */
class WorldUkPreviewComposerTest {
    @get:Rule val temp = TemporaryFolder()

    private val bytes = "PMTiles".toByteArray(Charsets.US_ASCII) + byteArrayOf(3) +
        ByteArray(6200) { (it % 107).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun installed(): File =
        temp.newFile("UK world preview.pmtiles").apply { writeBytes(bytes) }

    private fun pointer(): WorldOverview.PointerData {
        val sources = JSONObject()
        for (name in listOf("world-land.geojson", "world-countries.geojson", "world-places.geojson"))
            sources.put(name, JSONObject().put("tag", "map-data-world-overview-r2-a1")
                .put("sha256", "a".repeat(64)).put("bytes", 3200))
        val data = JSONObject()
            .put("schema", 1)
            .put("kind", "low-zoom-world-overview-geometry-only")
            .put("worldwide_routing", false)
            .put("android_app_map_selected", false)
            .put("attribution", "Natural Earth public domain")
            .put("pipeline_sha256", "b".repeat(64))
            .put("tested_sample_count", 6)
            .put("source_assets", sources)
            .put("map", JSONObject().put("tag", "map-data-worldtiles-r3-a1")
                .put("name", "world-overview.pmtiles").put("layout", "world-natural-earth-v1")
                .put("sha256", sha).put("bytes", bytes.size))
        return WorldOverview.parse(data.toString())
    }

    private fun ukStyle(): JSONObject {
        val sources = JSONObject()
            .put("drivemate", JSONObject().put("type", "vector")
                .put("url", "pmtiles://https://github.com/phuk-prog/drivemate-map/releases/download/UK/drivemate-v1.pmtiles"))
            .put("detail", JSONObject().put("type", "vector")
                .put("url", "pmtiles://https://github.com/phuk-prog/drivemate-map/releases/download/UK/detail.pmtiles"))
        return JSONObject()
            .put("version", 8)
            .put("metadata", JSONObject().put("drivemate:layout", "drivemate-v1")
                .put("drivemate:theme", "day"))
            .put("sources", sources)
            .put("layers", JSONArray()
                .put(JSONObject().put("id", "land").put("type", "background")
                    .put("paint", JSONObject().put("background-color", "#d8dbd7")))
                .put(JSONObject().put("id", "water").put("type", "fill")
                    .put("source", "drivemate").put("source-layer", "water")
                    .put("paint", JSONObject().put("fill-color", "#99ddee")))
                .put(JSONObject().put("id", "road-motorway").put("type", "line")
                    .put("source", "drivemate").put("source-layer", "roads")
                    .put("paint", JSONObject().put("line-color", "#ffc322")))
                .put(JSONObject().put("id", "junction-sign").put("type", "symbol")
                    .put("source", "detail").put("source-layer", "signs")
                    .put("layout", JSONObject().put("text-field", "M60"))))
    }

    @Test fun oneStyleDisplaysWorldLandUnderUnchangedUkRoads() {
        val uk = ukStyle()
        val before = uk.toString()
        val result = WorldUkPreviewComposer.compose(uk, pointer(), installed(), false)
        assertEquals(before, uk.toString())
        assertEquals("pmtiles://https://github.com/phuk-prog/drivemate-map/releases/download/UK/drivemate-v1.pmtiles",
            result.getJSONObject("sources").getJSONObject("drivemate").getString("url"))
        assertEquals(3, result.getJSONObject("sources").length())
        assertEquals(5, result.getJSONObject("sources").getJSONObject("world").getInt("maxzoom"))
        assertEquals("Natural Earth (public domain)",
            result.getJSONObject("sources").getJSONObject("world").getString("attribution"))
        assertTrue(result.getJSONObject("sources").getJSONObject("world").getString("url")
            .startsWith("pmtiles://file:///"))
        val layers = result.getJSONArray("layers")
        assertEquals(7, layers.length())
        assertEquals(listOf("land", "world-land", "world-borders", "world-places",
                            "water", "road-motorway", "junction-sign"),
            (0 until layers.length()).map { layers.getJSONObject(it).getString("id") })
        assertEquals("world_land", layers.getJSONObject(1).getString("source-layer"))
        assertEquals("world_borders", layers.getJSONObject(2).getString("source-layer"))
        assertEquals("world_places", layers.getJSONObject(3).getString("source-layer"))
        assertEquals(6.0, layers.getJSONObject(2).getDouble("maxzoom"), 0.01)
        assertEquals(6.0, layers.getJSONObject(3).getDouble("maxzoom"), 0.01)
        assertEquals("roads", layers.getJSONObject(5).getString("source-layer"))
        assertEquals("#ffc322", layers.getJSONObject(5).getJSONObject("paint")
            .getString("line-color"))
        assertEquals("detail", layers.getJSONObject(6).getString("source"))
        val metadata = result.getJSONObject("metadata")
        assertTrue(metadata.getBoolean("drivemate:world_preview_only"))
        assertFalse(metadata.getBoolean("drivemate:worldwide_routing"))
        assertFalse(metadata.getBoolean("drivemate:worldwide_road_data_available"))
        assertTrue(metadata.getBoolean("drivemate:uk_driving_style_untouched"))
        assertEquals("drivemate-v1", metadata.getString("drivemate:layout"))
    }

    @Test fun daytimeAndNighttimeWorldBackgroundChangeWithoutRewritingUk() {
        val file = installed()
        val uk = ukStyle()
        val day = WorldUkPreviewComposer.compose(uk, pointer(), file, false)
        val night = WorldUkPreviewComposer.compose(uk, pointer(), file, true)
        assertNotEquals(day.getJSONArray("layers").getJSONObject(0)
            .getJSONObject("paint").getString("background-color"),
            night.getJSONArray("layers").getJSONObject(0)
                .getJSONObject("paint").getString("background-color"))
        assertEquals("day", uk.getJSONObject("metadata").getString("drivemate:theme"))
    }

    @Test fun missingOrCorruptWorldFileKeepsOriginalUkStyleIntact() {
        val uk = ukStyle()
        val file = installed()
        val preserved = uk.toString()
        file.writeBytes(bytes.copyOf().apply { this[100] = 43 })
        assertEquals(preserved, WorldUkPreviewComposer.composeOrUk(uk, pointer(), file, false).toString())
        assertEquals(preserved, WorldUkPreviewComposer.composeOrUk(uk, pointer(), null, false).toString())
        assertEquals(preserved, WorldUkPreviewComposer.composeOrUk(uk, null, file, false).toString())
        assertEquals(preserved, uk.toString())
        try {
            WorldUkPreviewComposer.compose(uk, pointer(), file, false)
            fail("Corrupt PMTiles must not be accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun incompleteUkSourceAndFakeOriginalStylesAreRejected() {
        val file = installed()
        val base = ukStyle()
        val foreign = JSONObject(base.toString())
        foreign.getJSONObject("sources").put("world", JSONObject().put("type", "vector"))
        assertTrue(runCatching {
            WorldUkPreviewComposer.compose(foreign, pointer(), file, false)
        }.isFailure)
        val oldLayout = JSONObject(base.toString())
        oldLayout.getJSONObject("metadata").put("drivemate:layout", "legacy")
        assertTrue(runCatching {
            WorldUkPreviewComposer.compose(oldLayout, pointer(), file, false)
        }.isFailure)
        val noRoad = JSONObject(base.toString())
        val ls = noRoad.getJSONArray("layers")
        ls.getJSONObject(2).put("source-layer", "unsupported-road-source")
        assertTrue(runCatching { WorldUkPreviewComposer.compose(noRoad, pointer(), file, false) }.isFailure)
        val dup = JSONObject(base.toString())
        dup.getJSONArray("layers").getJSONObject(2).put("id", "water")
        assertTrue(runCatching { WorldUkPreviewComposer.compose(dup, pointer(), file, false) }.isFailure)
    }
}
