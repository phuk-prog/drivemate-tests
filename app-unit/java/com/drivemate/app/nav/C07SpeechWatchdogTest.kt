package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C07.3: a speech engine that never reports "done" cannot block later directions or keep music ducked. */
class C07SpeechWatchdogTest {
    @Test fun shortPromptsGetAtLeastEightSeconds() {
        assertEquals(SpeechWatchdog.MIN_MS, SpeechWatchdog.timeoutMs(0))
        assertEquals(SpeechWatchdog.MIN_MS, SpeechWatchdog.timeoutMs(10)) // "Turn left"
    }

    @Test fun longerPromptsGetTimeInProportionWithSlackForSlowSpeech() {
        val text = "In 400 yards, at the roundabout, take the third exit onto the A34 Kingsway, then keep left"
        val ms = SpeechWatchdog.timeoutMs(text.length)
        // At a slow ~8 characters per second the prompt still finishes before the watchdog.
        assertTrue(ms > text.length * 1000L / 8)
        assertEquals(text.length * SpeechWatchdog.PER_CHAR_MS + SpeechWatchdog.SLACK_MS, ms)
    }

    @Test fun neverUnboundedAndNeverNegative() {
        assertEquals(SpeechWatchdog.MAX_MS, SpeechWatchdog.timeoutMs(10_000))
        assertEquals(SpeechWatchdog.MIN_MS, SpeechWatchdog.timeoutMs(-5))
    }
}
