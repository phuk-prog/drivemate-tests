package com.drivemate.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * C01.5: live world pointer selection/install. The UK style must stay unchanged unless a
 * verified world archive from the owner's publisher is installed AND the overview is enabled.
 */
class WorldMapSourceTest {
    @get:Rule val temp = TemporaryFolder()

    private val publisher = "https://github.com/phuk-prog/drivemate-map/releases/download"

    private fun archive(seed: Int): ByteArray =
        "PMTiles".toByteArray(Charsets.US_ASCII) + byteArrayOf(3) + ByteArray(6100) { ((it + seed) % 113).toByte() }

    private fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun pointer(tag: String, bytes: ByteArray): String {
        val sources = JSONObject()
        for (name in listOf("world-land.geojson", "world-countries.geojson", "world-places.geojson"))
            sources.put(name, JSONObject().put("tag", "map-data-world-overview-r5-a1")
                .put("sha256", "a".repeat(64)).put("bytes", 3200))
        return JSONObject()
            .put("schema", 1)
            .put("kind", "low-zoom-world-overview-geometry-only")
            .put("worldwide_routing", false)
            .put("android_app_map_selected", false)
            .put("attribution", "Natural Earth public domain")
            .put("pipeline_sha256", "b".repeat(64))
            .put("tested_sample_count", 6)
            .put("source_assets", sources)
            .put("map", JSONObject().put("tag", tag)
                .put("name", "world-overview.pmtiles").put("layout", "world-natural-earth-v1")
                .put("sha256", digest(bytes)).put("bytes", bytes.size))
            .toString()
    }

    /** Fake publisher: serves one pointer and per-URL bytes; records every URL requested. */
    private class Publisher(var pointerText: String, val files: MutableMap<String, ByteArray>) : WorldMapSource.Fetcher {
        val requested = mutableListOf<String>()
        override fun pointer(url: String, maxBytes: Int): String { requested += url; return pointerText }
        override fun archive(url: String, dest: File, maxBytes: Long) {
            requested += url
            val bytes = files[url] ?: throw IOException("404")
            if (bytes.size > maxBytes) throw IOException("Map asset exceeds declared size")
            dest.writeBytes(bytes)
        }
    }

    private fun ukStyle(): JSONObject = JSONObject()
        .put("version", 8)
        .put("metadata", JSONObject().put("drivemate:layout", "drivemate-v1"))
        .put("sources", JSONObject().put("drivemate", JSONObject().put("type", "vector")
            .put("url", "pmtiles://https://github.com/phuk-prog/drivemate-map/releases/download/UK/drivemate-v1.pmtiles")))
        .put("layers", JSONArray()
            .put(JSONObject().put("id", "land").put("type", "background")
                .put("paint", JSONObject().put("background-color", "#d8dbd7")))
            .put(JSONObject().put("id", "water").put("type", "fill")
                .put("source", "drivemate").put("source-layer", "water"))
            .put(JSONObject().put("id", "road-motorway").put("type", "line")
                .put("source", "drivemate").put("source-layer", "roads")))

    private fun url(tag: String) = "$publisher/$tag/world-overview.pmtiles"

    @Test fun disabledOrUninstalledWorldAlwaysKeepsExactUkStyle() {
        val dir = temp.newFolder("world")
        val uk = ukStyle()
        assertEquals(WorldMapSource.Choice.UK_ONLY, WorldMapSource.select(dir, enabled = false).choice)
        assertEquals(WorldMapSource.Choice.UK_ONLY, WorldMapSource.select(dir, enabled = true).choice)
        assertEquals(uk.toString(), WorldMapSource.style(uk, dir, enabled = true, night = false).toString())
    }

