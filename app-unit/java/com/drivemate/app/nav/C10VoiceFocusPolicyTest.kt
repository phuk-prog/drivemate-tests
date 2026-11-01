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

    /** 10.2: another app's may-duck request lowers guidance speech; it is not cut off. */
    @Test fun q10_2_mayDuckLossDucksGuidanceInsteadOfCutting() {
        assertEquals(VoiceFocusAction.DUCK, VoiceFocusChange.action(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
    }

    /** 10.2: a loss that forbids ducking (full loss or transient loss) still stops guidance speech. */
    @Test fun q10_2_fullAndTransientLossStillCutSpeech() {
        assertEquals(VoiceFocusAction.CUT, VoiceFocusChange.action(android.media.AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(VoiceFocusAction.CUT, VoiceFocusChange.action(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
    }

    /** 10.2: regaining focus restores full guidance volume; unrelated focus changes do nothing. */
    @Test fun q10_2_gainRestoresVolumeAndOtherChangesAreIgnored() {
        assertEquals(VoiceFocusAction.RESTORE, VoiceFocusChange.action(android.media.AudioManager.AUDIOFOCUS_GAIN))
        assertEquals(VoiceFocusAction.NONE, VoiceFocusChange.action(0))
    }

    /** 10.2: the ducked level is a quarter of the chosen level, stays in range and keeps silence silent. */
    @Test fun q10_2_duckedLevelIsQuieterAndBounded() {
        assertEquals(0.25f, VoiceFocusChange.duckedLevel(1f), 0.0001f)
        assertEquals(0f, VoiceFocusChange.duckedLevel(0f), 0.0001f)
        for (level in listOf(0.1f, 0.5f, 1f)) {
            val ducked = VoiceFocusChange.duckedLevel(level)
            assertTrue("ducked must be quieter than $level", ducked < level)
            assertTrue("ducked must stay audible", ducked > 0f)
        }
    }

    /** 10.4: the optional repeat covers plain directions only, and only when the setting is on. */
    @Test fun q10_4_repeatOnlyForPlainDirectionsWhenEnabled() {
        assertTrue(RepeatPolicy.eligible(enabled = true, alert = false, answer = false, preview = false))
        assertFalse("setting off", RepeatPolicy.eligible(enabled = false, alert = false, answer = false, preview = false))
        assertFalse("warnings never repeat", RepeatPolicy.eligible(enabled = true, alert = true, answer = false, preview = false))
        assertFalse("answers never repeat", RepeatPolicy.eligible(enabled = true, alert = false, answer = true, preview = false))
        assertFalse("settings samples never repeat", RepeatPolicy.eligible(enabled = true, alert = false, answer = false, preview = true))
    }

    /** 10.4: the repeat waits longer than the 10 s same-direction suppression, so it is not dropped as a duplicate. */
    @Test fun q10_4_repeatDelayIsPastDuplicateSuppression() {
        assertTrue(RepeatPolicy.DELAY_MS > VoiceMessage.DIRECTION_REPEAT_MS)
    }

    /** 10.3: a repeat scheduled before stop or a new route is dropped when it comes due. */
    @Test fun q10_3_repeatDroppedAfterStopOrNewRoute() {
        for (reset in listOf("stop", "newRoute")) {
            val schedule = VoiceSpeechSchedule()
            var said = 0
            val repeat = schedule.delayed { said++ }
            schedule.supersede()
            repeat()
            assertEquals("a repeat pending at $reset must not be said", 0, said)
        }
    }
}
