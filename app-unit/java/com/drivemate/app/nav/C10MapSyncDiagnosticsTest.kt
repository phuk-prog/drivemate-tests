package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C10.1: privacy-safe evidence of whether the map kept up with accepted navigation fixes. */
class C10MapSyncDiagnosticsTest {
    private var now = 0L
    private val d = DriveDiagnostics { now }

    private fun fix(followed: Boolean, atMs: Long) = run { now = atMs; d.mapSync(followed) }

    @Test fun mapThatKeepsFollowingRaisesNothingAndReportsRate() {
        for (i in 0..10) assertTrue(fix(true, i * 1_000L).isEmpty())
        assertEquals(1.0, d.mapFixRateHz()!!, 1e-9)
        assertTrue(d.summary().endsWith("fixes=11 followed=11 maxMapHoldMs=0 fixHz=1.0"))
        assertTrue(d.summary().startsWith("automatic checks found no navigation anomaly"))
    }

    @Test fun mapHeldOverThreeSecondsWhileFixesArriveIsTheFrozenMapSymptom() {
        fix(true, 0)
        assertTrue(fix(false, 1_000).isEmpty())
        assertTrue(fix(false, 3_000).isEmpty())
        assertTrue("exactly 3 s is still within the uncertainty window", fix(false, 4_000).isEmpty())
        val issues = fix(false, 5_000)
        assertEquals(listOf("map-held"), issues.map { it.code })
        assertTrue(issues.single().detail.contains("4 s"))
        assertTrue("reported once per 30 s, not every fix", fix(false, 6_000).isEmpty())
        fix(true, 7_000)
        assertTrue(d.summary().contains("map-held=1"))
        assertTrue(d.summary().contains("maxMapHoldMs=5000"))
    }

    @Test fun shortHoldsAreNotFlaggedAndFollowingResetsTheHold() {
        fix(false, 0); fix(false, 2_000); fix(true, 2_500)
        fix(false, 3_000); assertTrue(fix(false, 5_500).isEmpty())
        assertTrue(d.summary().contains("maxMapHoldMs=2500"))
    }

    @Test fun noFixesMeansNoMapSummaryAndResetClearsIt() {
        assertNull(d.mapFixRateHz())
        assertEquals("automatic checks found no navigation anomaly", d.summary())
        fix(true, 0); fix(true, 500)
        d.reset()
        assertEquals("automatic checks found no navigation anomaly", d.summary())
    }
}
