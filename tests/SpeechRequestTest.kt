package com.drivemate.app

import android.app.Application
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.drivemate.app.nav.GuidanceVolume
import com.drivemate.app.nav.Speech
import com.drivemate.app.nav.Voice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Spoken guidance (subcategory D, old 12.1/12.2): the app must ask the speech engine to say the guidance
 * text, once, at the configured volume. Records speak requests through the Voice test hook, so audio is
 * not needed. Needs the onSpeakRequest hook (app candidate e3b6e04).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class SpeechRequestTest {
    @Test fun guidanceIsRequestedOnceWithTextAndVolume() {
        val app = RuntimeEnvironment.getApplication()
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        val voice = Voice(app)
        val requests = mutableListOf<Triple<String, Float, String>>()
        voice.onSpeakRequest = { text, volume, id -> requests.add(Triple(text, volume, id)) }
        try {
            voice.onInit(TextToSpeech.SUCCESS)
            looper.idle()
            voice.say("Turn left in 200 metres")
            looper.idle()
            assertEquals("Exactly one speak request for one prompt", 1, requests.size)
            assertEquals(Speech.forVoice("Turn left in 200 metres"), requests[0].first)
            assertEquals(GuidanceVolume.NORMAL.level, requests[0].second, 0.001f)
        } finally {
            voice.shutdown()
            looper.idle()
        }
    }
}
