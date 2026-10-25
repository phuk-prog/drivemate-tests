package com.drivemate.tests.cat01

import com.drivemate.app.data.LandmarkKind
import com.drivemate.app.data.Place
import com.drivemate.app.data.RoadAlerts
import com.drivemate.app.data.Route
import com.drivemate.app.data.RouteStep
import com.drivemate.app.nav.BackgroundGuidance
import com.drivemate.app.nav.Format
import com.drivemate.app.nav.LaneTiming
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.MiniRoundabouts
import com.drivemate.app.nav.NavigationEngine
import com.drivemate.app.nav.TurnInstructions
import com.drivemate.app.nav.arrivalApproachInstruction
import com.drivemate.app.nav.roundaboutCirculationInstruction
import com.drivemate.app.nav.stopLabel
import com.drivemate.app.nav.stopReached
import com.drivemate.app.ui.turnBannerNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.junit.Test

/**
 * Category 1, decision checks: the app does what is decided in
 * drivemate-app/register/01-turn-by-turn-guidance.md. Each test names its question.
 */
class TurnByTurnDecisionsTest {
    private val uk = Format(imperial = true, yards = true)

    /** 1.2, 1.3: yards in 10s under 100 yd, in 50s to ¼ mile, miles to one decimal, whole from 10. */
    @Test fun q1_2_and_1_3_distanceSteps() {
        assertEquals("50 yd", uk.distance(50.0))
        assertEquals("90 yd", uk.distance(80.0))
        assertEquals("350 yd", uk.distance(300.0))
        assertEquals("0.3 mi", uk.distance(500.0))
        assertEquals("1.2 mi", uk.distance(1_931.0))
        assertEquals("15 mi", uk.distance(24_140.0))
    }

    /** 1.4: "Now" about 5 seconds before the turn at your speed, and never further than that. */
    @Test fun q1_4_nowAboutFiveSeconds() {
        assertTrue(turnBannerNow(false, "turn", 150.0, 30f, 1_000))
        assertFalse(turnBannerNow(false, "turn", 160.0, 30f, 1_000))
        assertTrue("slow: at least ~50 yd", turnBannerNow(false, "turn", 45.0, 5f, 1_000))
        assertFalse(turnBannerNow(false, "turn", 50.0, 5f, 1_000))
        assertFalse("not for arriving", turnBannerNow(false, "arrive", 10.0, 5f, 1_000))
    }

    /** 1.5: the turn arrow is always shown, however far the next move is. */
    @Test fun q1_5_arrowAlwaysShown() {
        assertTrue(LaneTiming.arrowShown(false, "turn", 40_000.0, onRoundabout = false, fastRoad = true))
        assertTrue(LaneTiming.arrowShown(false, "turn", 2_000.0, onRoundabout = false, fastRoad = false))
        assertTrue(LaneTiming.arrowShown(false, "arrive", 5_000.0, onRoundabout = false, fastRoad = false))
        assertFalse("not once arrived", LaneTiming.arrowShown(true, "arrive", 0.0, onRoundabout = false, fastRoad = false))
    }

    /** 1.6: "Slight left" (not "Bear left"); other turns as before. */
    @Test fun q1_6_turnWording() {
        assertEquals("Slight left onto Station Road", TurnInstructions.describe("turn", "slight left", "Station Road", 0, true))
        assertEquals("Slight right", TurnInstructions.describe("turn", "slight right", "", 0, true))
        assertEquals("Turn sharp left onto Mill Lane", TurnInstructions.describe("turn", "sharp left", "Mill Lane", 0, true))
        assertEquals("Keep left at the fork", TurnInstructions.describe("fork", "slight left", "", 0, true))
        assertEquals("Make a U-turn", TurnInstructions.describe("turn", "uturn", "", 0, true))
    }

    /** 1.7: "the M60" for road numbers; the sign destination on slip roads and forks. */
    @Test fun q1_7_roadNames() {
        assertEquals("the M60", TurnInstructions.theRoad("M60"))
        assertEquals("Station Road", TurnInstructions.theRoad("Station Road"))
        assertEquals("Take the exit on the left towards A34 Oxford",
            TurnInstructions.describe("off ramp", "slight left", "A34", 0, true, "A34 Oxford"))
        assertEquals("Turn left onto Station Road", TurnInstructions.describe("turn", "left", "Station Road", 0, true, "Stockport"))
    }

    /** 1.8, 1.16: exit number only; normal roundabout wording unless the map is sure it is a mini. */
    @Test fun q1_8_and_1_16_roundaboutWording() {
        assertEquals("At the roundabout, take the second exit onto Station Road",
            TurnInstructions.describe("roundabout", "right", "Station Road", 2, true))
    }

    /** A straight test road north from (53.40, -2.20): [m] metres along it, [e] metres east. */
    private fun road(m: Double, e: Double = 0.0) = LatLon(53.40 + m / 111_195.0, -2.20 + e / (111_195.0 * Math.cos(Math.toRadians(53.40))))

