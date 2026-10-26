package com.drivemate.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Journey review 7: "Map detail unavailable" only when map pieces really stop arriving or keep
 * failing (owner: "Warn only on real failure"), not while pieces are simply still on their way.
 */
class MapRequestLogTest {
    private val file = "maps.example/drivemate.pmtiles"

    @Test fun piecesStillArrivingIsNotAFailure() {
        val log = MapRequestLog()
        for (t in 0L..19_000L step 1_000) log.record(true, file, t)
        assertFalse(log.failing(0))
    }

    @Test fun nothingArrivingIsAFailure() {
        val log = MapRequestLog()
        log.record(true, file, 0)
        assertTrue("no piece since 1 s", log.failing(1_000))
        assertTrue("no downloads at all: the drawing itself stalled, as before", MapRequestLog().failing(0))
    }

    @Test fun mostlyFailingIsAFailure() {
        val log = MapRequestLog()
        log.record(true, file, 1_000)
        repeat(3) { log.record(false, file, 2_000L + it) }
        assertTrue(log.failing(0))
        assertEquals(file, log.lastFailure(0))
    }

    @Test fun anOccasionalFailureIsNot() {
        val log = MapRequestLog()
        repeat(10) { log.record(true, file, it * 1_000L) }
        repeat(3) { log.record(false, file, 10_000L + it) }
        assertFalse(log.failing(0))
    }

    @Test fun missingPiecesAreANormalAnswer() {
        assertTrue(MapRequestLog.ok(200)); assertTrue(MapRequestLog.ok(206)); assertTrue(MapRequestLog.ok(204))
        assertTrue(MapRequestLog.ok(304)); assertTrue(MapRequestLog.ok(404))
        assertFalse(MapRequestLog.ok(500)); assertFalse(MapRequestLog.ok(503)); assertFalse(MapRequestLog.ok(429))
    }

    @Test fun recordingNeverKeepsTheQuery() {
        assertEquals("maps.example/drivemate.pmtiles", MapRequestLog.fileOf("maps.example", "/data/2026-10/drivemate.pmtiles"))
        assertNull(MapRequestLog().lastFailure(0))
    }

    @Test fun oldEventsAreForgotten() {
        val log = MapRequestLog(windowMs = 60_000)
        log.record(true, file, 0)
        log.record(false, file, 61_000)
        assertTrue("the old success is gone", log.failing(0))
    }
}
