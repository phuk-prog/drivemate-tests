package com.drivemate.tests.cat02

import com.drivemate.app.data.HazardType
import com.drivemate.app.data.PlaceKind
import com.drivemate.app.data.Settings
import com.drivemate.app.data.SoundMode
import com.drivemate.app.data.VoiceCommand
import com.drivemate.app.nav.AlertSound
import com.drivemate.app.nav.AlertSoundPolicy
import com.drivemate.app.nav.CallSound
import com.drivemate.app.nav.DuringCallPolicy
import com.drivemate.app.nav.GuidanceVolume
import com.drivemate.app.nav.SoundModePolicy
import com.drivemate.app.nav.Speech
import com.drivemate.app.nav.SpokenGuidance
import com.drivemate.app.nav.TurnInstructions
import com.drivemate.app.nav.VoiceAnswers
import com.drivemate.app.nav.VoiceFocusMode
import com.drivemate.app.nav.VoiceFocusPolicy
import com.drivemate.app.nav.VoiceOutput
import com.drivemate.app.nav.VoiceTiming
import com.drivemate.app.nav.WakePhrase
import com.drivemate.app.nav.roundaboutCirculationInstruction
import com.drivemate.app.nav.roundaboutExitPrompt
import com.drivemate.app.ui.soundModePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 2, decision checks: the app does what is decided in
 * drivemate-app/register/02-voice-and-sound.md. Each test names its question.
 */
class VoiceAndSoundDecisionsTest {
    private val defaults = Settings()

    /** 2.1: the voice says what the card shows: the same move words, the roundabout exit, mini-roundabouts. */
    @Test fun q2_1_voiceUsesTheCardWording() {
        val card = TurnInstructions.describe("turn", "slight left", "Station Road", 0, true)
        assertEquals("Slight left onto Station Road", card)
        assertEquals("In half a mile, slight left onto Station Road", VoiceAnswers.nextTurn("half a mile", card))
        assertEquals("In 300 yards, slight left onto Station Road", SpokenGuidance.early("300 yards", null, card))
        assertEquals("No unverified ordinal on the voice prompt", "Take the exit", roundaboutExitPrompt(""))
        assertEquals("Unknown exit number is consistently neutral on card and voice",
            roundaboutCirculationInstruction(0, 40.0, ""), roundaboutExitPrompt(""))
        assertEquals("When the source explicitly provides an exit ordinal, card and voice match",
            roundaboutCirculationInstruction(2, 40.0, ""), roundaboutExitPrompt("", exit = 2))
        assertEquals("Only mapped junctions justify the next-exit claim",
            roundaboutCirculationInstruction(2, 40.0, "", nextExitKnown = true),
            roundaboutExitPrompt("", nextExitKnown = true, exit = 2))
        val mini = TurnInstructions.miniRoundabout("right", "")
        assertTrue(mini, mini.startsWith("At the mini-roundabout, turn right"))
    }

    /** 2.2: Late / Normal / Early, Normal by default; the "now" call is the same for Late and Normal. */
    @Test fun q2_2_voiceTimingChoices() {
        assertEquals(1, defaults.voiceTiming)
        val fast = 30.0
        assertEquals(1600.0, VoiceTiming.early(fast, 1), 0.1)
        assertTrue("Early is sooner", VoiceTiming.early(fast, 2) > VoiceTiming.early(fast, 1))
        assertTrue("Late is later", VoiceTiming.early(fast, 0) < VoiceTiming.early(fast, 1))
        assertEquals(VoiceTiming.now(fast, 1), VoiceTiming.now(fast, 0), 0.1)
    }

    /** 2.3: one early call (about 1 mile over 56 mph, ½ mile at 43–56 mph, about 350 m in town), then "now" about 5 s before. */
    @Test fun q2_3_callsPerTurn() {
        assertEquals(1600.0, VoiceTiming.approach(27.0), 0.1)
        assertEquals(800.0, VoiceTiming.approach(22.0), 0.1)
        assertEquals(350.0, VoiceTiming.approach(13.0), 0.1)
        assertEquals("about 5 seconds", 150.0, VoiceTiming.now(30.0, 1), 0.1)
        assertEquals("at 5 m/s use five seconds, not 45 m", 25.0, VoiceTiming.now(5.0, 1), 0.1)
        assertEquals("at 5 mph use about 11 m", 11.2, VoiceTiming.now(2.24, 1), 0.1)
        assertEquals("at a stop use only junction tolerance", 8.0, VoiceTiming.now(0.0, 1), 0.1)
    }

