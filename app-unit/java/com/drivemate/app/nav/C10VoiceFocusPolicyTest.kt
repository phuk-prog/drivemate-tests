package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 10 pure policy checks (10.2, 10.3). They test the decision functions behind audio focus,
 * speaker routing, call handling and stale resumes. They do not call AudioManager, so they prove the
 * decision and not the Android focus grant or release itself.
 */
class C10VoiceFocusPolicyTest {
    /** 10.2: music ducks by default; only the explicit Pause choice asks for a pausing focus, on car and phone alike. */
    @Test fun q10_2_defaultDucksAndOnlyPauseChoicePauses() {
        assertEquals(VoiceFocusMode.CAR_DUCK, VoiceFocusPolicy.mode(VoiceOutput.CAR, pauseMusic = false))
        assertEquals(VoiceFocusMode.CAR_PAUSE, VoiceFocusPolicy.mode(VoiceOutput.CAR, pauseMusic = true))
        assertEquals(VoiceFocusMode.PHONE_DUCK, VoiceFocusPolicy.mode(VoiceOutput.PHONE, pauseMusic = false))
        assertEquals(VoiceFocusMode.PHONE_PAUSE, VoiceFocusPolicy.mode(VoiceOutput.PHONE, pauseMusic = true))
        for (route in VoiceOutput.entries) {
            assertTrue("$route default must duck", VoiceFocusPolicy.mode(route, pauseMusic = false).name.endsWith("_DUCK"))
        }
    }

    /** 10.2: Bluetooth (normal car route) and phone speaker choose the same focus kind for the same pause setting. */
    @Test fun q10_2_bluetoothAndSpeakerDifferOnlyInRoute() {
        for (pause in listOf(false, true)) {
            val car = VoiceFocusPolicy.mode(VoiceOutput.CAR, pause).name.substringAfter('_')
            val phone = VoiceFocusPolicy.mode(VoiceOutput.PHONE, pause).name.substringAfter('_')
            assertEquals("pause=$pause", car, phone)
        }
    }

    /** 10.2: a phone speaker is only chosen when the device has an API that can route it (28 and later). */
    @Test fun q10_2_phoneSpeakerNeedsApi28() {
        assertFalse(VoiceFocusPolicy.supportsPhoneSpeaker(27))
        assertTrue(VoiceFocusPolicy.supportsPhoneSpeaker(28))
    }

    /** 10.2: the phone speaker is treated as in use only when it is the single active output. */
    @Test fun q10_2_speakerOnlyRequiresTheBuiltInSpeakerAlone() {
        val builtIn = 2
        val bluetooth = 8
        assertTrue(PhoneSpeakerRoutePolicy.speakerOnly(listOf(builtIn), builtIn))
        assertFalse("Bluetooth is also active", PhoneSpeakerRoutePolicy.speakerOnly(listOf(builtIn, bluetooth), builtIn))
        assertFalse("Bluetooth alone", PhoneSpeakerRoutePolicy.speakerOnly(listOf(bluetooth), builtIn))
        assertFalse("no active output is not proof of speaker-only", PhoneSpeakerRoutePolicy.speakerOnly(emptyList(), builtIn))
    }

    /** 10.3: with "Speak directions during calls" off, nothing is spoken or beeped over a call; it waits. */
    @Test fun q10_3_callHoldsEverythingWhenTheSettingIsOff() {
        for (direction in listOf(true, false)) for (answer in listOf(true, false)) {
            assertEquals(CallSound.WAIT, DuringCallPolicy.sound(false, direction, answer))
        }
    }

    /** 10.3: with the setting on, directions and answers speak over the call, warnings only beep. */
    @Test fun q10_3_callSettingOnSpeaksOnlyUsefulPrompts() {
        assertEquals(CallSound.SPEAK, DuringCallPolicy.sound(true, direction = true, answer = false))
        assertEquals(CallSound.SPEAK, DuringCallPolicy.sound(true, direction = false, answer = true))
        assertEquals(CallSound.BEEP, DuringCallPolicy.sound(true, direction = false, answer = false))
    }

    /** 10.3: a delayed resume scheduled before a route change, background or call cannot run afterwards. */
    @Test fun q10_3_staleDelayedSpeechIsDroppedAfterSupersede() {
        val schedule = VoiceSpeechSchedule()
        var ran = 0
        val stale = schedule.delayed { ran++ }
        schedule.supersede()
        stale()
        assertEquals("a superseded resume must not speak", 0, ran)
        val fresh = schedule.delayed { ran++ }
        fresh()
        assertEquals("a resume scheduled after the change still runs once", 1, ran)
    }

    /** 10.3: a silent (Silent mode) setting still speaks an answer the driver asked for, but never a direction. */
    @Test fun q10_3_silentModeKeepsAnswersOnly() {
        assertTrue(com.drivemate.app.nav.SoundModePolicy.plays(com.drivemate.app.data.SoundMode.OFF, alert = false, answer = true))
        assertFalse(com.drivemate.app.nav.SoundModePolicy.plays(com.drivemate.app.data.SoundMode.OFF, alert = false, answer = false))
    }
}
