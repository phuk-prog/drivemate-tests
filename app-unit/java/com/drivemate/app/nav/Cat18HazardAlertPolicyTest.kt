package com.drivemate.app.nav

import com.drivemate.app.nav.HazardAlertPolicy.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 18: selectable severity, voice for urgent events only, dated sources, fast expiry, de-duplication. */
class Cat18HazardAlertPolicyTest {
    @Test fun severityFilterShowsOnlyAtOrAboveTheSelectedMinimum() {
        assertFalse(HazardAlertPolicy.visible(Severity.WARNING, Severity.URGENT))
        assertTrue(HazardAlertPolicy.visible(Severity.URGENT, Severity.WARNING))
        assertTrue(HazardAlertPolicy.visible(Severity.INFO, Severity.INFO))
    }

    @Test fun onlyUrgentEventsAreSpoken() {
        assertTrue(HazardAlertPolicy.speak(Severity.URGENT))
        assertFalse(HazardAlertPolicy.speak(Severity.WARNING))
        assertFalse(HazardAlertPolicy.speak(Severity.INFO))
    }

    @Test fun datedSourcesAreFreshOnlyWithinTheirAgeLimit() {
        val now = 1_000_000L
        assertFalse("undated source is never fresh", HazardAlertPolicy.sourceFresh(null, now, 60_000L))
        assertTrue(HazardAlertPolicy.sourceFresh(now - 60_000L, now, 60_000L))
        assertFalse(HazardAlertPolicy.sourceFresh(now - 60_001L, now, 60_000L))
        assertFalse("a source dated in the future is not trusted", HazardAlertPolicy.sourceFresh(now + 1L, now, 60_000L))
        assertFalse(HazardAlertPolicy.sourceFresh(now, now, 0L))
    }

    @Test fun reportsExpireQuicklyAtTheirOwnWindow() {
        assertFalse(HazardAlertPolicy.expired(0L, 900_000L, 900_000L))
        assertTrue(HazardAlertPolicy.expired(0L, 900_001L, 900_000L))
    }

    @Test fun sameKindInOneSquareHasOneKeyAndDifferentKindsDoNot() {
        val a = HazardAlertPolicy.cellKey("CAMERA", 53.4000, -2.2000)
        val b = HazardAlertPolicy.cellKey("CAMERA", 53.40001, -2.20001)
        assertEquals(a, b)
        assertFalse(a == HazardAlertPolicy.cellKey("SCHOOL", 53.4000, -2.2000))
        assertFalse("about 1 km away is a different square", a == HazardAlertPolicy.cellKey("CAMERA", 53.409, -2.2000))
    }

    @Test fun badPositionsHaveNoKey() {
        assertNull(HazardAlertPolicy.cellKey("CAMERA", Double.NaN, -2.2))
        assertNull(HazardAlertPolicy.cellKey("CAMERA", 91.0, -2.2))
        assertNull(HazardAlertPolicy.cellKey("CAMERA", 53.4, 181.0))
    }

    @Test fun dedupeKeepsTheNewestReportPerKeyAndKeepsUnkeyedItems() {
        data class R(val name: String, val key: String?, val at: Long)
        val items = listOf(
            R("old", "k1", 10L),
            R("new", "k1", 20L),
            R("other", "k2", 5L),
            R("nokey", null, 1L),
        )
        val kept = HazardAlertPolicy.dedupe(items, { it.key }, { it.at }).map { it.name }
        assertEquals(listOf("new", "other", "nokey"), kept)
    }
}