    /** 2.4: motorway exits: 1 mile with the junction number first, ½ mile, then "Exit now, on the left". */
    @Test fun q2_4_motorwayExitThreeCalls() {
        val early = VoiceTiming.early(30.0, 1)
        val near = VoiceTiming.now(30.0, 1)
        assertEquals("In 1 mile, at junction 2, take the exit on the left towards Stockport",
            SpokenGuidance.early("1 mile", "2", "Take the exit on the left towards Stockport"))
        assertTrue("½ mile reminder", VoiceTiming.exitReminder("off ramp", early, 804.0, near))
        assertFalse("no reminder for a town turn", VoiceTiming.exitReminder("turn", early, 804.0, near))
        assertEquals("Exit now, on the left", SpokenGuidance.exitNow("off ramp", "slight left"))
        assertEquals("Exit now, on the right", SpokenGuidance.exitNow("off ramp", "right"))
        assertNull(SpokenGuidance.exitNow("turn", "left"))
    }

    /** 2.5: "Continue on the M6 for 12 miles" when the move is far, and the 5-minute heads-up at most every 15 minutes. */
    @Test fun q2_5_longStretches() {
        assertEquals("Continue on the M6 for 12 miles", SpokenGuidance.continueOn("M6", "12 miles"))
        assertEquals("Continue on Station Road for 4 miles", SpokenGuidance.continueOn("Station Road", "4 miles"))
        assertTrue(SpokenGuidance.continueDue(20_000.0, 1600.0))
        assertFalse(SpokenGuidance.continueDue(2_500.0, 1600.0))
        assertEquals("In about 5 minutes, turn left onto Station Road", SpokenGuidance.headsUp("turn left onto Station Road"))
        val fiveMinutesAway = 30.0 * 290
        assertTrue(SpokenGuidance.headsUpDue(30.0, fiveMinutesAway, 1600.0, 16 * 60_000L))
        assertFalse("not within 15 minutes of the last call", SpokenGuidance.headsUpDue(30.0, fiveMinutesAway, 1600.0, 10 * 60_000L))
    }

    /** 2.6: "Starting route to Tesco. In 300 yards, turn left onto …". */
    @Test fun q2_6_startingATrip() {
        assertEquals("Starting route to Tesco. In 300 yards, turn left onto Station Road",
            SpokenGuidance.start("Tesco", "300 yards", "turn left onto Station Road"))
        assertEquals("Starting route. In 300 yards, turn left", SpokenGuidance.start(null, "300 yards", "turn left"))
    }

    /** 2.7: "Take the next exit" on the card and in the voice just before your exit; the lane when known. */
    @Test fun q2_7_roundaboutNextExit() {
        assertEquals("Take the next exit onto Station Road",
            roundaboutCirculationInstruction(2, 50.0, "Station Road", nextExitKnown = true))
        assertEquals("Take the next exit", roundaboutExitPrompt("", nextExitKnown = true))
        assertEquals("Take the next exit, from the left lane",
            roundaboutExitPrompt("from the left lane", nextExitKnown = true))
        assertEquals("Unknown next exit retains the numbered claim instead of an invented 'next'",
            "Take the 2nd exit onto Station Road",
            roundaboutExitPrompt("", exit = 2, road = "Station Road"))
        assertEquals("Unmapped exit stays unknown", "Take the exit", roundaboutExitPrompt(""))
        assertEquals("Card and speech identical when unverified",
            roundaboutCirculationInstruction(2, 40.0, "Station Road"),
            roundaboutExitPrompt("", exit = 2, road = "Station Road"))
        assertEquals("further out, the exit number", "Take the 2nd exit onto Station Road", roundaboutCirculationInstruction(2, 100.0, "Station Road"))
    }

