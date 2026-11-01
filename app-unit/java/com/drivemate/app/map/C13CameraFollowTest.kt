package com.drivemate.app.map

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.RouteGeometryIndex
import com.drivemate.app.nav.HeadingEstimator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C13.1 / C13.2 (pure logic, JVM): a simulated drive along a route with one bend. Follow state,
 * camera heading, zoom, remaining geometry and frame timing are checked per fix. The on-device
 * part (real map, real frames) is left to the owner phone check.
 */
class C13CameraFollowTest {
    // North 1 km from Stockport, then east 1 km (a 90 degree bend at 1 000 m).
    private val corner = LatLon(53.4000, -2.1600)
    private val route: List<LatLon> = run {
        val north = (0..20).map { LatLon(corner.lat + it * 0.00045, corner.lon) } // 20 x ~50 m = 1 km
        val bendLat = north.last().lat
        north + (1..20).map { LatLon(bendLat, corner.lon + it * 0.0013) }        // ~87 m a step east
    }
    private val index = RouteGeometryIndex(route)

    private fun carAt(along: Double): LatLon = index.slice(along, along + 0.01).first()

    private fun headLength(along: Double): Double {
        val f = JSONObject(RouteLayers.headJson(index, along)).getJSONArray("features")
        if (f.length() == 0) return 0.0
        val c = f.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        val pts = (0 until c.length()).map { LatLon(c.getJSONArray(it).getDouble(1), c.getJSONArray(it).getDouble(0)) }
        return pts.zipWithNext { a, b -> Geo.distance(a, b) }.sum()
    }

    @Test fun remainingGeometryShrinksEveryFixAndAlwaysMatchesTheRouteAhead() {
        var last = Double.MAX_VALUE
        var along = 0.0
        while (along < index.total - 5) {
            val drawn = headLength(along) + RouteLayers.pieces(route)
                .filter { it.second > RouteLayers.pieceEnd(along, index.total) + 0.001 }
                .sumOf { p -> p.first.zipWithNext { a, b -> Geo.distance(a, b) }.sum() }
            assertEquals("remaining at $along m", index.total - along, drawn, 1.0)
            assertTrue("never grows at $along m", drawn <= last + 0.5)
            last = drawn
            along += 4.0 // one fix every ~4 m at 15 m/s and 4 Hz
        }
    }

    @Test fun headingFollowsTheBendFromNorthToEastAndSettles() {
        val h = CameraHeading()
        var t = 0L
        var along = 0.0
        var previous: Double? = null
        var maxStepPerFix = 0.0
        val speed = 15.0
        while (along < index.total) {
            val bearing = routeBearingNear(route, carAt(along))
            val v = h.update(t, bearing, speed)!!
            previous?.let { p -> maxStepPerFix = maxOf(maxStepPerFix, Math.abs(HeadingEstimator.signedDiff(v, p))) }
            previous = v
            t += 250L
            along += speed * 0.25
        }
        assertEquals("ends facing east", 90.0, previous!!, 2.0)
        // The bend is followed promptly by design (FAST_TAU 0.25 s): a 90 degree corner may turn up to
        // ~57 degrees in one 250 ms fix, but never overshoots and never turns faster than that envelope.
        assertTrue("largest step $maxStepPerFix deg within the bend envelope", maxStepPerFix <= 60.0)
        assertTrue("no overshoot past east", previous!! <= 91.0)
    }

    @Test fun stoppedAtALightHoldsTheHeadingAndFollowingTheCarResumes() {
        val h = CameraHeading()
        h.update(0, 0.0, 15.0)
        val held = h.update(1_000, 90.0, 0.5)!!
        assertEquals("no spin while stopped", 0.0, held, 1e-9)
        assertEquals("poor bearing never turns the map", 0.0, h.update(2_000, 180.0, 15.0, bearingAccuracyDeg = 60.0)!!, 1e-9)
        assertTrue("moving again turns toward the new bearing", h.update(3_000, 90.0, 15.0)!! > 0.0)
    }

    @Test fun speedAdaptiveZoomGlidesNoFasterThanOneLevelPerSecondAtAnyFixRate() {
        // Five fixes a second glide no faster than one fix a second (elapsed time is what counts).
        val oneFix = DrivingCamera.limitZoomStepForElapsed(16.0, 12.0, 1_000)
        val fiveFixes = DrivingCamera.limitZoomStepForElapsed(16.0, 12.0, 200)
        assertEquals(16.0 - DrivingCamera.ZOOM_RATE_PER_SECOND, oneFix, 1e-9)
        assertEquals(16.0 - DrivingCamera.ZOOM_RATE_PER_SECOND * 0.2, fiveFixes, 1e-9)
        assertEquals("a long gap is still bounded", 16.0 - DrivingCamera.ZOOM_RATE_PER_SECOND * 1.5,
            DrivingCamera.limitZoomStepForElapsed(16.0, 12.0, 60_000), 1e-9)
    }

    @Test fun zoomForAPointAheadFallsWithDistanceAndRisesOnASmallerView() {
        val near = DrivingCamera.zoomShowing(200.0, 53.4)
        val far = DrivingCamera.zoomShowing(800.0, 53.4)
        assertTrue("further road needs a lower zoom", far < near)
        assertTrue("a shorter view needs a lower zoom to fit the same road", DrivingCamera.zoomShowing(400.0, 53.4, 300.0) < DrivingCamera.zoomShowing(400.0, 53.4, 600.0))
    }

    @Test fun turnViewZoomStaysBetweenStreetLevelAndTheCap() {
        assertEquals(15.5, DrivingCamera.turnFrameZoom(null, 10.0, sameTurn = false), 1e-9)
        assertEquals(17.8, DrivingCamera.turnFrameZoom(null, 30.0, sameTurn = false), 1e-9)
        // Nearing the same turn it only zooms in, never back out.
        assertEquals(16.5, DrivingCamera.turnFrameZoom(16.5, 15.9, sameTurn = true), 1e-9)
    }
}
