package com.drivemate.tests.cat01

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.nav.Shield
import com.drivemate.app.ui.NavigationCardNavy
import com.drivemate.app.ui.NavigationHeader
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category 1, screen check for 1.1: the instruction card, drawn by the app on the emulator, shows
 * every part (distance, arrow, action words, road name, sign destination, road number shield,
 * motorway junction number), with the arrow beside them and distance, action, then signs below.
 */
@RunWith(AndroidJUnit4::class)
class TurnCardScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        .getBoundsInRoot().let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }

    private fun textBounds(text: String): Rect = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        .getBoundsInRoot().let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }

    /** Saved beside the test results, so the picture page shows what the card looked like. */
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "additional_test_output")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun q1_1_cardShowsEveryPartInOrder() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(NavigationCardNavy)) {
                NavigationHeader(
                    distanceText = "300 yd", now = false, action = "Turn left onto",
                    road = "Station Road", destination = "Oxford",
                    shields = listOf(Shield("A34", Shield.Kind.PRIMARY)), exitNumber = "9",
                    icon = { Box(Modifier.fillMaxSize().background(Color.White)) },
                )
            }
        }
        val arrow = bounds("navigation-arrow")
        val distance = bounds("navigation-distance")
        val action = bounds("navigation-action")
        val road = bounds("navigation-road")
        val destination = bounds("navigation-destination")
        val shield = textBounds("A34")
        val junction = textBounds("J9")
        compose.onNodeWithText("300 yd", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Turn left onto", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Station Road", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Oxford", useUnmergedTree = true).assertIsDisplayed()
        screenshot("q1_1_cardShowsEveryPartInOrder")
        assertTrue("arrow left of the words", arrow.right <= distance.left && arrow.right <= action.left)
        assertTrue("distance above the action words", distance.bottom <= action.top)
        for ((name, r) in listOf("road" to road, "destination" to destination, "shield" to shield, "junction" to junction))
            assertTrue("$name below the action words", r.top >= action.bottom)
        assertTrue("arrow is a real size", arrow.width > 0f && arrow.height > 0f)
    }
}
