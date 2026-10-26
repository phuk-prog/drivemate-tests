package com.drivemate.app.dmn

import com.drivemate.app.data.RouteStep
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Journey review 5: a roundabout the map only partly marks as one (the rest of the ring mapped as an
 * ordinary one-way road) gave "Turn right" part-way round, then a separate turn to leave. The exit
 * number told on the approach was right and must stay right: one roundabout move, no turn inside it.
 *
 * A 25 m roundabout with 16 ring nodes, going round clockwise (UK). Arms: west (270°), north (0°),
 * east (90°); the car comes in from the south (180°) and leaves by the east arm, the 3rd exit.
 */
class UnmarkedRoundaboutTest {
    private val centre = LatLon(53.40, -2.20)
    private val ring = (0 until 16).map { (180.0 + it * 22.5) % 360 } // clockwise from the south entry

    /** [marked]: how many ring pieces from the entry the map marks as roundabout (16 = all of it). */
    private fun drive(marked: Int, leaveAt: Double = 90.0): List<RouteStep> {
        val lat = ArrayList<Double>(); val lon = ArrayList<Double>()
        fun node(p: LatLon): Int { lat += p.lat; lon += p.lon; return lat.size - 1 }
        val ringNodes = ring.map { node(Geo.offset(centre, it, 25.0)) }
        val from = ArrayList<Int>(); val to = ArrayList<Int>(); val metres = ArrayList<Float>(); val way = ArrayList<Int>()
        fun edge(a: Int, b: Int, w: Int): Int {
            from += a; to += b; way += w
            metres += Geo.distance(LatLon(lat[a], lon[a]), LatLon(lat[b], lon[b])).toFloat()
            return from.size - 1
        }
        // Ways: 0 marked ring, 1 unmarked ring (one-way), then one two-way way per arm.
        val ringEdges = ring.indices.map { k -> edge(ringNodes[k], ringNodes[(k + 1) % 16], if (k < marked) 0 else 1) }
        val armWays = mapOf(180.0 to 2, 270.0 to 3, 0.0 to 4, 90.0 to 5)
        val armIn = HashMap<Double, Int>(); val armOut = HashMap<Double, Int>()
        for ((brg, w) in armWays) {
            val far = node(Geo.offset(centre, brg, 225.0))
            val at = ringNodes[ring.indexOf(brg)]
            armIn[brg] = edge(far, at, w); armOut[brg] = edge(at, far, w)
        }
        val n = lat.size; val e = from.size
        val pack = DmnPack(
            lat.toDoubleArray(), lon.toDoubleArray(), null,
            wayId = longArrayOf(1, 2, 3, 4, 5, 6),
            wayRoundabout = booleanArrayOf(true, false, false, false, false, false),
            wayName = arrayOf("", "", "South Road", "West Road", "North Road", "East Road"),
            wayRef = Array(6) { "" }, wayHighway = Array(6) { "tertiary" },
            from = from.toIntArray(), to = to.toIntArray(), metres = metres.toFloatArray(), kph = FloatArray(e) { 30f },
            access = ByteArray(e), startAccess = ByteArray(e), finishAccess = ByteArray(e), restricted = BooleanArray(e),
            way = way.toIntArray(), banPairs = LongArray(0), sequences = emptyList(), uturnNode = BooleanArray(n), metadata = "",
        )
        val leave = ring.indexOf(leaveAt)
        val path = ringEdges.take(leave) + armOut.getValue(leaveAt)
        val start = armIn.getValue(180.0)
        val plan = DmnRouter.Plan(start, path.last(), path.toIntArray(), path.sumOf { metres[it].toDouble() }, 60.0, false)
        val begin = LatLon(lat[from[start]], lon[from[start]])
        val end = LatLon(lat[to[path.last()]], lon[to[path.last()]])
        return Dmn.build(DmnRouter(pack), listOf(Triple(begin, end, plan))).steps
    }

    private fun moves(steps: List<RouteStep>) = steps.filter { it.type !in setOf("depart", "arrive") }

    @Test fun fullyMarkedRoundaboutIsOneMove() {
        val moves = moves(drive(marked = 16))
        assertEquals(listOf("roundabout"), moves.map { it.type })
        assertEquals(3, moves.single().exit)
        assertEquals("At the roundabout, take the third exit onto East Road", moves.single().instruction)
    }

    @Test fun unmarkedStretchBeforeTheExitIsStillTheRoundabout() {
        // The map marks the ring from the entry to 45°; 45° to the east exit is an ordinary one-way road.
        val moves = moves(drive(marked = 10)) // pieces 180° → … → 45°
        assertTrue("no turn inside the roundabout: ${moves.map { it.instruction }}", moves.none { it.type == "turn" })
        assertEquals(listOf("roundabout"), moves.map { it.type })
        assertEquals("the exit number told on the approach", 3, moves.single().exit)
        assertEquals("At the roundabout, take the third exit onto East Road", moves.single().instruction)
    }

    @Test fun leavingWhereTheMarkingEndsCountsTheSameExits() {
        // The marking ends at the north arm, and the car leaves there: the 2nd exit.
        val moves = moves(drive(marked = 8, leaveAt = 0.0))
        assertEquals(listOf("roundabout"), moves.map { it.type })
        assertEquals(2, moves.single().exit)
    }

    @Test fun carryingOnPastWhereTheMarkingEndsIsNotAnExit() {
        // The marking ends at the north arm, but the car carries on round to the east arm: still the 3rd exit.
        val moves = moves(drive(marked = 8))
        assertEquals(listOf("roundabout"), moves.map { it.type })
        assertEquals(3, moves.single().exit)
    }
}
