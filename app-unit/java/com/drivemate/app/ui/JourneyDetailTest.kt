package com.drivemate.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Journey review 9: no "Your speeds" label under the arrival time (owner: "Hide it"); stops still shown. */
class JourneyDetailTest {
    @Test fun noArrivalSourceLabel() {
        assertNull("nothing to show without stops", journeyDetail(0))
    }

    @Test fun stopsStillShown() {
        assertEquals("1 stop", journeyDetail(1))
        assertEquals("3 stops", journeyDetail(3))
    }
}
