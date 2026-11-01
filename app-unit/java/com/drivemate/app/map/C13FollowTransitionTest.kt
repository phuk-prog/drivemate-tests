package com.drivemate.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Register 13.2: manual browsing is kept, and Re-centre and Follow transitions are exact. */
class C13FollowTransitionTest {

    @Test fun aMapGestureStopsNavigatingFollowUntilRecentre() {
        assertEquals(Follow.NONE, FollowTransitions.afterUserMove(Follow.NAVIGATE))
    }

    @Test fun aMapGestureDuringBrowsingIsManualBrowsingAndChangesNothing() {
        assertEquals(Follow.BROWSE, FollowTransitions.afterUserMove(Follow.BROWSE))
        assertEquals(Follow.NONE, FollowTransitions.afterUserMove(Follow.NONE))
    }

    @Test fun trackingDismissalAlwaysStopsFollowing() {
        assertEquals(Follow.NONE, FollowTransitions.afterTrackingDismissed())
    }

    @Test fun recentreFollowsTheCarAgainInTheModeTheTripNeeds() {
        assertEquals(Follow.NAVIGATE, FollowTransitions.afterRecentre(navigating = true))
        assertEquals(Follow.BROWSE, FollowTransitions.afterRecentre(navigating = false))
    }

    @Test fun manualBrowsingSurvivesFurtherMovesUntilRecentre() {
        var follow = FollowTransitions.afterTrackingDismissed()
        follow = FollowTransitions.afterUserMove(follow)
        assertEquals("a second drag while browsing stays browsing", Follow.NONE, follow)
        assertFalse(FollowSnapshot.of(follow, null, 17.0, emptyList(), 0).following)
        assertTrue(FollowSnapshot.of(follow, null, 17.0, emptyList(), 0).recentreShown)
        follow = FollowTransitions.afterRecentre(navigating = true)
        assertTrue(FollowSnapshot.of(follow, 0.0, 17.0, emptyList(), 0).following)
    }
}
