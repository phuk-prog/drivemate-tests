package com.drivemate.app.nav

import com.drivemate.app.data.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C14.1/C14.3: arrival only at the real end of the route, from a trustworthy measured fix. */
class C14ArrivalPolicyTest {
    @Test fun atTheRouteEndOrAtTheDestinationItselfCountsAsArrived() {
        assertTrue(ArrivalPolicy.arrived(20.0, 10.0, 40.0, estimating = false, speedMps = 0.0))
        assertTrue("at the pin, near the route end", ArrivalPolicy.arrived(120.0, 30.0, 15.0, false, speedMps = 0.0))
    }

    @Test fun sixtyYardsShortOrOnANearbyRoadIsNotArrived() {
        assertFalse("~60 yd short (Menai Grove report)", ArrivalPolicy.arrived(55.0, 5.0, 55.0, false, speedMps = 0.0))
        assertFalse("near the end but on another road", ArrivalPolicy.arrived(20.0, 40.0, 30.0, false, speedMps = 0.0))
        assertFalse("passing the pin on the far carriageway", ArrivalPolicy.arrived(400.0, 5.0, 15.0, false, speedMps = 0.0))
    }

    @Test fun neverFromAnEstimateOrAnUnsafeFix() {
        assertFalse(ArrivalPolicy.arrived(5.0, 2.0, 5.0, estimating = true, speedMps = 0.0))
        assertFalse("GPS gap or rough fix", ArrivalPolicy.arrived(5.0, 2.0, 5.0, false, positionSafe = false, speedMps = 0.0))
        assertFalse(ArrivalPolicy.arrived(Double.NaN, 2.0, 5.0, false, speedMps = 0.0))
    }

    @Test fun sideComesOnlyFromTheArrivalStep() {
        fun step(type: String, modifier: String) = RouteStep("", type, modifier, "", 0.0, LatLon(0.0, 0.0))
        assertEquals("left", ArrivalPolicy.side(listOf(step("turn", "right"), step("arrive", "left"))))
        assertNull("last turn's side is not the destination's", ArrivalPolicy.side(listOf(step("turn", "left"))))
        assertNull("composite modifier is unknown", ArrivalPolicy.side(listOf(step("arrive", "slight left"))))
    }
}
