package com.drivemate.tests.cat06

import com.drivemate.app.data.RoadAlerts
import org.junit.Assert.*
import org.junit.Test

/** GOV.UK /speed-limits and GOV.WALES 20mph FAQ; ordinary car limits, not vehicle-specific advice. */
class SpeedLimitsLawTest {
    @Test fun explicitNationalCarLimitsUseSixtyAndSeventyMph() {
        assertEquals(60 * 1.609344, RoadAlerts.parseSpeed("GB:nsl_single")!!, 0.00001)
        assertEquals(70 * 1.609344, RoadAlerts.parseSpeed("GB:nsl_dual")!!, 0.00001)
        assertEquals(70 * 1.609344, RoadAlerts.parseSpeed("GB:motorway")!!, 0.00001)
    }
    @Test fun welshRestrictedRoadCodeUsesTwentyAndSignedExceptionRemainsThirty() {
        assertEquals(20 * 1.609344, RoadAlerts.parseSpeed("GB:WLS:urban")!!, 0.00001)
        assertEquals(30 * 1.609344, RoadAlerts.parseSpeed("30 mph")!!, 0.00001)
        assertEquals(30 * 1.609344, RoadAlerts.parseSpeed("GB:ENG:urban")!!, 0.00001)
    }
}
