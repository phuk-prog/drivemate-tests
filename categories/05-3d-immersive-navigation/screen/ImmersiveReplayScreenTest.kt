package com.drivemate.tests.cat05

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.ui.RoundaboutReplayDialog
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImmersiveReplayScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun q5_16_17_manualPlayPauseRestartAndMotionLock() {
        val safe = AtomicBoolean(true)
        compose.setContent { MaterialTheme { RoundaboutReplayDialog({ safe.get() }, {}) } }
        compose.onNodeWithTag("replay-locked").assertExists()
        compose.onNodeWithText("I am safely parked").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("replay-stage").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Next").performScrollTo().performClick()
        compose.onNodeWithTag("replay-stage").assertTextContains("2 of 6", substring = true)
        compose.onNodeWithText("Previous").performScrollTo().performClick()
        compose.onNodeWithTag("replay-stage").assertTextContains("1 of 6", substring = true)
        compose.onNodeWithText("Play").performScrollTo().performClick()
        compose.onNodeWithText("Pause").assertExists()
        compose.waitUntil(5000) { compose.onAllNodesWithText("2 of 6", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        compose.onNodeWithText("Restart").performScrollTo().performClick()
        compose.onNodeWithTag("replay-stage").assertTextContains("1 of 6", substring = true)
        val bitmap = compose.onNodeWithTag("replay-scene").performScrollTo().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "additional_test_output"); dir.mkdirs()
        File(dir,"q5_16_17_manualPlayPauseRestartAndMotionLock.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        safe.set(false)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("replay-locked").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithTag("replay-scene").assertDoesNotExist()
    }
}
