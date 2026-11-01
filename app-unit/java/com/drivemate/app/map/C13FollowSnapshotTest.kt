package com.drivemate.app.map

import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Register 13.1: the pure snapshot of follow state, heading, zoom and remaining route geometry. */
class C13FollowSnapshotTest {
    private val route = (0 until 5).map { LatLon(53.0 + it * 0.001, -2.0) }

    @Test fun navigatingFollowsTheCarAndHidesRecentre() {
        val s = FollowSnapshot.of(Follow.NAVIGATE, 90.0, 17.0, route, 1)
        assertTrue(s.following)
        assertFalse(s.recentreShown)
        assertEquals(90.0, s.heading!!, 1e-9)
        assertEquals(17.0, s.zoom, 1e-9)
    }

    @Test fun browsingIsFollowingButKeepsTheRouteFromTheCar() {
        val s = FollowSnapshot.of(Follow.BROWSE, null, 15.5, route, 2)
        assertTrue(s.following)
        assertFalse(s.recentreShown)
        assertNull(s.heading)
    }

    @Test fun dismissedFollowShowsRecentre() {
        val s = FollowSnapshot.of(Follow.NONE, 0.0, 16.0, route, 0)
        assertFalse(s.following)
        assertTrue(s.recentreShown)
    }

    @Test fun remainingGeometryStartsAtTheCarIndex() {
        assertEquals(route.drop(3), FollowSnapshot.of(Follow.NAVIGATE, 0.0, 17.0, route, 3).remaining)
    }

    @Test fun carIndexOutsideTheRouteIsClamped() {
        assertEquals(route, FollowSnapshot.of(Follow.NAVIGATE, 0.0, 17.0, route, -4).remaining)
        assertTrue(FollowSnapshot.of(Follow.NAVIGATE, 0.0, 17.0, route, 99).remaining.isEmpty())
    }

    @Test fun emptyRouteGivesNoRemainingGeometry() {
        assertTrue(FollowSnapshot.of(Follow.BROWSE, null, 15.5, emptyList(), 0).remaining.isEmpty())
    }

    @Test fun headingIsNormalisedAndNonFiniteHeadingIsUnknown() {
        assertEquals(270.0, FollowSnapshot.of(Follow.NAVIGATE, -90.0, 17.0, route, 0).heading!!, 1e-9)
        assertEquals(10.0, FollowSnapshot.of(Follow.NAVIGATE, 370.0, 17.0, route, 0).heading!!, 1e-9)
        assertNull(FollowSnapshot.of(Follow.NAVIGATE, Double.NaN, 17.0, route, 0).heading)
    }

    @Test fun snapshotCopiesTheRouteSoLaterChangesDoNotLeakIn() {
        val mutable = route.toMutableList()
        val s = FollowSnapshot.of(Follow.NAVIGATE, 0.0, 17.0, mutable, 0)
        mutable.clear()
        assertEquals(route, s.remaining)
    }
}
