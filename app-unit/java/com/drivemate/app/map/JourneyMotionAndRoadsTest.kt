package com.drivemate.app.map

import com.drivemate.app.data.MapBridges
import com.drivemate.app.nav.RoutePalette
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Journey review 8 and 13: the owner's choices for the car's movement and the route on the map. */
class JourneyMotionAndRoadsTest {
    /** 8: "Glide full gap": no pause before the next fix; a bend settles in about 0.4 s, not 0.18 s. */
    @Test fun q8_cameraGlidesTheWholeGap() {
        assertEquals(1_000, DrivingCamera.animationDuration(1_000, 90.0, 92.0))
        assertEquals(1_500, DrivingCamera.animationDuration(1_500, 90.0, 92.0))
        assertEquals(400, DrivingCamera.animationDuration(1_000, 90.0, 130.0))
        assertEquals("a quick fix stays quick", 200, DrivingCamera.animationDuration(200, 90.0, 130.0))
        assertEquals(0, DrivingCamera.animationDuration(0, 90.0, 92.0))
    }

    /** 13: "Slimmer near junctions": unchanged on open road (zoom 16 and out), slimmer closer in. */
    @Test fun q13_routeSlimmerCloseIn() {
        for (line in RoutePalette.Line.entries) {
            assertEquals(line.z16.toDouble(), RoutePalette.width(line, 16.0), 0.01)
            assertEquals("no wider at 17 than at 16", line.z16.toDouble(), RoutePalette.width(line, 17.0), 0.01)
        }
        // Junction zoom 17.2: about 11.6 px, where it was about 16.6 px.
        assertTrue(RoutePalette.width(RoutePalette.Line.FILL, 17.2) < 12.0)
        assertEquals(20.0, RoutePalette.width(RoutePalette.Line.FILL, 19.0), 0.01)
        assertEquals(30.0, RoutePalette.width(RoutePalette.Line.HALO, 19.0), 0.01)
    }

    private val style = JSONObject("""{"version":8,"sources":{},"layers":[
        {"id":"water","type":"fill"},
        {"id":"road-primary-casing","type":"line","source":"drivemate","source-layer":"roads","filter":["==",["get","kind"],"primary"]},
        {"id":"road-primary","type":"line","source":"drivemate","source-layer":"roads","filter":["==",["get","kind"],"primary"]},
        {"id":"building-3d","type":"fill-extrusion"},
        {"id":"label-road","type":"symbol"}]}""")

    /** 13: "Bridge over route": each road layer gets a bridge-only copy just under the labels. */
    @Test fun q13_bridgesDrawnAgainUnderTheLabels() {
        val layers = MapBridges.add(JSONObject(style.toString())).getJSONArray("layers")
        val ids = (0 until layers.length()).map { layers.getJSONObject(it).getString("id") }
        assertEquals(listOf("water", "road-primary-casing", "road-primary", "building-3d",
            "bridge-primary-casing", "bridge-primary", "label-road"), ids)
        val filter = layers.getJSONObject(5).getJSONArray("filter").toString()
        assertTrue(filter.startsWith("[\"all\""))
        assertTrue("our layout's mark", filter.contains("\"bridge\"]"))
        assertTrue("OpenMapTiles' mark", filter.contains("brunnel"))
        assertEquals("the copy keeps the road's own source", "roads", layers.getJSONObject(5).getString("source-layer"))
    }

    @Test fun q13_bridgesAddedOnce() {
        val once = MapBridges.add(JSONObject(style.toString()))
        val twice = MapBridges.add(JSONObject(once.toString()))
        assertEquals(once.getJSONArray("layers").length(), twice.getJSONArray("layers").length())
    }
}
