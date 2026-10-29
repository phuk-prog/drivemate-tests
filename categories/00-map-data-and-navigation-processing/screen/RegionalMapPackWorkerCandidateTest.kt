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
import com.drivemate.app.dmn.DmnPack
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
 * CAT1 candidate paired with frozen PR #68 RED scenario. One genuine publisher
 * Stockport PMTiles + one genuine indexed DMN road piece (the 772-piece index
 * is purposefully subset for a bounded emulator run). Actual production
 * MapPack.Worker and DmnTiles.fetchAll/Http.download do all the installing.
 * Not a full-UK install, route, screen, speech or release acceptance.
 */
@RunWith(AndroidJUnit4::class)
class RegionalMapPackWorkerCandidateTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = inst.targetContext.applicationContext as Application

    private fun sha(data: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(data).joinToString("") { "%02x".format(it) }
    private fun fileSha(file: File): String = file.inputStream().use { stream ->
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
    private fun mapField(name: String) =
        MapData::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun graphField(name: String) =
        DmnTiles::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun minimalWeeklySnapshot(): Any {
        val tags=Class.forName("com.drivemate.app.data.MapData\$ReleaseTags")
            .declaredConstructors.first { it.parameterTypes.size == 4 }
            .apply { isAccessible = true }.newInstance("map-data-uk",null,null,"map-data-uk")
        return Class.forName("com.drivemate.app.data.MapData\$Snapshot")
            .declaredConstructors.first { it.parameterTypes.size == 2 }
            .apply { isAccessible = true }.newInstance(tags,emptyMap<String,Any>())
    }

    @Test fun actualRegionalWorkerInstallsPublishedDmnWithCandidate() {
        val catalogText=inst.context.assets.open("stockport-live-region.json")
            .bufferedReader().use { it.readText() }
        val catalog=PublishedRegionCatalog.parse(catalogText)
        val region=catalog.regions.single()
        assertEquals("stockport",region.id)
        val source=JSONObject(catalogText).getJSONObject("test_source")
        assertEquals("actual_published_catalog_subset",source.getString("kind"))
        val publishedBytes=inst.context.assets.open("cat1-dmn-stockport-index.json").use { it.readBytes() }
        val live=JSONObject(String(publishedBytes,Charsets.UTF_8))
        val tile=live.getJSONObject("tiles").getJSONObject("213_-9")
        val pieceFile=tile.getString("file")
        val release=live.getString("release")
        val pieceBytes=tile.getLong("bytes")
        val pieceSha=tile.getString("sha256")
        assertEquals("DMNPACK2",live.getString("format"))

        val sourceSubset=JSONObject()
            .put("schema",1).put("format","DMNPACK2")
            .put("release",release).put("built",live.optString("built"))
            .put("source",live.optString("source"))
            .put("tiles",JSONObject().put("213_-9",tile))
            .put("test_scope","single_published_piece_bounded_index_subset")
            .toString()
        val oldMap=linkedMapOf<String,Any?>()
        val oldGraph=linkedMapOf<String,Any?>()
        val mapNames=listOf("checking","regionCatalog","activeRegion","snapshot","ukFiles","nextCheck")
        val graphNames=listOf("index","checked","cached","getText")
        mapNames.forEach { oldMap[it]=mapField(it).get(null) }
        graphNames.forEach { oldGraph[it]=graphField(it).get(null) }
        val prefs=app.getSharedPreferences("map_pack",0)
        val wasWanted=prefs.getBoolean("wanted",false)
        val dmn=File(app.noBackupFilesDir,"dmn")
        val points=listOf(LatLon(53.4051,-2.1630),LatLon(53.4106,-2.1591))
        try {
            mapField("checking").setBoolean(null,true)
            MapData.init(app)
            mapField("regionCatalog").set(null,catalog)
            mapField("activeRegion").set(null,region)
            mapField("snapshot").set(null,minimalWeeklySnapshot())
            mapField("ukFiles").set(null,null)
            mapField("nextCheck").setLong(null,Long.MAX_VALUE)
            assertTrue(MapData.isRegionalSnapshot())
            assertEquals(emptySet<String>(),MapData.publishedNames())
            graphField("index").set(null,null)
            graphField("checked").setLong(null,0L)
            graphField("cached").set(null,null)
            // Only the index is bounded: the actual immutable published road
            // piece is downloaded over HTTPS using the production downloader.
            graphField("getText").set(null,({ url: String ->
                assertTrue("Wrong published index requested",url.endsWith("/dmn-uk/dmn-uk.json"))
                sourceSubset
            } as (String)->String))
            dmn.deleteRecursively()
            assertFalse(File(dmn,"dmn-uk.json").exists())
            prefs.edit().putBoolean("wanted",true).commit()
            val worker=TestListenableWorkerBuilder<MapPack.Worker>(app).build()
            val result=runBlocking { worker.doWork() }
            assertTrue("Candidate production worker must finish",result is ListenableWorker.Result.Success)

            val display=requireNotNull(RegionPacks.local(region)) {
                "Verified Stockport display region not installed"
            }
            assertEquals(region.map.bytes,display.length())
            assertEquals(region.map.sha256,fileSha(display))
            val expected=File(dmn,pieceFile)
            assertTrue("Published DMN tile missing",expected.isFile)
            assertEquals(pieceBytes,expected.length())
            assertEquals(pieceSha,fileSha(expected))
            assertEquals(pieceSha,File(dmn,pieceFile+".sha256").readText())
            assertTrue(File(dmn,"dmn-uk.json").isFile)
            assertEquals(DmnTiles.Offline.READY,DmnTiles.offline(dmn,points))
            val pack=expected.inputStream().use { DmnPack.read(it) }
            assertTrue(pack.nodeCount > 0)
            assertTrue(pack.edgeCount > 0)
            assertNotNull("Production router did not load installed tile",DmnTiles.router(dmn,points))

            val evidence=JSONObject()
                .put("schema",1).put("issue","CAT1-REGIONAL-MAP-WORKER-GREEN-01")
                .put("scope","same_stockport_region_real_worker_single_real_dmn_piece_candidate")
                .put("region_id","stockport")
                .put("published_catalog_sha256",source.getString("catalog_sha256"))
                .put("display_installed_sha256",fileSha(display))
                .put("display_installed_bytes",display.length())
                .put("full_published_index_sha256",sha(publishedBytes))
                .put("published_dmn_release",release)
                .put("published_piece_file",pieceFile)
                .put("published_piece_sha256",pieceSha)
                .put("published_piece_bytes",pieceBytes)
                .put("installed_dmn_index_scope","single_tile_bounded_subset")
                .put("routing_index_installed",true)
                .put("one_published_dmn_piece_installed",true)
                .put("dmn_offline_for_two_points","READY")
                .put("worker_result","SUCCESS")
                .put("node_count",pack.nodeCount).put("edge_count",pack.edgeCount)
                .put("whole_uk_install_verified",false)
                .put("offline_route_computed",false)
                .put("screen_or_speech_verified",false)
                .put("independent_legal_truth_verified",false)
                .put("release_allowed",false)
            val out=File(requireNotNull(app.externalMediaDirs.firstOrNull()),"additional_test_output")
                .apply { mkdirs() }
            File(out,"cat1-regional-worker-green-receipt.json").writeText(evidence.toString())
        } finally {
            oldMap.forEach { (n,value)->mapField(n).set(null,value) }
            oldGraph.forEach { (n,value)->graphField(n).set(null,value) }
            prefs.edit().putBoolean("wanted",wasWanted).commit()
            RegionPacks.deleteAll(app)
            dmn.deleteRecursively()
        }
    }
}
