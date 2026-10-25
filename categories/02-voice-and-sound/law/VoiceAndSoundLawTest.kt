package com.drivemate.tests.cat02

import com.drivemate.app.data.VoiceCommand
import com.drivemate.app.nav.Format
import com.drivemate.app.nav.GuidanceVolume
import com.drivemate.app.nav.SoundModePolicy
import com.drivemate.app.nav.VoiceFocusMode
import com.drivemate.app.nav.VoiceFocusPolicy
import com.drivemate.app.nav.VoiceOutput
import com.drivemate.app.nav.VoiceTiming
import com.drivemate.app.nav.WakePhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 2, law tests: the app against the law and official sources only, never the register's
 * choices. References are in drivemate-app/register/02-voice-and-sound.md.
 */
class VoiceAndSoundLawTest {
    private val uk = Format(imperial = true, yards = true)

    /**
     * L3 regulation 110 and L4 GOV.UK: no hand-held use while driving. A question said with the wake
     * phrase is understood in one breath, with no touch, and the spoken commands need no screen.
     */
    @Test fun questionsNeedNoTouch() {
        val question = WakePhrase.after("Hey DriveMate, how long left")
        assertEquals("how long left", question)
        assertNotNull(VoiceCommand.parse(question!!))
    }

    /** L5 Highway Code rule 219: beeps and chimes are short, so a siren is never hidden. */
    @Test fun tonesAreShort() {
        assertTrue("${SoundModePolicy.TONE_MS} ms", SoundModePolicy.TONE_MS <= 500)
    }

    /** L5 rule 219: guidance is never pushed above the phone's or car's own volume. */
    @Test fun guidanceNeverAboveTheDeviceVolume() {
        for (v in GuidanceVolume.entries) assertTrue("${v.label}: ${v.level}", v.level in 0.1f..1f)
    }

    /** L1 Highway Code rule 148: directions are heard over music: it is always turned down or paused. */
    @Test fun musicNeverDrownsDirections() {
        for (output in VoiceOutput.entries) for (pause in listOf(false, true)) {
            val mode = VoiceFocusPolicy.mode(output, pause)
            assertTrue("$output pause=$pause: $mode", mode.name.endsWith("_DUCK") || mode.name.endsWith("_PAUSE"))
        }
        assertEquals(VoiceFocusMode.CAR_DUCK, VoiceFocusPolicy.mode(VoiceOutput.CAR, false))
        assertEquals(VoiceFocusMode.PHONE_PAUSE, VoiceFocusPolicy.mode(VoiceOutput.PHONE, true))
    }

    /** L6 National Highways: advance direction signs at 1 mile and ½ mile; motorway calls come at the same points. */
    @Test fun motorwayCallsAtTheSignDistances() {
        val early = VoiceTiming.early(30.0, 1)
        assertEquals("first call at the 1 mile sign", 1609.344, early, 1609.344 * 0.02)
        assertEquals("1 mile", uk.spokenDistance(early))
        assertTrue("second call at the ½ mile sign", VoiceTiming.exitReminder("off ramp", early, 804.672, VoiceTiming.now(30.0, 1)))
        assertEquals("half a mile", uk.spokenDistance(804.672))
    }

    /** L6 (and TSRGD, as on UK signs): spoken distances are in miles and yards. */
    @Test fun spokenDistancesInMilesAndYards() {
        for (m in listOf(50.0, 150.0, 220.0)) assertTrue("$m m: ${uk.spokenDistance(m)}", uk.spokenDistance(m).endsWith("yards"))
        for (m in listOf(800.0, 1_609.344, 20_000.0)) {
            val said = uk.spokenDistance(m)
            assertTrue("$m m: $said", said.endsWith("mile") || said.endsWith("miles"))
        }
    }
}
