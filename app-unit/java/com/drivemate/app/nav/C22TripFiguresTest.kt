package com.drivemate.app.nav

import com.drivemate.app.data.MemoryPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 22.3: history figures say what was measured and what is estimated, and count planned steps passed. */
class C22TripFiguresTest {
    private val t0 = 1_800_000_000_000L

    @Test fun measuredTripSaysItIsGpsAndCountsStepsPassed() {
        val lines = TripFigures.lines(TripSummary("Work", t0, 600, 5_000.0, 0, emptyList(), TripSource.RECORDED, turns = 3))
        assertTrue(lines[0].startsWith("Measured by GPS"))
        assertTrue(lines.contains("Planned-route steps passed: 3 (counted as you passed them)."))
        assertTrue(lines.any { it.startsWith("Uncertainty: GPS distance") })
    }

    @Test fun measuredTripWithoutStepsHasNoStepLine() {
        val lines = TripFigures.lines(TripSummary("Work", t0, 600, 5_000.0, 0, emptyList(), TripSource.RECORDED))
        assertFalse(lines.any { it.startsWith("Planned-route steps") })
    }

    @Test fun plannedEntryIsNeverCalledMeasured() {
        val lines = TripFigures.lines(TripSummary("Work", t0, 600, 5_000.0, 0, emptyList(), TripSource.PLANNED))
        assertFalse(lines.any { it.contains("Measured") })
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("Not driven"))
    }

    @Test fun stepCountOnlyMovesForward() {
        val stats = TripStats("Work", t0)
        stats.reachedStep(2)
        stats.reachedStep(1)
        assertEquals(2, stats.finish(t0 + 60_000).turns)
    }

    @Test fun passedStepsSurviveSavingTheHistory() {
        val prefs = MemoryPreferences()
        TripHistory(prefs).add(TripSummary("Work", t0, 600, 5_000.0, 0, emptyList(), turns = 4))
        assertEquals(4, TripHistory(prefs).all().single().turns)
    }
}