    @Test fun livePointerInstallsVerifiedArchiveAndComposesWorldUnderUkRoads() {
        val dir = temp.newFolder("world")
        val bytes = archive(1)
        val tag = "map-data-worldtiles-r41178246944-a1"
        val pub = Publisher(pointer(tag, bytes), mutableMapOf(url(tag) to bytes))
        assertEquals(tag, WorldMapSource.refresh(dir, publisher, pub))
        assertEquals(listOf("$publisher/map-data-worldtiles/worldtiles.json", url(tag)), pub.requested)

        val chosen = WorldMapSource.select(dir, enabled = true)
        assertEquals(WorldMapSource.Choice.WORLD_AND_UK, chosen.choice)
        // Still off unless explicitly enabled: production driving map is untouched.
        assertEquals(WorldMapSource.Choice.UK_ONLY, WorldMapSource.select(dir, enabled = false).choice)

        val style = WorldMapSource.style(ukStyle(), dir, enabled = true, night = false)
        assertTrue(style.getJSONObject("sources").has("world"))
        val ids = (0 until style.getJSONArray("layers").length()).map { style.getJSONArray("layers").getJSONObject(it).getString("id") }
        assertTrue(ids.indexOf("world-land") < ids.indexOf("road-motorway"))
        assertTrue(style.getJSONObject("metadata").getBoolean("drivemate:uk_driving_style_untouched"))

        // An unchanged pointer does not re-download the archive.
        pub.requested.clear()
        WorldMapSource.refresh(dir, publisher, pub)
        assertEquals(listOf("$publisher/map-data-worldtiles/worldtiles.json"), pub.requested)
    }

    @Test fun corruptOrMissingUpdateKeepsLastGoodWorldPair() {
        val dir = temp.newFolder("world")
        val good = archive(2)
        val oldTag = "map-data-worldtiles-r10-a1"
        val pub = Publisher(pointer(oldTag, good), mutableMapOf(url(oldTag) to good))
        WorldMapSource.refresh(dir, publisher, pub)

        val next = archive(3)
        val newTag = "map-data-worldtiles-r11-a1"
        pub.pointerText = pointer(newTag, next)
        pub.files[url(newTag)] = next.copyOf().also { it[100] = (it[100] + 1).toByte() } // tampered
        assertTrue(runCatching { WorldMapSource.refresh(dir, publisher, pub) }.isFailure)
        pub.files.remove(url(newTag)) // interrupted / not yet uploaded
        assertTrue(runCatching { WorldMapSource.refresh(dir, publisher, pub) }.isFailure)

        val chosen = WorldMapSource.select(dir, enabled = true)
        assertEquals(WorldMapSource.Choice.WORLD_AND_UK, chosen.choice)
        assertEquals(oldTag, chosen.world!!.archive.tag)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") || it.name.endsWith(".part") })

        // A successful later update replaces the pair and removes only the superseded archive.
        pub.files[url(newTag)] = next
        assertEquals(newTag, WorldMapSource.refresh(dir, publisher, pub))
        assertEquals(1, dir.listFiles()!!.count { it.name.endsWith(".pmtiles") })
        assertEquals(newTag, WorldMapSource.select(dir, enabled = true).world!!.archive.tag)
    }

    @Test fun tamperedLocalArchiveFallsBackToUkOnly() {
        val dir = temp.newFolder("world")
        val bytes = archive(4)
        val tag = "map-data-worldtiles-r12-a1"
        WorldMapSource.refresh(dir, publisher, Publisher(pointer(tag, bytes), mutableMapOf(url(tag) to bytes)))
        val file = dir.listFiles()!!.single { it.name.endsWith(".pmtiles") }
        file.writeBytes(bytes.copyOf().also { it[200] = (it[200] + 1).toByte() })
        assertEquals(WorldMapSource.Choice.UK_ONLY, WorldMapSource.select(dir, enabled = true).choice)
        val uk = ukStyle()
        assertEquals(uk.toString(), WorldMapSource.style(uk, dir, enabled = true, night = true).toString())
    }

    @Test fun foreignPublisherOrUnsafePointerIsNeverDownloaded() {
        val dir = temp.newFolder("world")
        val bytes = archive(5)
        val tag = "map-data-worldtiles-r13-a1"
        val pub = Publisher(pointer(tag, bytes), mutableMapOf(url(tag) to bytes))
        assertTrue(runCatching { WorldMapSource.refresh(dir, "https://example.com/releases/download", pub) }.isFailure)
        assertTrue(pub.requested.isEmpty())

        val routing = JSONObject(pointer(tag, bytes)).put("worldwide_routing", true).toString()
        val unsafe = Publisher(routing, mutableMapOf(url(tag) to bytes))
        assertTrue(runCatching { WorldMapSource.refresh(dir, publisher, unsafe) }.isFailure)
        assertFalse(unsafe.requested.any { it.endsWith(".pmtiles") })
        assertEquals(WorldMapSource.Choice.UK_ONLY, WorldMapSource.select(dir, enabled = true).choice)
    }
}
