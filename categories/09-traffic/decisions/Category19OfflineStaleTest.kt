package com.drivemate.tests.cat09

import com.drivemate.app.data.Hazard
import com.drivemate.app.data.HazardType
import com.drivemate.app.data.LiveTrafficInfo
import com.drivemate.app.data.TrafficDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.drivemate.app.nav.LatLon

/** Category 19 (19.5): offline, stale or rapidly changing provider answers never keep an old report as current. */
class Category19OfflineStaleTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private val pos = LatLon(53.4, -2.2)

    private fun live(id: Long, source: String, checkedAt: Long) =
        Hazard(id, HazardType.CRASH, pos, checkedAt, live = LiveTrafficInfo(source, checkedAt))

    @Test fun q19_5_refreshedSourceReplacesItsOwnOldReports() {
        val old = listOf(live(1, "TomTom", now - 2 * min))
        val fresh = listOf(live(2, "TomTom", now))
        assertEquals(listOf(2L), TrafficDetails.mergeLive(old, fresh, setOf("TomTom"), now).map { it.id })
    }

    @Test fun q19_5_sourceThatFailedKeepsItsReportOnlyUntilStale() {
        val recent = listOf(live(1, "TfL", now - 2 * min))
        val stale = listOf(live(3, "TfL", now - (TrafficDetails.LIVE_MAX_AGE + min)))
        // TfL was not asked this round (or failed): its recent report stays, its stale one is dropped.
        assertEquals(listOf(1L), TrafficDetails.mergeLive(recent + stale, emptyList(), setOf("TomTom"), now).map { it.id })
    }

    @Test fun q19_5_reportWithoutItsSourceIsNeverKept() {
        val anonymous = Hazard(4, HazardType.CRASH, pos, now, live = LiveTrafficInfo("", now))
        assertTrue(TrafficDetails.mergeLive(listOf(anonymous), emptyList(), emptySet(), now).isEmpty())
    }

    @Test fun q19_5_staleFeedIsReportedAsUnknownNotAsNoProblems() {
        val feeds = TrafficDetails.FeedState()
        feeds.success("TomTom", now - (TrafficDetails.LIVE_MAX_AGE + min))
        assertEquals("Live traffic reports unavailable · problems ahead unknown",
            feeds.summary(listOf("TomTom"), 0, now))
    }

    @Test fun q19_5_failedRefreshAfterSuccessIsVisible() {
        val feeds = TrafficDetails.FeedState()
        feeds.success("TomTom", now - 2 * min)
        feeds.failure("TomTom", now - min)
        val s = feeds.summary(listOf("TomTom"), 1, now)
        assertTrue(s, s.contains("latest TomTom check failed"))
        assertFalse(s.startsWith("No traffic problems"))
    }

    @Test fun q19_5_unknownAgeIsNeverStale() {
        assertFalse(TrafficDetails.stale(null as LiveTrafficInfo?, now))
        assertTrue(TrafficDetails.stale(LiveTrafficInfo("TomTom", 0L), now))
    }
}
