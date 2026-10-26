package com.drivemate.tests.cat03

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.data.Lane
import com.drivemate.app.ui.LaneRow
import com.drivemate.app.ui.laneReviewDue
import com.drivemate.app.nav.LaneClaims
import com.drivemate.app.nav.PositionEvidence
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaneGuidanceScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "additional_test_output")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun q3_2_3_10_13_allKnownLanesPreferredAndSharedBranches() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A1830))) {
                LaneRow(listOf(
                    Lane(listOf("left"), false, false),
                    Lane(listOf("left", "straight"), true, false),
                    Lane(listOf("straight"), true, true)), true, laneSize = 52.dp, maneuver = "straight")
            }
        }
        compose.onNodeWithContentDescription("Lane 1. Markings: left. Not recommended by lane guidance").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lane 2. Markings: left or straight on. Recommended by lane guidance").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lane 3. Markings: straight on. Preferred by lane guidance").assertIsDisplayed()
        screenshot("q3_2_3_10_13_allKnownLanesPreferredAndSharedBranches")
    }
    @Test fun q3_11_12_restrictionsAndClosedLaneRemainVisible() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A1830))) {
                LaneRow(listOf(Lane(listOf("straight"), false, bus = true),
                    Lane(listOf("straight"), true), Lane(listOf("straight"), false, closed = true)), true, laneSize = 52.dp)
            }
        }
        compose.onNodeWithContentDescription("Lane 1. Bus lane").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lane 3. Closed. Do not use. Follow road signs").assertIsDisplayed()
        screenshot("q3_11_12_restrictionsAndClosedLaneRemainVisible")
    }
    @Test fun q3_16_20_reviewPromptWaitsUntilDriveEndsAndParked() {
        assertFalse(laneReviewDue(true, false, true, 1))
        assertFalse(laneReviewDue(false, false, false, 1))
        assertFalse(laneReviewDue(false, false, true, 0))
        assertTrue(laneReviewDue(false, false, true, 1))
        assertTrue(laneReviewDue(true, true, true, 1))
    }
    @Test fun q3_13_noCurrentLaneClaimFromOrdinaryGps() {
        assertTrue(LaneClaims.occupied(null, 0.0, 3) is LaneClaims.Occupancy.Unknown)
        assertTrue(LaneClaims.occupied(PositionEvidence(0.01, 0L, false), 0.0, 3) is LaneClaims.Occupancy.Unknown)
    }
    @Test fun q3_2_3_allTwelveLanesFitAtLargeTextWithoutFalsePreference() {
        compose.setContent {
            val current = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(current.density, 2f)) {
                Box(Modifier.width(320.dp).height(160.dp).background(Color(0xFF0A1830)).testTag("lane-strip-container")) {
                    LaneRow(List(12) { Lane(listOf("straight"), true) }, true, laneSize = 52.dp)
                }
            }
        }
        val container = compose.onNodeWithTag("lane-strip-container").fetchSemanticsNode().boundsInRoot
        for (index in 1..12) {
            val node = compose.onNodeWithContentDescription("Lane $index. Markings: straight on. Recommended by lane guidance")
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("lane $index must fit within the strip", bounds.left >= container.left - 1f && bounds.right <= container.right + 1f)
        }
        compose.onAllNodes(hasContentDescription("Preferred by lane guidance", substring = true)).assertCountEquals(0)
        screenshot("q3_2_3_allTwelveLanesFitAtLargeTextWithoutFalsePreference")
    }
    @Test fun q3_11_12_carBitmapMarksRestrictionsWithoutInventingStraightArrows() {
        val lane = Lane(listOf("straight"), true)
        assertEquals("×", com.drivemate.app.car.carLaneMarker(lane.copy(closed = true, bus = true)))
        assertEquals("BUS", com.drivemate.app.car.carLaneMarker(lane.copy(bus = true)))
        assertEquals("HOV", com.drivemate.app.car.carLaneMarker(lane.copy(restriction = "HOV")))
        assertEquals("?", com.drivemate.app.car.carLaneMarker(lane.copy(indications = listOf("unknown"))))
        assertNull(com.drivemate.app.car.carLaneMarker(lane))
    }

}
