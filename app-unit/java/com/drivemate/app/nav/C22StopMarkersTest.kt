package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 22.2: stop markers come from measured positions only; short or inaccurate stillness is not a stop. */
class C22StopMarkersTest {
    private val t0 = 1_800_000_000_000L
    private val here = LatLon(53.40, -2.20)

    private fun fix(atMs: Long, p: LatLon, accuracy: Double = 5.0) = EvidenceFix(p, atMs, accuracy, null)

    @Test fun twoMinutesStationaryIsOneStop() {
        val fixes = (0..8).map { fix(t0 + it * 15_000L, here) }
        val stops = StopMarkers.detect(fixes)
        assertEquals(1, stops.size)
        assertEquals(t0, stops[0].startMs)
        assertEquals(120_000L, stops[0].durationMs)
    }

    @Test fun movingDriveHasNoStop() {
        val fixes = (0..20).map { fix(t0 + it * 15_000L, LatLon(here.lat + it * 0.001, here.lon)) }
        assertTrue(StopMarkers.detect(fixes).isEmpty())
    }

    @Test fun stillnessShorterThanAMinuteIsNotAStop() {
        val fixes = (0..2).map { fix(t0 + it * 15_000L, here) }
        assertTrue(StopMarkers.detect(fixes).isEmpty())
    }

    @Test fun inaccuratePositionsNeverMakeAStop() {
        val fixes = (0..8).map { fix(t0 + it * 15_000L, here, accuracy = 80.0) }
        assertTrue(StopMarkers.detect(fixes).isEmpty())
    }

    @Test fun aStopBetweenTwoMovesIsFoundOnce() {
        val moving1 = listOf(fix(t0, LatLon(here.lat - 0.002, here.lon)))
        val stopped = (1..7).map { fix(t0 + it * 15_000L, here) }
        val moving2 = listOf(fix(t0 + 8 * 15_000L, LatLon(here.lat + 0.002, here.lon)))
        val stops = StopMarkers.detect(moving1 + stopped + moving2)
        assertEquals(1, stops.size)
        assertEquals(90_000L, stops[0].durationMs)
    }
}
