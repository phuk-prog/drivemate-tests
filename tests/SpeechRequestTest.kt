package com.drivemate.app

import android.app.Application
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.drivemate.app.nav.GuidanceVolume
import com.drivemate.app.nav.Speech
import com.drivemate.app.nav.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Spoken guidance (subcategory D, old 12.1/12.2): the app must ask the speech engine to say the guidance
 * text, once, at the configured volume. Records speak requests through the Voice test hook, so audio is
 * not needed. Needs the onSpeakRequest hook (app candidate e3b6e044e058a954b3bc35663b81bb2a22af27cd).
 *
 * Split into three tests so a failing name shows which check broke in the public output.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class SpeechRequestTest {
    private val prompt = "Turn left in 200 metres"

    /** Speaks one prompt and returns every speak request the hook recorded. */
    private fun recordedRequests(): List<Triple<String, Float, String>> {
        val app = RuntimeEnvironment.getApplication()
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        val voice = Voice(app)
        val requests = mutableListOf<Triple<String, Float, String>>()
        voice.onSpeakRequest = { text, volume, id -> requests.add(Triple(text, volume, id)) }
        try {
            voice.onInit(TextToSpeech.SUCCESS)
            looper.idle()
            voice.say(prompt)
            looper.idle()
        } finally {
            voice.shutdown()
            looper.idle()
        }
        return requests
    }

    @Test fun guidanceSpeakRequestIsRecorded() {
        assertTrue("A prompt must produce a speak request", recordedRequests().isNotEmpty())
    }

    @Test fun guidanceIsRequestedExactlyOnce() {
        assertEquals("One prompt must produce one speak request", 1, recordedRequests().size)
    }

    @Test fun guidanceCarriesTheSpokenTextAndVolume() {
        val request = recordedRequests().firstOrNull() ?: throw AssertionError("No speak request was recorded")
        assertEquals(Speech.forVoice(prompt), request.first)
        assertEquals(GuidanceVolume.NORMAL.level, request.second, 0.001f)
    }
}
