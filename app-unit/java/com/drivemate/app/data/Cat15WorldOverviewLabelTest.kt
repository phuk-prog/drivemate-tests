package com.drivemate.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 1.5: the worldwide overview shows its size, its offline state, and that it is not navigable. */
class Cat15WorldOverviewLabelTest {
    private val bytes = 2_400_000_000L

    @Test fun downloadChoiceShowsSizeBeforeAnyDownload() {
        val line = WorldOverviewLabel.downloadLine(bytes)
        assertTrue(line.contains("download 2.4 GB"))
        assertTrue(line.contains("not on this phone yet"))
    }

    @Test fun onlyVerifiedOverviewIsOfflineReady() {
        assertTrue(WorldOverviewLabel.statusLine(bytes, installed = true, verified = true).contains("offline-ready"))
        val notVerified = WorldOverviewLabel.statusLine(bytes, installed = true, verified = false)
        assertFalse(notVerified.contains("offline-ready"))
        assertTrue(notVerified.contains("the UK map is used"))
    }

    @Test fun everyStateSaysTheOverviewIsNotNavigable() {
        val states = listOf(
            WorldOverviewLabel.downloadLine(bytes),
            WorldOverviewLabel.statusLine(bytes, installed = true, verified = false),
            WorldOverviewLabel.statusLine(bytes, installed = true, verified = true),
        )
        states.forEach { assertTrue(it, it.contains(WorldOverviewLabel.NOT_NAVIGABLE)) }
    }

    @Test fun sourceIsWorldOnlyWhenEnabledAndVerified() {
        assertEquals("Map source: worldwide overview under UK roads", WorldOverviewLabel.sourceLine(true, true))
        assertEquals("Map source: UK map only", WorldOverviewLabel.sourceLine(true, false))
        assertEquals("Map source: UK map only", WorldOverviewLabel.sourceLine(false, true))
    }
}
