package com.drivemate.tests.cat00

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.drivemate.app.data.MapData
import com.drivemate.app.data.MapPack
import com.drivemate.app.data.PublishedRegionCatalog
import com.drivemate.app.data.RegionPacks
import com.drivemate.app.dmn.DmnTiles
import com.drivemate.app.nav.LatLon
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Category 1 bounded RED diagnostic of the real MapPack.Worker orchestration.
 * One genuine published Stockport display region; weekly companion manifest is
 * deliberately empty to avoid downloading nationwide data during this test.
 * The production worker, its display downloader and WholeUk.fetchAll are unmodified.
 *
 * A passing test proves the EXISTING regional skip is observable, not that
 * DMN installation or complete UK navigation succeeds.
 */
@RunWith(AndroidJUnit4::class)
class RegionalMapPackWorkerPretest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private fun bytesSha(data: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private fun sha(file: File) = bytesSha(file.readBytes())
    private fun state(name: String) = MapData::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun minimalWeeklySnapshot(): Any {
        val releaseTags = Class.forName("com.drivemate.app.data.MapData\$ReleaseTags")
            .declaredConstructors.first { it.parameterTypes.size == 4 }
            .apply { isAccessible = true }
            .newInstance("map-data-uk", null, null, "map-data-uk")
        return Class.forName("com.drivemate.app.data.MapData\$Snapshot")
            .declaredConstructors.first { it.parameterTypes.size == 2 }
            .apply { isAccessible = true }
            .newInstance(releaseTags, emptyMap<String, Any>())
    }

    @Test fun actualRegionalMapPackWorkerCompletesWithoutInstallingPublishedDmn() {
        val catalogData = instrumentation.context.assets.open("stockport-live-region.json")
            .bufferedReader().use { it.readText() }
        val fixture = JSONObject(catalogData)
        val source = fixture.getJSONObject("test_source")
        assertEquals("actual_published_catalog_subset", source.getString("kind"))
        val catalog = PublishedRegionCatalog.parse(catalogData)
        val region = catalog.regions.single()
        assertEquals("stockport", region.id)
        val dmnIndexData = instrumentation.context.assets.open("cat1-dmn-stockport-index.json")
            .use { it.readBytes() }
        val dmnIndex = JSONObject(String(dmnIndexData, Charsets.UTF_8))
        assertEquals("DMNPACK2", dmnIndex.getString("format"))
        assertTrue(dmnIndex.getJSONObject("tiles").has("213_-9"))

        val saved = linkedMapOf<String, Any?>()
        val fields = listOf("checking", "regionCatalog", "activeRegion", "snapshot", "ukFiles", "nextCheck")
        fields.forEach { saved[it] = state(it).get(null) }
        val prefs = app.getSharedPreferences("map_pack", 0)
        val priorWanted = prefs.getBoolean("wanted", false)
        val dmn = File(app.noBackupFilesDir, "dmn")
        try {
            state("checking").setBoolean(null, true) // prevent unrelated background publication refresh
            MapData.init(app)
            state("regionCatalog").set(null, catalog)
            state("activeRegion").set(null, region)
            state("snapshot").set(null, minimalWeeklySnapshot())
            state("ukFiles").set(null, null)
            state("nextCheck").setLong(null, Long.MAX_VALUE)
            assertTrue(MapData.isRegionalSnapshot())
            assertEquals(emptySet<String>(), MapData.publishedNames())
            dmn.deleteRecursively()
            assertFalse(File(dmn, "dmn-uk.json").exists())
            prefs.edit().putBoolean("wanted", true).commit()
            val worker = TestListenableWorkerBuilder<MapPack.Worker>(app).build()
            val result = runBlocking { worker.doWork() }
            assertTrue("Production regional MapPack.Worker failed", result is ListenableWorker.Result.Success)
            val displayed = requireNotNull(RegionPacks.local(region)) {
                "Production regional display download did not install real Stockport map"
            }
            assertTrue(displayed.isFile)
            assertEquals(region.map.bytes, displayed.length())
            val installedSha = sha(displayed)
            assertEquals(region.map.sha256, installedSha)
            assertFalse("DMN index unexpectedly installed by regional worker",
                File(dmn, "dmn-uk.json").exists())
            assertEquals("Regional worker did not fetch needed published graph",
                DmnTiles.Offline.MISSING,
                DmnTiles.offline(dmn, listOf(LatLon(53.4051, -2.1630), LatLon(53.4106, -2.1591))))
            val receipt = JSONObject()
                .put("schema", 1)
                .put("issue", "CAT1-REGIONAL-MAP-WORKER-RED-01")
                .put("scope", "real_worker_one_published_stockport_display_bounded_empty_weekly_companions")
                .put("published_catalog_sha256", source.getString("catalog_sha256"))
                .put("region_id", region.id)
                .put("display_installed_sha256", installedSha)
                .put("display_installed_bytes", displayed.length())
                .put("published_dmn_index_sha256", bytesSha(dmnIndexData))
                .put("published_dmn_release", dmnIndex.getString("release"))
                .put("regional_snapshot_selected", MapData.isRegionalSnapshot())
                .put("weekly_companion_files_in_bounded_fixture", 0)
                .put("worker_result", "SUCCESS")
                .put("dmn_index_installed_after_worker", false)
                .put("dmn_offline_after_worker", "MISSING")
                .put("observed_bug", "REGIONAL_MAP_WORKER_SUCCESS_WITHOUT_DMN_GRAPH")
                .put("real_world_full_catalog_verified", false)
                .put("offline_navigation_verified", false)
                .put("screen_or_speech_verified", false)
                .put("release_allowed", false)
            val out = File(requireNotNull(app.externalMediaDirs.firstOrNull()),
                "additional_test_output").apply { mkdirs() }
            File(out, "cat1-regional-worker-red-receipt.json").writeText(receipt.toString())
        } finally {
            // Never persist artificial catalogue state or installed display packs.
            for ((name, original) in saved) state(name).set(null, original)
            prefs.edit().putBoolean("wanted", priorWanted).commit()
            RegionPacks.deleteAll(app)
            dmn.deleteRecursively()
        }
    }
}