    /** 800 m north on Mill Lane, then right onto Station Road at 800 m, 400 m east. */
    private fun testRoute(): Route {
        val points = (0..80).map { road(it * 10.0) } + (1..40).map { road(800.0, it * 10.0) }
        return Route(points, 1_200.0, 120.0, listOf(
            RouteStep("Head out on Mill Lane", "depart", "", "Mill Lane", 800.0, road(0.0), duration = 80.0),
            RouteStep("Turn right onto Station Road", "turn", "right", "Station Road", 400.0, road(800.0), duration = 40.0),
            RouteStep("You have arrived", "arrive", "", "", 0.0, road(800.0, 400.0)),
        ), "test")
    }

    /** 1.9: a turn at a mini-roundabout the map marks: "At the mini-roundabout, turn right". */
    @Test fun q1_9_turnAtMiniRoundabout() {
        val marked = MiniRoundabouts.mark(testRoute(), listOf(road(803.0)))
        val turn = marked.steps[1]
        assertTrue(turn.miniRoundabout)
        assertEquals("At the mini-roundabout, turn right onto Station Road", turn.instruction)
        assertEquals("the card's words", "At the mini-roundabout, turn right", TurnInstructions.miniRoundabout("right", ""))
        assertEquals("At the mini-roundabout, turn left", TurnInstructions.miniRoundabout("slight left", ""))
        assertEquals("At the mini-roundabout, make a U-turn", TurnInstructions.miniRoundabout("uturn", ""))
        assertEquals("no extra moves", 3, marked.steps.size)
    }

    /** 1.18: straight over a mini-roundabout: a move is added, "At the mini-roundabout, go straight on". */
    @Test fun q1_18_straightOverMiniRoundabout() {
        val route = testRoute()
        val marked = MiniRoundabouts.mark(route, listOf(road(300.0)))
        assertEquals(4, marked.steps.size)
        val over = marked.steps[1]
        assertTrue(over.miniRoundabout)
        assertEquals("At the mini-roundabout, go straight on", over.instruction)
        assertEquals(300.0, marked.steps[0].distance, 15.0)
        assertEquals("the route is no longer or shorter", route.steps.sumOf { it.distance }, marked.steps.sumOf { it.distance }, 0.5)
        val engine = NavigationEngine(marked)
        assertTrue("kept as a move of its own", engine.isGuidance(1))
        var progress = engine.update(road(150.0), 0.0, 13.0)
        progress = engine.update(road(160.0), 0.0, 13.0)
        progress = engine.update(road(170.0), 0.0, 13.0)
        assertEquals("the next move is the mini-roundabout", 1, progress.nextStep)
    }

    /** 1.16: only mini-roundabouts the map marks, on the route itself; otherwise the route is as it was. */
    @Test fun q1_16_onlyMarkedMiniRoundabouts() {
        val route = testRoute()
        assertEquals(route, MiniRoundabouts.mark(route, emptyList()))
        assertEquals("40 m off the route: not on it", route, MiniRoundabouts.mark(route, listOf(road(300.0, 40.0))))
        assertEquals("At the roundabout, take the second exit onto Station Road",
            TurnInstructions.describe("roundabout", "right", "Station Road", 2, true))
    }

    /** 1.9: the road information on the phone carries the map's mini-roundabouts. */
    @Test fun q1_9_roadInfoReadsMiniRoundabouts() {
        val elements = JSONArray("""[{"type":"node","id":13,"lat":53.40270,"lon":-2.20000,"tags":{"highway":"mini_roundabout"}}]""")
        val found = RoadAlerts.fromOwnData(elements, listOf(road(0.0), road(800.0))).landmarks
        assertEquals(listOf(LandmarkKind.MINI_ROUNDABOUT), found.map { it.kind })
    }

    /** 1.10: on the roundabout, the exit number until the last 60 m, then "Take the exit". */
    @Test fun q1_10_onTheRoundabout() {
        assertEquals("Take the 2nd exit onto Station Road", roundaboutCirculationInstruction(2, 100.0, "Station Road"))
        assertEquals("Take the exit onto Station Road", roundaboutCirculationInstruction(2, 50.0, "Station Road"))
        assertEquals("unknown exit: never an ordinal", "Take the exit", roundaboutCirculationInstruction(0, 200.0, ""))
    }

