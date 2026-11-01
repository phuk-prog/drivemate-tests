package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 11.2 and 11.4 pure checks for a safe reroute: a lane change is claimed only from verified
 * connections, a junction is not passed before a fresh fix confirms it, and a declined alternative
 * is not offered again.
 */
class C11SafeRerouteTest {
    private fun verified(vararg links: LaneLink) = LaneConnectivity(links.toList(), DataSource.LANE_STRIP, 0.9)

    /** 11.2: unsupported lane exits: without verified connections no lane change is claimed. */
    @Test fun q11_2_unverifiedLaneExitMakesNoClaim() {
        assertTrue(LaneGuide.advise(LaneConnectivity.UNKNOWN, listOf(1)) is LaneAdvice.Unknown)
        val weak = LaneConnectivity(listOf(LaneLink(2, 1, 100.0)), DataSource.LANE_STRIP, 0.5)
        assertTrue("low confidence is not verified", LaneGuide.advise(weak, listOf(1)) is LaneAdvice.Unknown)
    }

    /** 11.2: a verified single change is the only lane change ever claimed; partial or doubtful links are not. */
    @Test fun q11_2_onlyVerifiedSingleChangeIsClaimed() {
        assertEquals(LaneAdvice.ChangeLane(2, 1, 100.0), LaneGuide.advise(verified(LaneLink(2, 1, 100.0)), listOf(1)))
        assertTrue("a recommended lane with no known way in is partly known",
            LaneGuide.advise(verified(LaneLink(2, 1, 100.0)), listOf(1, 3)) is LaneAdvice.Unknown)
        assertTrue("two possible changes into one lane are not one claim",
            LaneGuide.advise(verified(LaneLink(2, 1, 100.0), LaneLink(3, 1, 80.0)), listOf(1)) is LaneAdvice.Unknown)
        assertEquals(LaneAdvice.StayInLane(listOf(2)), LaneGuide.advise(verified(LaneLink(2, 2)), listOf(2)))
    }

    /** 11.2: premature junction advance: the position is held just before the unconfirmed manoeuvre. */
    @Test fun q11_2_gapNeverAdvancesPastAnUnconfirmedJunction() {
        val limit = GpsGapProgressPolicy.limitAtLoss(along = 100.0, distanceToNext = 50.0, total = 1_000.0)
        assertEquals(145.0, limit, 0.0)
        val held = GpsGapProgressPolicy.advance(140.0, limit, gapMs = 1_000L, elapsedSeconds = 1.0,
            freshCarSpeedMps = 20.0, lastGpsSpeedMps = 0.0)
        assertTrue("never past the junction without a fresh fix", held <= limit)
        val nearJunction = GpsGapProgressPolicy.limitAtLoss(along = 100.0, distanceToNext = 3.0, total = 1_000.0)
        assertEquals("inside the 5 m margin the position stays put", 100.0, nearJunction, 0.0)
    }

    /** 11.4: declined alternatives: an alternative that repeats a declined route is not offered again. */
    @Test fun q11_4_declinedAlternativeIsNotOfferedAgain() {
        fun offer(declined: List<Double>) = RouteSwitchPolicy.shouldOffer(
            currentSeconds = 1_200.0, savesSeconds = 300.0, overlapWithCurrent = 0.2,
            overlapsDeclined = declined, offerAlreadyVisible = false)
        assertFalse("nearly the same road as one declined", offer(listOf(0.9)))
        assertTrue("a different road is still offered", offer(listOf(0.3)))
        assertFalse("an offer already on screen is not repeated", RouteSwitchPolicy.shouldOffer(
            1_200.0, 300.0, 0.2, emptyList(), offerAlreadyVisible = true))
    }
}
