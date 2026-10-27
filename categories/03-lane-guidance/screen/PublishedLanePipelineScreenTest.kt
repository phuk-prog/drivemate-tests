package com.drivemate.tests.cat03

import android.app.Application
import android.location.Location
import android.os.SystemClock
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.OwnMap
import com.drivemate.app.data.Route
import com.drivemate.app.data.RouteStep
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import com.drivemate.app.ui.LaneRow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frozen published Great Ancoats Street geometry/tags -> production region loader/parser ->
 * NavigationSession -> real LaneRow. The route has NO injected lane arrows. A continuation
 * marker lies on the source geometry; it is not asserted to be a real junction or legal turn.
 * This tests the pipeline, not road-law truth, online downloading or a complete physical drive.
 */
@RunWith(AndroidJUnit4::class)
class PublishedLanePipelineScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun call(session: NavigationSession, name: String, vararg args: Any?): Any? =
        NavigationSession::class.java.declaredMethods.single {
            it.name == name && it.parameterCount == args.size
        }.apply { isAccessible = true }.invoke(session, *args)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun publishedGreatAncoatsSourceReachesSessionAndVisibleLaneStrip() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val raw = instrumentation.context.assets.open("cat3-published-manchester-lanes-3.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        assertEquals("dcaff102f855531e6bce98b77d8a2068684296590f20c032240a4eb8521e5685", root.getString("source_sha256"))
        val ways = root.getJSONArray("ways")
        val way = (0 until ways.length()).map { ways.getJSONObject(it) }.single { it.getLong("id") == 930354L }
        val geometry = way.getJSONArray("g")
        val points = (0 until geometry.length()).map { i ->
            geometry.getJSONArray(i).let { LatLon(it.getDouble(1), it.getDouble(0)) }
        }
        val length = points.zipWithNext().sumOf { (a,b) -> Geo.distance(a,b) }
        val marker = points[points.lastIndex - 1]
        val firstLength = points.take(points.lastIndex).zipWithNext().sumOf { (a,b) -> Geo.distance(a,b) }
        val route = Route(points, length, 30.0, listOf(
            RouteStep("Continue", "depart", "straight", "Great Ancoats Street", firstLength, points.first(), leftDriving = true),
            RouteStep("Continue", "continue", "straight", "Great Ancoats Street", length-firstLength, marker, leftDriving = true),
            RouteStep("Arrive", "arrive", "", "", 0.0, points.last(), leftDriving = true)
        ), "Frozen published source pipeline")
        assertTrue(route.steps.all { it.lanes.isEmpty() })
        lateinit var session: NavigationSession
        compose.runOnIdle { session = NavigationSession(app) }
        compose.waitUntil(30000) { session.settingsReady }
        try {
            compose.runOnIdle {
                call(session, "setRouteInternal", route)
                // Feed exact source bytes through the production loader and its LaneDb parser.
                val loader = OwnMap::class.java.getDeclaredField("laneLoader").apply { isAccessible = true }.get(OwnMap)
                val request = loader.javaClass.getDeclaredMethod("beginRequest").invoke(loader) as Long
                val generation: () -> Long = { 0L }
                val active: () -> Boolean = { true }
                val read: (String, String?) -> String? = { key, _ -> if (key == "106_-5") raw else null }
                val loaded = loader.javaClass.declaredMethods.single { it.name == "load" && it.parameterCount == 5 }
                    .apply { isAccessible = true }.invoke(loader, points, request, generation, active, read) as Boolean
                assertTrue("Production source loader must publish the parsed database", loaded)
                call(session, "setPositionReliable", true)
                val at = Geo.interpolate(points[1], points[2], 0.2)
                val gps = Location("gps").apply {
                    latitude = at.lat; longitude = at.lon; accuracy = 3f; speed = 0f
                    time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                call(session, "updateNavigationInternal", gps)
                assertEquals("The session must obtain both lanes from source data", 2, session.nav?.lanes?.size)
                assertEquals(listOf("left", "straight"), session.nav!!.lanes[0].indications)
                assertEquals(listOf("straight"), session.nav!!.lanes[1].indications)
            }
            compose.setContent { LaneRow(session.nav!!.lanes, true, maneuver = "straight") }
            compose.onNodeWithContentDescription("Lane 1. Markings: left or straight on. Recommended by lane guidance").assertIsDisplayed()
            compose.onNodeWithContentDescription("Lane 2. Markings: straight on. Recommended by lane guidance").assertIsDisplayed()
            val name = "publishedGreatAncoatsSourceReachesSessionAndVisibleLaneStrip"
            val dir = File(app.externalMediaDirs.first(), "additional_test_output").apply { mkdirs() }
            val screenshot = File(dir, "$name.png")
            screenshot.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val stages = JSONObject()
            fun stage(key: String, digest: String) { stages.put(key, JSONObject().put("status", "passed").put("artifact_sha256", digest)) }
            stage("source", sha(raw.toByteArray()))
            val loadedLanes = requireNotNull(OwnMap.lanesIfLoaded()!!.lanesAt(points[1], Geo.bearing(points[0], points[2]), "straight"))
            stage("loader", sha(loadedLanes.toString().toByteArray()))
            stage("session", sha(session.nav!!.lanes.toString().toByteArray()))
            stage("screen", sha(screenshot.readBytes()))
            val verdict = JSONObject().put("case_id", "osm-way-930354").put("country", "GB")
                .put("mode", "offline").put("source_kind", "published_real_uk").put("claim", "pipeline_only")
                .put("input_sha256", sha(raw.toByteArray())).put("map_sha256", root.getString("source_sha256"))
                .put("legal_truth", JSONObject().put("status", "unknown")).put("stages", stages)
            File(dir, "$name-verdict.json").writeText(verdict.toString())
        } finally {
            compose.runOnIdle { session.close(); OwnMap.trimMemory() }
        }
    }
}
