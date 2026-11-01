package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Category 6.1: counts only, no positions or routes in the telemetry. */
class FixTelemetryTest {
    @Test fun countsRawAcceptedAndRejectedFixesByReason() {
        val t = FixTelemetry()
        repeat(5) { t.onRaw() }
        t.onAccepted(ageMs = 100L, accuracyM = 5f)
        t.onAccepted(ageMs = 300L, accuracyM = 15f)
        t.onRejected("stale")
        t.onRejected("stale")
        t.onRejected("inaccurate")
        assertEquals(5L, t.raw)
        assertEquals(2L, t.accepted)
        assertEquals(mapOf("stale" to 2L, "inaccurate" to 1L), t.rejectedReasons())
    }

    @Test fun meansIgnoreMissingOrInvalidValues() {
        val t = FixTelemetry()
        assertNull(t.meanAgeMs())
        assertNull(t.meanAccuracyM())
        t.onAccepted(ageMs = 100L, accuracyM = 4f)
        t.onAccepted(ageMs = -1L, accuracyM = Float.NaN)
        t.onAccepted(ageMs = 300L, accuracyM = null)
        assertEquals(200.0, t.meanAgeMs()!!, 0.0001)
        assertEquals(4.0, t.meanAccuracyM()!!, 0.0001)
    }

    @Test fun summaryHoldsNoCoordinates() {
        val t = FixTelemetry()
        t.onRaw()
        t.onRejected("outlier")
        val text = t.summary()
        assertEquals("raw=1 accepted=0 rejected=1 reasons=outlier:1", text)
        assertFalse(Regex("[0-9]+\\.[0-9]{3,}").containsMatchIn(text))
    }
}
