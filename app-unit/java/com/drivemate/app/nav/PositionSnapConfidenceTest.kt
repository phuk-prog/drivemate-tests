package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/** M03 route/parallel-road confidence checks; synthetic positions are not verified UK road truth. */
class PositionSnapConfidenceTest {
    private fun choose(
        route: Double, road: Double?, confidence: Double,
        verdict: RoadNetworkMatch.Verdict = RoadNetworkMatch.Verdict.UNKNOWN,
        headingOpposes: Boolean = false, accuracy: Double = 8.0,
    ): PositionSnap.Target = PositionSnap.choose(
        routeOffset = route, accuracy = accuracy, verdict = verdict,
        roadOffset = road, roadOnRoute = false, roadConfidence = confidence,
        headingOpposes = headingOpposes,
    )

    @Test fun doesNotJumpOntoUnsupportedParallelRoad() {
        assertEquals(PositionSnap.Target.RAW, choose(80.0, 2.0, 0.1))
        assertEquals(PositionSnap.Target.RAW, choose(80.0, 2.0, 0.0,
            RoadNetworkMatch.Verdict.OFF_ROUTE))
        assertEquals(PositionSnap.Target.RAW, choose(80.0, 45.0, 0.95))
        assertEquals(PositionSnap.Target.RAW, choose(80.0, Double.NaN, 0.99))
    }

    @Test fun highConfidenceNearbyDifferentRoadCanBeUsed() {
        assertEquals(PositionSnap.Target.MATCHED_ROAD, choose(50.0, 4.0, 0.9))
        assertEquals(PositionSnap.Target.MATCHED_ROAD, choose(50.0, 4.0, 0.9,
            RoadNetworkMatch.Verdict.OFF_ROUTE))
    }

    @Test fun anOffRouteVerdictNeverSnapsBackOntoTheRoute() {
        assertEquals(PositionSnap.Target.RAW, choose(10.0, 9.0, 0.3,
            RoadNetworkMatch.Verdict.OFF_ROUTE))
        assertEquals(PositionSnap.Target.RAW, choose(10.0, null, 0.8,
            RoadNetworkMatch.Verdict.OFF_ROUTE))
    }

    @Test fun opposingHeadingDoesNotTreatOppositeCarriagewayAsOwnRoute() {
        assertEquals(PositionSnap.Target.RAW, choose(6.0, null, 0.0,
            headingOpposes = true))
        assertEquals(PositionSnap.Target.ROUTE, choose(6.0, null, 0.0,
            headingOpposes = false))
    }

    @Test fun uncertaintyCloseToRouteDoesNotBlindlySwitchRoads() {
        assertEquals(PositionSnap.Target.ROUTE, choose(10.0, 2.0, 0.99))
        assertEquals(PositionSnap.Target.RAW, choose(Double.NaN, null, 0.9))
    }

    @Test fun corruptRouteDistanceOrAccuracyCannotCauseAnotherRoadSnap() {
        assertEquals(PositionSnap.Target.RAW, choose(Double.NaN, 2.0, 0.99))
        assertEquals(PositionSnap.Target.RAW, choose(Double.POSITIVE_INFINITY, 2.0, 0.99))
        assertEquals(PositionSnap.Target.RAW, choose(-1.0, 2.0, 0.99))
        assertEquals(PositionSnap.Target.RAW, choose(50.0, 2.0, 0.99, accuracy = Double.NaN))
        assertEquals(PositionSnap.Target.RAW, choose(50.0, 2.0, 0.99, accuracy = -2.0))
    }
}
