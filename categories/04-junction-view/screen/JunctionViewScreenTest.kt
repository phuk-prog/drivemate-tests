package com.drivemate.tests.cat04

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.nav.*
import com.drivemate.app.ui.JunctionPill
import com.drivemate.app.ui.navigationJunctionAction
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JunctionViewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val plan = JunctionPlan(true, 2, 1, listOf(0), RibbonSource.ARROWS,
        listOf(Shield("M60", Shield.Kind.MOTORWAY)), "Stockport", "1")
    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "additional_test_output")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun q4_5_6_headerKeepsSignsAndNextMoveInsideIt() {
        compose.setContent {
            MaterialTheme {
                JunctionPill(plan, "300 m", false, "Take the exit", "", false, "Left lane",
                    icon = { Text("↖") }, lanes = null, thenText = "Then roundabout, 3rd exit")
            }
        }
        compose.onNodeWithTag("navigation-header", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Then roundabout, 3rd exit")))
        compose.onNodeWithText("Then roundabout, 3rd exit", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("J1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("M60", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Stockport", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Then", useUnmergedTree = true).assertDoesNotExist()
        shot("q4_5_6_headerKeepsSignsAndNextMoveInsideIt")
    }
    @Test fun q4_11_19_laneTapOnlyReportsAndHeaderTapDismisses() {
        var dismissed = 0; var reports = 0
        compose.setContent {
            JunctionPill(plan, "100 m", false, "Take the exit", "", false, "",
                icon = { Text("↖") },
                lanes = { Text("Report lanes", Modifier.heightIn(min = 48.dp).testTag("lanes").clickable { reports++ }) },
                modifier = Modifier.clickable { dismissed++ })
        }
        compose.onNodeWithTag("lanes", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, reports); assertEquals(0, dismissed) }
        compose.onNodeWithText("Take the exit", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, dismissed); assertEquals(1, reports) }
    }
    @Test fun q4_16_nightAndLargeTextKeepEssentialHeaderVisible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    JunctionPill(plan, "Now", true, "Take the exit", "", true, "",
                        icon = { Text("↖") }, lanes = null, thenText = "Then roundabout, 3rd exit")
                }
            }
        }
        compose.onNodeWithText("Now", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Take the exit", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Then roundabout, 3rd exit", useUnmergedTree = true).assertIsDisplayed()
        shot("q4_16_nightAndLargeTextKeepEssentialHeaderVisible")
    }
    @Test fun q4_6_10_chainedRoundaboutHeaderDropsPreviousExitSignsAndLanes() {
        val step = com.drivemate.app.data.RouteStep("Roundabout", "roundabout", "", "A6", 100.0,
            LatLon(53.0, -2.0), exit = 3, ref = "A6", towards = "Town")
        val header = com.drivemate.app.ui.navigationJunctionHeaderPlan(step)
        assertEquals(listOf("A6"), header.shields.map { it.ref })
        assertEquals("Town", header.destination)
        assertEquals("", header.exitNumber)
        assertEquals(3, header.roundaboutExit)
        assertTrue(header.highlighted.isEmpty())
        assertEquals(RibbonSource.NONE, header.source)
    }
    @Test fun q4_6_14_unknownRoundaboutDirectionUsesExitOnly() {
        assertEquals("Exit 3", navigationJunctionAction("At the roundabout, take the third exit", true, false, 3))
        assertEquals("Turn left", navigationJunctionAction("Turn left", false, false, 0))
    }
}
