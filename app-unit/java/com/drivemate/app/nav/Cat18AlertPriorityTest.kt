package com.drivemate.app.nav

import com.drivemate.app.nav.HazardAlertPolicy.Prompt
import com.drivemate.app.nav.HazardAlertPolicy.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 18.2 to 18.4: relevance gating, conservative prompt priority, non-obstructive display and dismissal. */
class Cat18AlertPriorityTest {
    private val now = 1_000_000L

    @Test fun warningIsRelevantOnlyOnRouteDirectionSeverityAndFreshness() {
        fun rel(onRoute: Boolean = true, heading: Boolean = true, sev: Severity = Severity.WARNING, date: Long? = now - 1_000L) =
            HazardAlertPolicy.relevant(sev, Severity.INFO, date, now, 60_000L, onRoute, heading)
        assertTrue(rel())
        assertFalse("off route", rel(onRoute = false))
        assertFalse("other direction", rel(heading = false))
        assertFalse("stale source", rel(date = now - 60_001L))
        assertFalse("undated source", rel(date = null))
        assertFalse("below selected severity", HazardAlertPolicy.relevant(Severity.INFO, Severity.WARNING, now, now, 60_000L, true, true))
    }

    @Test fun manoeuvreThenHazardThenSpeedThenWeather() {
        assertEquals("turn", HazardAlertPolicy.winner(listOf(Prompt.WEATHER to "rain", Prompt.MANOEUVRE to "turn", Prompt.HAZARD to "deer")))
        assertEquals("deer", HazardAlertPolicy.winner(listOf(Prompt.WEATHER to "rain", Prompt.SPEED to "30", Prompt.HAZARD to "deer")))
        assertEquals("30", HazardAlertPolicy.winner(listOf(Prompt.WEATHER to "rain", Prompt.SPEED to "30")))
        assertNull(HazardAlertPolicy.winner(emptyList<Pair<Prompt, String>>()))
    }

    @Test fun equalPriorityKeepsTheEarlierPrompt() {
        assertEquals("first", HazardAlertPolicy.winner(listOf(Prompt.HAZARD to "first", Prompt.HAZARD to "second")))
    }

    @Test fun weatherIsShownOnlyWhileItsSourceIsFresh() {
        assertTrue(HazardAlertPolicy.weatherShown(sourceFresh = true, hasWarning = true))
        assertFalse("outage never reads as clear weather", HazardAlertPolicy.weatherShown(sourceFresh = false, hasWarning = true))
        assertFalse(HazardAlertPolicy.weatherShown(sourceFresh = true, hasWarning = false))
    }

    @Test fun urgentWarningsHaveNoTimerWhileAhead() {
        assertNull(HazardAlertPolicy.displayMs(Severity.URGENT))
        assertTrue(HazardAlertPolicy.stillShown(Severity.URGENT, 0L, Long.MAX_VALUE / 2, passed = false))
        assertFalse(HazardAlertPolicy.stillShown(Severity.URGENT, 0L, 1L, passed = true))
    }

    @Test fun nonUrgentWarningsLeaveWhenTheirTimeIsUp() {
        assertTrue(HazardAlertPolicy.stillShown(Severity.WARNING, 0L, 12_000L, passed = false))
        assertFalse(HazardAlertPolicy.stillShown(Severity.WARNING, 0L, 12_001L, passed = false))
        assertFalse(HazardAlertPolicy.stillShown(Severity.INFO, 0L, 1L, passed = true))
    }

    @Test fun urgentWarningCannotBeDismissedWhileStillAhead() {
        assertFalse(HazardAlertPolicy.canDismiss(Severity.URGENT, passed = false))
        assertTrue(HazardAlertPolicy.canDismiss(Severity.URGENT, passed = true))
        assertTrue(HazardAlertPolicy.canDismiss(Severity.WARNING, passed = false))
    }
}