    /** 2.8: Normal (Bluetooth or phone) by default; Car-call mode and Phone speaker only as choices. */
    @Test fun q2_8_whereDirectionsPlay() {
        assertEquals(VoiceOutput.CAR, defaults.voiceOutput)
        assertEquals(listOf(VoiceOutput.CAR, VoiceOutput.CALL, VoiceOutput.PHONE), VoiceOutput.entries.toList())
    }

    /** 2.9: music turned down by default; "Pause" stays a choice. */
    @Test fun q2_9_musicTurnedDown() {
        assertFalse(defaults.pauseMusic)
        assertEquals(VoiceFocusMode.CAR_DUCK, VoiceFocusPolicy.mode(VoiceOutput.CAR, pauseMusic = false))
        assertEquals(VoiceFocusMode.CAR_PAUSE, VoiceFocusPolicy.mode(VoiceOutput.CAR, pauseMusic = true))
    }

    /** 2.10: "Speak directions during calls", off by default: off holds everything back, on speaks directions. */
    @Test fun q2_10_speakDuringCallsSetting() {
        assertFalse(defaults.speakDuringCalls)
        assertEquals(CallSound.WAIT, DuringCallPolicy.sound(false, direction = true, answer = false))
        assertEquals(CallSound.WAIT, DuringCallPolicy.sound(false, direction = false, answer = false))
        assertEquals(CallSound.SPEAK, DuringCallPolicy.sound(true, direction = true, answer = false))
    }

    /** 2.11: Waze style: cameras and hazards spoken, speeding a short chime; "Beep" stays a choice. */
    @Test fun q2_11_alertSounds() {
        assertFalse(defaults.beepAlerts)
        assertEquals(AlertSound.SPOKEN, AlertSoundPolicy.warning(beepAlerts = false))
        assertEquals(AlertSound.BEEP, AlertSoundPolicy.warning(beepAlerts = true))
        assertEquals(AlertSound.CHIME, AlertSoundPolicy.speeding())
    }

    /** 2.12: Directions & warnings / Alerts only / Silent, Directions & warnings by default. */
    @Test fun q2_12_soundModes() {
        assertEquals(SoundMode.ALL, defaults.sound)
        assertEquals(listOf("Directions & warnings", "Alerts only", "Silent"), SoundMode.entries.map { it.label })
        assertTrue(SoundModePolicy.plays(SoundMode.ALL, alert = false, answer = false))
        assertFalse("Alerts only: no directions", SoundModePolicy.plays(SoundMode.ALERTS, alert = false, answer = false))
        assertTrue(SoundModePolicy.plays(SoundMode.ALERTS, alert = true, answer = false))
        assertFalse("Silent", SoundModePolicy.plays(SoundMode.OFF, alert = true, answer = false))
    }

    /** 2.13: the sample is kept word for word; Silent plays nothing. */
    @Test fun q2_13_sample() {
        assertEquals("This is how DriveMate sounds. In 300 yards, at the roundabout, take the second exit.", soundModePreview(SoundMode.ALL))
        assertNull(soundModePreview(SoundMode.OFF))
    }

    /** 2.14: not added: there is no setting that drops spoken prompts while music plays. */
    @Test fun q2_14_noFewerPromptsWithMusic() {
        val musicSettings = Settings::class.java.declaredFields.map { it.name }.filter { it.contains("music", ignoreCase = true) }
        assertEquals(listOf("pauseMusic"), musicSettings)
        assertTrue("the long-stretch calls do not depend on music", SpokenGuidance.continueDue(20_000.0, 1600.0))
    }

    /** 2.15: the tap stays; "Hey DriveMate" is a setting, off by default, on the phone only, only while navigating. */
    @Test fun q2_15_wakeWord() {
        assertFalse(defaults.wakeWord)
        assertTrue(WakePhrase.listen(enabled = true, navigating = true, micPermission = true, onDevice = true))
        assertFalse("off by default", WakePhrase.listen(enabled = false, navigating = true, micPermission = true, onDevice = true))
        assertFalse("only while navigating", WakePhrase.listen(enabled = true, navigating = false, micPermission = true, onDevice = true))
        assertFalse("on the phone only", WakePhrase.listen(enabled = true, navigating = true, micPermission = true, onDevice = false))
        assertEquals("what's my next turn", WakePhrase.after("Hey DriveMate, what's my next turn"))
        assertEquals("how's the traffic", WakePhrase.after("hey drive mate how's the traffic"))
        assertEquals("", WakePhrase.after("Hey DriveMate"))
        assertNull(WakePhrase.after("drive to work"))
    }

