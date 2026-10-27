package com.drivemate.tests.cat03

import com.drivemate.app.data.Lane
import com.drivemate.app.data.Settings
import com.drivemate.app.data.SoundMode
import com.drivemate.app.nav.LaneAlerts
import com.drivemate.app.nav.SoundModePolicy
import com.drivemate.app.nav.VoiceFocusMode
import com.drivemate.app.nav.VoiceFocusPolicy
import com.drivemate.app.nav.VoiceMessageQueue
import com.drivemate.app.nav.VoiceOutput
import com.drivemate.app.nav.Wording
import org.junit.Assert.*
import org.junit.Test

/** Contract checks use the actual production queue and warning policy, not a fake speech player.
 * A JVM test cannot measure Bluetooth music volume or listen to Android TextToSpeech. */
class LaneSpeechInterruptionAcceptanceTest {
    @Test fun q3_12_14_changedRoadworksMustBeAudibleEvenInsideNormalDuplicateSuppressionWindow() {
        val warnings = LaneAlerts()
        val queue = VoiceMessageQueue()
        val first = warnings.warning("nh-works-1", "roadworks, one lane closed")
        assertEquals("Roadworks ahead. Follow road signs.", first)
        assertNull(warnings.warning("nh-works-1", "roadworks, one lane closed"))
        assertTrue(queue.add(first!!, alert = true, interrupt = false, now = 1_000L))
        val heard = queue.next(1_005L)!!
        assertEquals(first, heard.text)
        queue.markSpoken(heard, 1_005L)
        val changed = warnings.warning("nh-works-1", "roadworks, two lanes closed")
        assertNotNull(changed)
        assertNotEquals("Updated source evidence must not be the same speech key", first, changed)
        assertTrue("Updated roadworks must not be hidden by the queue's 30-second alert repeat guard",
            queue.add(changed!!, alert = true, interrupt = false, now = 1_100L))
        assertEquals(changed, queue.next(1_110L)!!.text)
        assertNull(queue.next(1_110L))
    }

    @Test fun q3_14_roadworksDoNotInterruptImminentTurnAndDoNotRepeatWithoutChange() {
        val queue = VoiceMessageQueue()
        assertTrue(queue.add("In 300 yards, turn left", alert = false, interrupt = false, now = 1_000L))
        assertTrue(queue.add("Roadworks ahead. Follow road signs.", alert = true, interrupt = false, now = 1_001L))
        assertTrue(queue.add("Turn left now", alert = false, interrupt = true, now = 1_002L))
        assertEquals("Turn left now", queue.next(1_003L)!!.text)
        assertEquals("Roadworks ahead. Follow road signs.", queue.next(1_003L)!!.text)
        assertNull("Nonurgent old direction was dropped when urgent turn arrived", queue.next(1_003L))
        val warnings = LaneAlerts()
        assertNotNull(warnings.warning("nh-works-2", "roadworks"))
        assertNull(warnings.warning("nh-works-2", "roadworks"))
        assertNull(warnings.warning("", "roadworks"))
        assertNull(warnings.warning("nh-works-2", ""))
    }

    @Test fun q3_14_interruptedWarningResumesOnlyOnceAfterTheUrgentDirection() {
        val queue = VoiceMessageQueue()
        assertTrue(queue.add("Roadworks ahead. Follow road signs.", alert = true, interrupt = false, now = 1_000L))
        val playing = queue.next(1_010L)!!
        assertTrue(queue.add("Turn right now", alert = false, interrupt = true, now = 1_020L))
        assertTrue("Warning may resume after interruption while it is still relevant",
            queue.resume(playing, now = 1_020L))
        assertEquals("Turn right now", queue.next(1_030L)!!.text)
        val resumed = queue.next(1_031L)!!
        assertEquals(playing.text, resumed.text)
        assertTrue(resumed.interrupted)
        assertFalse("Never resume an already resumed warning again", queue.resume(resumed, now = 1_040L))
        assertNull(queue.next(1_040L))
    }

    @Test fun q3_7_14_oldLaneSpeechCannotFollowAClearedJunctionOrReroute() {
        val queue = VoiceMessageQueue()
        assertTrue(queue.add("Use the left lane", alert = false, interrupt = false, now = 1_000L))
        assertTrue(queue.newManoeuvre(2))
        assertNull("Old manoeuvre cannot speak after passage", queue.next(1_010L))
        assertTrue(queue.add("Use the right lane", alert = false, interrupt = false, now = 1_020L))
        queue.newRoute()
        assertNull("Old route's lane guidance cannot speak after rerouting", queue.next(1_030L))
        assertTrue(queue.add("Use the right lane", alert = false, interrupt = false, now = 1_031L))
        assertEquals("Use the right lane", queue.next(1_032L)!!.text)
    }

    @Test fun q3_3_14_spokenLaneSelectionMatchesPermittedLanesNotJustAPreferredMarker() {
        val choices = listOf(
            Lane(listOf("left", "straight"), true, preferred = true),
            Lane(listOf("straight"), false, preferred = false)
        )
        assertEquals("Use the left lane", Wording.laneWords(choices))
        assertEquals("Use the left lane", Wording.laneWords(choices.map { it.copy(preferred = false) }))
        assertEquals("", Wording.laneWords(choices.map { it.copy(valid = true) }))
        assertEquals("", Wording.laneWords(emptyList()))
    }

    @Test fun q2_9_12_q3_14_voiceWarningsRespectSoundModeAndMusicDucking() {
        val defaults = Settings()
        assertFalse("Music must duck rather than pause by default", defaults.pauseMusic)
        assertEquals(VoiceFocusMode.CAR_DUCK, VoiceFocusPolicy.mode(VoiceOutput.CAR, pauseMusic = false))
        assertEquals(VoiceFocusMode.PHONE_DUCK, VoiceFocusPolicy.mode(VoiceOutput.PHONE, pauseMusic = false))
        assertTrue(SoundModePolicy.plays(SoundMode.ALL, alert = false, answer = false))
        assertTrue(SoundModePolicy.plays(SoundMode.ALL, alert = true, answer = false))
        assertFalse(SoundModePolicy.plays(SoundMode.ALERTS, alert = false, answer = false))
        assertTrue(SoundModePolicy.plays(SoundMode.ALERTS, alert = true, answer = false))
        assertFalse(SoundModePolicy.plays(SoundMode.OFF, alert = true, answer = false))
    }
}