    /**
     * 1.11: the "Then" chip shows the move after next when it comes within about 30 seconds at your
     * speed (never closer than ~150 m or further than ~900 m). An L-shaped test route: 1 km north,
     * right, [gap] m east, left, 500 m north; the car is 200 m before the first turn.
     */
    private fun thenShown(gap: Double, speed: Double): Boolean {
        val start = LatLon(53.40, -2.20)
        val north = 1.0 / 111_195.0
        val east = 1.0 / (111_195.0 * Math.cos(Math.toRadians(53.40)))
        fun at(n: Double, e: Double) = LatLon(start.lat + n * north, start.lon + e * east)
        val points = (0..100).map { at(it * 10.0, 0.0) } +
            (1..(gap / 10).toInt()).map { at(1_000.0, it * 10.0) } +
            (1..50).map { at(1_000.0 + it * 10.0, gap) }
        val steps = listOf(
            RouteStep("Head out", "depart", "", "Mill Lane", 1_000.0, at(0.0, 0.0)),
            RouteStep("Turn right onto Station Road", "turn", "right", "Station Road", gap, at(1_000.0, 0.0)),
            RouteStep("Turn left onto High Street", "turn", "left", "High Street", 500.0, at(1_000.0, gap)),
            RouteStep("You have arrived", "arrive", "", "", 0.0, at(1_500.0, gap)),
        )
        val engine = NavigationEngine(Route(points, 1_500.0 + gap, (1_500.0 + gap) / speed, steps, "test"))
        var progress = engine.update(at(780.0, 0.0), 0.0, speed)
        progress = engine.update(at(790.0, 0.0), 0.0, speed)
        progress = engine.update(at(800.0, 0.0), 0.0, speed)
        assertEquals("the next move is the first turn", 1, progress.nextStep)
        return progress.thenStep == 2
    }

    @Test fun q1_11_thenChipAbout30Seconds() {
        assertTrue("13 m/s (about 30 mph), turns 300 m apart", thenShown(300.0, 13.0))
        assertFalse("13 m/s, turns 500 m apart", thenShown(500.0, 13.0))
        assertTrue("30 m/s (about 67 mph), turns 800 m apart", thenShown(800.0, 30.0))
        assertFalse("30 m/s, turns 950 m apart: never beyond ~900 m", thenShown(950.0, 30.0))
        assertTrue("walking pace: the window never drops below 30 s at 8 m/s (240 m)", thenShown(200.0, 1.0))
    }

    /** 1.12: side of the road when known, "ahead" when not. */
    @Test fun q1_12_destination() {
        assertEquals("Destination on the left", arrivalApproachInstruction("arrive", "left"))
        assertEquals("Destination ahead", arrivalApproachInstruction("arrive", null))
        assertNull(arrivalApproachInstruction("arrive", "left", arrived = true))
        assertEquals("Your destination is on the right", TurnInstructions.describe("arrive", "right", "", 0, true))
        assertEquals("You have arrived at your destination", TurnInstructions.describe("arrive", "", "", 0, true))
    }

    /** 1.13: stops are named: "Shell, Station Road on the left", then "You've reached Shell". */
    @Test fun q1_13_namedStops() {
        val shell = Place("Shell", "Station Road, Stockport", LatLon(53.4, -2.15))
        assertEquals("Shell, Station Road on the left", arrivalApproachInstruction("stop", "left", stop = stopLabel(shell, "Station Road")))
        assertEquals("You've reached Shell", stopReached(shell, "Station Road"))
        assertEquals("street already in the name", "24 Station Road", stopLabel(Place("24 Station Road", "", LatLon(0.0, 0.0)), "Station Road"))
        assertEquals("not the destination's words", "Destination on the left", arrivalApproachInstruction("arrive", "left", stop = "Shell"))
    }

    /** 1.17: a stop with no name goes by its street; "your stop" with no street either. */
    @Test fun q1_17_unnamedStops() {
        val pin = Place("Dropped pin", "53.40000, -2.15000", LatLon(53.4, -2.15))
        val postcode = Place("SK1 3AB", "Stockport", LatLon(53.4, -2.15), kind = "postcode")
        assertEquals("Station Road on the left", arrivalApproachInstruction("stop", "left", stop = stopLabel(pin, "Station Road")))
        assertEquals("Station Road ahead", arrivalApproachInstruction("stop", "", stop = stopLabel(postcode, "Station Road")))
        assertEquals("You've reached Station Road", stopReached(pin, "Station Road"))
        assertNull(stopLabel(pin, ""))
        assertEquals("Stop on the right", arrivalApproachInstruction("stop", "right", stop = stopLabel(pin, "")))
        assertEquals("You have reached your stop", stopReached(pin, ""))
    }

    /** 1.14: "Head out on …", no compass direction. */
    @Test fun q1_14_start() {
        assertEquals("Head out on Station Road", TurnInstructions.describe("depart", "", "Station Road", 0, true))
        assertEquals("Head out", TurnInstructions.describe("depart", "", "", 0, true))
    }

    /** 1.15: guidance keeps going in the background; a warning only when something is blocked. */
    @Test fun q1_15_background() {
        val ready = BackgroundGuidance.status(true, false, true, true, 10.0)
        assertEquals(BackgroundGuidance.Status.READY, ready)
        assertNull(BackgroundGuidance.message(ready))
        assertEquals(BackgroundGuidance.Status.STARTING, BackgroundGuidance.status(true, false, false, true, 2.0))
        val blocked = BackgroundGuidance.status(true, false, false, true, 10.0)
        assertEquals(BackgroundGuidance.Status.BLOCKED, blocked)
        assertTrue(BackgroundGuidance.message(blocked)!!.startsWith("Guidance may stop"))
        assertEquals(BackgroundGuidance.Status.NO_NOTIFICATIONS, BackgroundGuidance.status(true, false, true, false, 10.0))
    }
}