    /** 2.16: time left, speed limit, and the four added: next turn, traffic, current road, next fuel / services. */
    @Test fun q2_16_spokenAnswers() {
        assertEquals(VoiceCommand.HowLong, VoiceCommand.parse("how long left"))
        assertEquals(VoiceCommand.SpeedLimit, VoiceCommand.parse("what's the speed limit"))
        assertEquals(VoiceCommand.NextTurn, VoiceCommand.parse("what's my next turn"))
        assertEquals(VoiceCommand.Traffic, VoiceCommand.parse("how's the traffic"))
        assertEquals(VoiceCommand.CurrentRoad, VoiceCommand.parse("what road am I on"))
        assertEquals(VoiceCommand.HowFar(PlaceKind.SERVICES), VoiceCommand.parse("how far to the next services"))
        assertEquals(VoiceCommand.HowFar(PlaceKind.FUEL), VoiceCommand.parse("where's the nearest petrol station"))
        assertEquals("still a report", VoiceCommand.Report(HazardType.TRAFFIC), VoiceCommand.parse("there's traffic ahead"))
        assertEquals("You're on the A6", VoiceAnswers.currentRoad("A6"))
        assertEquals("Traffic is adding about 5 minutes to your journey", VoiceAnswers.traffic(true, true, 300.0, false))
        assertEquals("No traffic delays reported on your route", VoiceAnswers.traffic(true, true, 20.0, false))
        assertEquals("unknown is never \"no delays\"", "Live traffic isn't available right now, so I don't know about delays",
            VoiceAnswers.traffic(true, false, null, false))
        assertEquals("Next services: Knutsford Services, in 12 miles",
            VoiceAnswers.nextPlace(PlaceKind.SERVICES, true, "Knutsford Services", "12 miles"))
    }

    /** 2.17: guidance volume Softer / Normal / Louder, Normal by default; the phone's own voice. */
    @Test fun q2_17_guidanceVolume() {
        assertEquals(GuidanceVolume.NORMAL, defaults.guidanceVolume)
        assertEquals(listOf("Softer", "Normal", "Louder"), GuidanceVolume.entries.map { it.label })
        assertTrue(GuidanceVolume.SOFTER.level < GuidanceVolume.NORMAL.level && GuidanceVolume.NORMAL.level < GuidanceVolume.LOUDER.level)
    }

    /** 2.18: road numbers and abbreviations said the UK way. */
    @Test fun q2_18_ukRoadNumbers() {
        assertEquals("A-fifty-one forty-five", Speech.forVoice("A5145"))
        assertEquals("M-six oh two", Speech.forVoice("M602"))
        assertEquals("A-five five five", Speech.forVoice("A555"))
        assertEquals("junction two", Speech.forVoice("J2"))
        assertEquals("Saint Mary's Road", Speech.forVoice("St Mary's Rd"))
        assertEquals("Mill Street", Speech.forVoice("Mill St"))
    }

    /** 2.19: with the setting on, turn directions are spoken over the call; warnings are a short beep only. */
    @Test fun q2_19_overACall() {
        assertEquals(CallSound.SPEAK, DuringCallPolicy.sound(true, direction = true, answer = false))
        assertEquals(CallSound.BEEP, DuringCallPolicy.sound(true, direction = false, answer = false))
    }

    /** 2.20: after "Hey DriveMate", a chime then it listens; in Silent the answer to your own question is still spoken. */
    @Test fun q2_20_wakeWordInSilent() {
        assertTrue(SoundModePolicy.plays(SoundMode.OFF, alert = true, answer = true))
        assertFalse("directions stay silent", SoundModePolicy.plays(SoundMode.OFF, alert = false, answer = false))
        assertTrue("Silent does not stop listening", WakePhrase.listen(enabled = true, navigating = true, micPermission = true, onDevice = true))
    }
}
