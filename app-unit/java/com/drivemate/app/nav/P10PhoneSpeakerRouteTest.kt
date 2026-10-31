package com.drivemate.app.nav

import com.drivemate.app.nav.PhoneSpeakerRoutePolicy.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P10 regression (owner, 31 Oct, phone on Bluetooth, "Phone speaker only"): no direction played.
 * The muted prompt now waits for Android's real output, tries the speaker call route once on
 * Android 12+, and is never unmuted unless the built-in speaker alone is playing.
 */
class P10PhoneSpeakerRouteTest {
    private val speaker = 2 // AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    private val a2dp = 8 // AudioDeviceInfo.TYPE_BLUETOOTH_A2DP

    private fun step(types: List<Int>, waited: Long, viaCall: Boolean = false, api: Int = 34, routeOk: Boolean = true) =
        PhoneSpeakerRoutePolicy.step(types, speaker, routeOk, waited, viaCall, api)

    @Test fun speakerAlonePlaysAtOnce() {
        assertEquals(Step.PLAY, step(listOf(speaker), 150))
        assertEquals(Step.PLAY, step(listOf(speaker), 150, viaCall = true))
    }

    @Test fun androidStillSwitchingIsGivenUpToOneSecond() {
        assertEquals(Step.WAIT, step(emptyList(), 150)) // no routed device reported yet
        assertEquals(Step.WAIT, step(listOf(a2dp), 900))
        assertEquals(Step.PLAY, step(listOf(speaker), 900))
    }

    @Test fun keptOnBluetoothTriesTheSpeakerCallRouteOnceOnAndroid12() {
        assertEquals(Step.SPEAKER_CALL_ROUTE, step(listOf(a2dp), PhoneSpeakerRoutePolicy.ROUTE_WAIT_MS))
        assertEquals(Step.STOP, step(listOf(a2dp), PhoneSpeakerRoutePolicy.ROUTE_WAIT_MS, viaCall = true)) // only once
        assertEquals(Step.STOP, step(listOf(a2dp), PhoneSpeakerRoutePolicy.ROUTE_WAIT_MS, api = 30)) // Android 11
    }

    @Test fun neverUnmutedOnTheCarOrBothOrWithTheWrongAudioMode() {
        for (waited in listOf(150L, 2_000L)) {
            for (viaCall in listOf(false, true)) {
                for (types in listOf(listOf(a2dp), listOf(speaker, a2dp), emptyList())) {
                    val s = step(types, waited, viaCall)
                    assertTrue("$types $waited $viaCall gave $s", s != Step.PLAY)
                }
            }
        }
        assertEquals(Step.STOP, step(listOf(speaker), 150, routeOk = false)) // e.g. a real call started
    }
}
