package com.drivemate.tests.cat02

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.nav.roundaboutCirculationInstruction
import com.drivemate.app.nav.roundaboutExitPrompt
import com.drivemate.app.ui.NavigationCardNavy
import com.drivemate.app.ui.NavigationHeader
import com.drivemate.app.ui.navigationCirculationAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category 2, screen check for 2.7 (and 2.1): in the last 60 m on a roundabout the card, drawn by
 * the app on the emulator, says "Take the next exit", the same words the voice says.
 */
@RunWith(AndroidJUnit4::class)
class RoundaboutNextExitScreenTest {
    @get:Rule val compose = createComposeRule()

    /** Saved beside the test results, so the picture page shows what the card looked like. */
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "additional_test_output")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun q2_7_cardSaysTakeTheNextExit() {
        val instruction = roundaboutCirculationInstruction(2, 40.0, "Station Road", nextExitKnown = true)
        val action = navigationCirculationAction(onRoundabout = true, arrived = false, instruction = instruction, road = "Station Road")!!
        assertEquals("the card's words are the voice's words", roundaboutExitPrompt("", nextExitKnown = true), action)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(NavigationCardNavy)) {
                NavigationHeader(
                    distanceText = "40 yd", now = true, action = action,
                    road = "Station Road", destination = "",
                    shields = emptyList(), exitNumber = "",
                    icon = { Box(Modifier.fillMaxSize().background(Color.White)) },
                )
            }
        }
        compose.onNodeWithText("Take the next exit", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Station Road", useUnmergedTree = true).assertIsDisplayed()
        screenshot("q2_7_cardSaysTakeTheNextExit")
    }
}
