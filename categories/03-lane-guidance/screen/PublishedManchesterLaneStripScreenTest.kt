package com.drivemate.tests.cat03

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.data.MapData
import com.drivemate.app.data.OwnMap
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LanePolicy
import com.drivemate.app.nav.LatLon
import com.drivemate.app.ui.LaneRow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * J27-01 source-to-actual-Compose-strip PRETEST using the unchanged published
 * Greater Manchester lane square and production MapData, OwnMap, LaneDb, LaneRow.
 *
 * Frozen real OSM way 930354 (Great Ancoats Street, A665) and coordinates are
 * from the audited UK-join square SHA-256
 * dcaff102f855531e6bce98b77d8a2068684296590f20c032240a4eb8521e5685.
 *
 * This proves source-derived arrows can render in the actual lane composable.
 * It deliberately does NOT claim dated independent legal lane markings, a
 * connected manoeuvre, NavigationSession/NavUi selection, the full driving
 * screen, offline parity, or the driver's installed APK.
 */
@RunWith(AndroidJUnit4::class)
class PublishedManchesterLaneStripScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun q3_j27_01_publishedManchesterLaneSquareDrawsActualSourceArrows() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        // Exact first two vertices of published OSM way 930354, not a made-up route.
        val a = LatLon(53.4782526, -2.2199713)
        val b = LatLon(53.4782723, -2.2198129)
        val at = Geo.interpolate(a, b, 0.5)
        val heading = Geo.bearing(a, b)
        val square = "lanes-106_-5.json"

        MapData.init(app)
        MapData.focusRegion(at.lat, at.lon)
        val until = SystemClock.elapsedRealtime() + 50_000L
        var published = MapData.publishedNames()
        while (square !in published.orEmpty() && SystemClock.elapsedRealtime() < until) {
            Thread.sleep(500)
            published = MapData.publishedNames()
        }
        assertTrue("PUBLISHED_MANCHESTER_LANE_SQUARE_UNAVAILABLE", square in published.orEmpty())
        OwnMap.loadAlong(app, listOf(at), OwnMap.beginLaneRequest())
        val db = requireNotNull(OwnMap.lanesIfLoaded()) {
            "PUBLISHED_MANCHESTER_LANE_SQUARE_NOT_INDEXED"
        }
        val actualWay = db.nearbySegments(at).any {
            it.way.id == "930354" &&
                it.way.tags["name"] == "Great Ancoats Street" &&
                it.way.tags["turn:lanes"] == "left;through|through"
        }
        assertTrue("PUBLISHED_OSM_WAY_930354_NOT_LOADED_OR_CHANGED", actualWay)

        val lanes = requireNotNull(db.lanesAt(at, heading, "straight")) {
            "SOURCE_BACKED_LANES_NOT_SELECTED: source is present but LaneDb rejected it"
        }
        assertEquals(
            listOf(listOf("left", "straight"), listOf("straight")),
            lanes.map { it.indications }
        )
        assertEquals(listOf(0, 1), LanePolicy.usable(lanes))
        assertTrue("SOURCE_LANE_LAYOUT_NOT_RELIABLE", LanePolicy.reliable(lanes))
        assertTrue("No unverified preferred lane", LanePolicy.preferred(lanes).isEmpty())

        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A1830))) {
                LaneRow(lanes, true, laneSize = 52.dp, maneuver = "straight")
            }
        }
        compose.onNodeWithContentDescription(
            "Lane 1. Markings: left or straight on. Recommended by lane guidance"
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Lane 2. Markings: straight on. Recommended by lane guidance"
        ).assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val media = File(
            InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(),
            "additional_test_output"
        ).apply { mkdirs() }
        File(media, "q3_j27_01_publishedManchesterLaneSquareDrawsActualSourceArrows.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        println(
            "J27_01_PUBLISHED_SOURCE_TO_LANEROW way=930354; source_square=$square; " +
                "source_lanes=${lanes.size}; displayed_lanes=2; " +
                "navigation_session=NOT_TESTED; legal_markings=UNKNOWN; " +
                "connected_turn=UNKNOWN; driver_apk=UNKNOWN"
        )
    }
}
