package com.drivemate.app.nav

import com.drivemate.app.data.Route
import com.drivemate.app.data.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Test

/** One noisy route projection cannot clear a maneuver; samples are synthetic, not road truth. */
class M03ConfirmedPassageTest {
    private val origin = LatLon(53.4, -2.2)
    private fun at(north: Double, east: Double = 0.0) = LatLon(
        origin.lat + north / 111_195.0,
        origin.lon + east / (111_195.0 * Math.cos(Math.toRadians(origin.lat))),
    )

    private fun engine(): NavigationEngine {
        val points = (0..20).map { at(it * 10.0) } + (1..10).map { at(200.0, it * 10.0) }
        val steps = listOf(
            RouteStep("Head north", "depart", "", "North Road", 200.0, at(0.0)),
            RouteStep("Turn right", "turn", "right", "East Road", 100.0, at(200.0)),
            RouteStep("Arrive", "arrive", "", "", 0.0, at(200.0, 100.0)),
        )
        return NavigationEngine(Route(points, 300.0, 30.0, steps, "synthetic"))
    }

    private fun denseEngine(): NavigationEngine {
        val points = (0..20).map { at(it * 10.0) } +
            (1..3).map { at(200.0, it * 10.0) } +
            (1..10).map { at(200.0 + it * 10.0, 30.0) }
        val steps = listOf(
            RouteStep("Head north", "depart", "", "North Road", 200.0, at(0.0)),
            RouteStep("Turn right", "turn", "right", "East Road", 30.0, at(200.0)),
            RouteStep("Turn left", "turn", "left", "North Road", 100.0, at(200.0, 30.0)),
            RouteStep("Arrive", "arrive", "", "", 0.0, at(300.0, 30.0)),
        )
        return NavigationEngine(Route(points, 330.0, 33.0, steps, "synthetic"))
    }

    @Test fun oneForwardOutlierDoesNotClearTheNextManeuver() {
        val engine = engine()
        assertEquals(1, engine.update(at(170.0), 0.0, 13.0).nextStep)

        // This fix projects 40 m beyond the turn. Progress remains at the approach until another
        // measured fix confirms the vehicle is still beyond the maneuver.
        val provisional = engine.update(at(200.0, 40.0), 90.0, 13.0)
        assertEquals(1, provisional.nextStep)
        assertEquals(4.0, provisional.distanceToNext, 0.01)
        assertEquals("Holding display progress does not alter the measured route distance",
            0.0, provisional.offRoute, 0.01)

        val confirmed = engine.update(at(200.0, 50.0), 90.0, 13.0)
        assertEquals(2, confirmed.nextStep)
    }

    @Test fun laterManeuverNeedsItsOwnConfirmedFix() {
        val engine = denseEngine()
        engine.update(at(170.0), 0.0, 0.5)
        assertEquals(1, engine.update(at(250.0, 30.0), 0.0, 0.5).nextStep)

        // This confirms the first turn, while leaving the second one provisional.
        assertEquals(2, engine.update(at(255.0, 30.0), 0.0, 0.5).nextStep)
        assertEquals(3, engine.update(at(260.0, 30.0), 0.0, 0.5).nextStep)
    }

    @Test fun aFalseForwardFixIsDiscardedWhenTheNextFixReturnsBeforeTheTurn() {
        val engine = engine()
        engine.update(at(170.0), 0.0, 13.0)
        assertEquals(1, engine.update(at(200.0, 40.0), 90.0, 13.0).nextStep)

        val corrected = engine.update(at(180.0), 0.0, 13.0)
        assertEquals(1, corrected.nextStep)
        assertEquals(20.0, corrected.distanceToNext, 1.0)
    }

    @Test fun repeatedStationaryFixCannotConfirmVehiclePassage() {
        val engine = engine()
        engine.update(at(170.0), 0.0, 0.0)
        repeat(5) {
            assertEquals(1, engine.update(at(200.0, 40.0), 90.0, 0.0).nextStep)
        }
        assertEquals(2, engine.update(at(200.0, 45.0), 90.0, 0.5).nextStep)
    }
}
