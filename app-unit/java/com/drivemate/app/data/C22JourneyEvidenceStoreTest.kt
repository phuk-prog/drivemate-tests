package com.drivemate.app.data

import com.drivemate.app.nav.EvidenceFix
import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Category 22.1/22.2: route recording is off by default, records only after opt-in, and deletion removes it. */
class C22JourneyEvidenceStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val day = 86_400_000L
    private var now = 1_800_000_000_000L
    private fun store() = JourneyEvidenceStore(folder.root.resolve("journey-evidence").apply { mkdirs() }) { now }
    private fun fix(atMs: Long, lat: Double = 53.40, lon: Double = -2.20) = EvidenceFix(LatLon(lat, lon), atMs, 5.0, null)

    @Test fun recordingIsOffByDefaultAndNothingIsKept() {
        val s = store()
        assertFalse(s.enabled())
        assertNull(s.start(now))
        assertTrue(s.all().isEmpty())
    }

    @Test fun afterOptInOnlyTheDrivenPositionsAreKept() {
        val s = store()
        s.setEnabled(true)
        val id = requireNotNull(s.start(now))
        s.capture(id, fix(now + 1_000), emptyList())
        val journey = requireNotNull(s.load(id))
        assertEquals(1, journey.samples.size)
        assertEquals(53.40, journey.samples[0].fix.position.lat, 1e-9)
    }

    @Test fun captureIsThrottledToOneFixEvery15Seconds() {
        val s = store()
        s.setEnabled(true)
        val id = s.start(now)!!
        s.capture(id, fix(now + 1_000), emptyList())
        s.capture(id, fix(now + 6_000), emptyList())   // 5 s after the last kept fix: ignored
        s.capture(id, fix(now + 21_000), emptyList())  // 20 s after: kept
        assertEquals(2, s.load(id)!!.samples.size)
    }

    @Test fun invalidPositionsAreRejected() {
        val s = store()
        s.setEnabled(true)
        val id = s.start(now)!!
        s.capture(id, fix(now + 1_000, lat = 95.0), emptyList())
        s.capture(id, fix(now + 20_000, lon = Double.NaN), emptyList())
        assertTrue(s.load(id)!!.samples.isEmpty())
    }

    @Test fun partialJourneyHasNoEndUntilItCompletes() {
        val s = store()
        s.setEnabled(true)
        val id = s.start(now)!!
        assertNull(s.load(id)!!.endedAtMs)
        s.complete(id, now + 600_000)
        assertEquals(now + 600_000, s.load(id)!!.endedAtMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun completionBeforeTheStartIsRefused() {
        val s = store()
        s.setEnabled(true)
        val id = s.start(now)!!
        s.complete(id, now - 1)
    }

    @Test fun turningRecordingOffDeletesJourneysButKeepsTheSetting() {
        val s = store()
        s.setEnabled(true)
        val id = s.start(now)!!
        s.capture(id, fix(now + 1_000), emptyList())
        s.setEnabled(false)
        assertFalse(s.enabled())
        assertTrue(s.all().isEmpty())
        assertNull(s.load(id))
    }

    @Test fun journeysOlderThanTheRetentionPeriodAreRemoved() {
        val s = store()
        s.setEnabled(true)
        val id = s.start(now)!!
        now += 8 * day   // default retention is 7 days
        assertTrue(s.all().isEmpty())
        assertNull(s.load(id))
    }

    @Test fun deleteOlderThanRemovesOnlyJourneysBeforeTheCutoff() {
        val s = store()
        s.setEnabled(true)
        s.setRetentionDays(30)   // the largest retention the app allows, so the older journey is still kept
        val old = requireNotNull(s.start(now - 25 * day))
        val recent = requireNotNull(s.start(now - 2 * day))
        s.deleteOlderThan(7, now)
        assertNull(s.load(old))
        requireNotNull(s.load(recent))
        assertTrue(s.enabled())
    }

    @Test fun deleteAllKeepsTheRecordingSetting() {
        val s = store()
        s.setEnabled(true)
        s.start(now)
        assertTrue(s.deleteAll())
        assertTrue(s.all().isEmpty())
        assertTrue(s.enabled())
    }
}
