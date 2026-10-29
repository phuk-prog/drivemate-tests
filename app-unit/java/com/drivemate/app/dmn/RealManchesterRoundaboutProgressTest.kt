package com.drivemate.app.dmn

import com.drivemate.app.data.Route
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.NavigationEngine
import com.drivemate.app.nav.roundaboutCirculationInstruction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * J27-03: REAL bundled UK graph pretest, NOT a diagram invented by this test.
 *
 * Selects legally traversable edges according to the bundled OSM-derived DMNPACK
 * (not proof that the source matches 2026 road markings). The start, ring pieces,
 * exit and GPS replay positions are all derived from actual source edges/nodes
 * in the current Greater Manchester app asset. This is a map/engine consistency
 * test; independently verified road law/ground truth still needs a separate check.
 */
class RealManchesterRoundaboutProgressTest {
    private val pack: DmnPack by lazy {
        // Gradle test working directory is normally the app module; accept root too.
        val file = listOf(
            File("src/main/assets/${Dmn.ASSET}"),
            File("app/src/main/assets/${Dmn.ASSET}")
        ).firstOrNull { it.isFile }
        require(file != null) {
            "BLOCKED: Greater Manchester DMN asset missing; cannot claim a real-UK-graph roundabout pretest"
        }
        file.inputStream().use(DmnPack::read)
    }

    private fun ring(p: DmnPack, e: Int) = p.wayRoundabout[p.way[e]]
    private fun point(p: DmnPack, n: Int) = LatLon(p.lat[n], p.lon[n])
    private fun allowed(p: DmnPack, e: Int) = p.access[e].toInt() == DmnRouter.ALLOWED
    private fun outgoing(p: DmnPack, node: Int): List<Int> =
        (p.outStart[node] until p.outStart[node + 1]).map { p.outEdges[it] }

    private fun inStockport(p: DmnPack, n: Int) =
        p.lat[n] in 53.37..53.47 && p.lon[n] in -2.27..-2.08

    private data class ActualPassage(
        val entry: Int,
        val ringEdges: List<Int>,
        val leaving: Int,
        val route: Route,
        val sourceWay: Long,
        val sourceNode: Long?,
    )

    /**
     * Find several distinct mapped ring passages near Stockport, including
     * the general M60/A560/A5145 area. Do not pretend they are necessarily the
     * driver's exact entry and exit: the screenshots do not contain a trace.
     */
    private fun realPassages(p: DmnPack, minRingMetres: Double = 35.0, focus: LatLon? = null, maxCases: Int = 4): List<ActualPassage> {
        val approachByEnd = HashMap<Int, MutableList<Int>>()
        val rings = ArrayList<Int>()
        for (e in 0 until p.edgeCount) {
            val node = p.from[e]
            if (!inStockport(p, node)) continue
            if (ring(p, e) && allowed(p, e)) rings += e
            if (!ring(p, e) && allowed(p, e) && !p.restricted[e]) {
                approachByEnd.getOrPut(p.to[e]) { ArrayList() }.add(e)
            }
        }
        val result = ArrayList<ActualPassage>()
        val selected = ArrayList<LatLon>()
        val router = DmnRouter(p)
        // A longer same-graph approach at these source-mapped roundabouts is a separate
        // exercise: the engine formerly limited its inferred exit search to 300 metres.
        val starts = if (focus == null) rings else rings.filter { e ->
            Geo.distance(point(p, p.from[e]), focus) <= 350.0
        }.sortedBy { e -> Geo.distance(point(p, p.from[e]), focus) }
        for (start in starts) {
            val startNode = p.from[start]
            val approach = approachByEnd[startNode]?.firstOrNull {
                it != p.reverse[start] && !p.banned(it, start) &&
                p.from[it] != p.to[start]
            } ?: continue
            val origin = point(p, startNode)
            if (selected.any { Geo.distance(it, origin) < 300 }) continue

            val visited = HashSet<Int>()
            val travelled = ArrayList<Int>()
            var current = start
            var distance = 0.0
            var leaving = -1
            while (visited.add(current) && travelled.size < 100 && distance < minRingMetres + 550.0) {
                travelled += current
                distance += p.metres[current]
                val n = p.to[current]
                val outs = outgoing(p, n)
                if (travelled.size >= 3 && distance >= minRingMetres) {
                    leaving = outs.firstOrNull { e ->
                        !ring(p, e) && allowed(p, e) && !p.restricted[e] &&
                        p.to[e] != p.from[current] && !p.banned(current, e) &&
                        p.wayHighway[p.way[e]] !in setOf("service", "footway", "path", "track")
                    } ?: -1
                }
                if (leaving >= 0) break
                val next = outs.filter { e ->
                    ring(p, e) && allowed(p, e) &&
                    p.to[e] != p.from[current] && !p.banned(current, e) &&
                    e !in visited
                }.minWithOrNull(compareBy<Int> { p.way[it] != p.way[current] }.thenBy { it })
                if (next == null) break
                current = next
            }
            if (leaving < 0) continue
            val path = (travelled + leaving).toIntArray()
            val metres = path.sumOf { p.metres[it].toDouble() }
            val secs = path.sumOf { p.seconds(it).toDouble() }
            val plan = DmnRouter.Plan(approach, leaving, path, metres, secs, false)
            val from = point(p, p.from[approach])
            val to = point(p, p.to[leaving])
            val route = Dmn.build(router, listOf(Triple(from, to, plan)))
            if (route.steps.none { it.isRoundabout }) continue
            result += ActualPassage(
                approach, travelled, leaving, route,
                p.wayId[p.way[start]], p.nodeId?.get(startNode)
            )
            selected += origin
            if (result.size == maxCases) break
        }
        return result
    }

    @Test fun actualTaggedRingExitIsNotDeclaredCompleteBeforeTheMappedExitNode() {
        val p = pack
        val cases = realPassages(p)
        assertTrue(
            "INSUFFICIENT REAL DATA: need >=2 distinct source-backed Stockport roundabout passages, got ${cases.size}",
            cases.size >= 2
        )
        for (c in cases) {
            val eng = NavigationEngine(c.route)
            val i = eng.steps.indexOfFirst { it.isRoundabout }
            assertTrue("Expected one actual mapped roundabout move: way=${c.sourceWay}", i > 0)
            // Leaving edge starts at the true end node of the tagged ring chain.
            // It is NOT inferred from a bend angle or the test's expected result.
            val atExit = point(p, p.from[c.leaving])
            val exitAlong = eng.alongOf(atExit, maxDistance = 15.0)
            assertTrue("Ring exit must be on routed graph: way=${c.sourceWay}", exitAlong != null)
            val claimed = eng.maneuverEnd(i)
            assertTrue(
                "PREMATURE ROUNDABOUT EXIT (real UK map): way=${c.sourceWay}, OSM node=${c.sourceNode}, " +
                    "actual graph exit along=${exitAlong}, engine completion=${claimed}",
                claimed >= exitAlong!! - 10.0
            )
            assertTrue(
                "ROUNDABOUT STAYS ACTIVE TOO LONG: way=${c.sourceWay}, exit=${exitAlong}, engine=${claimed}",
                claimed <= exitAlong + 25.0
            )
        }
    }

    @Test fun realRingGpsReplayKeepsRoundaboutInstructionUntilItTrulyLeaves() {
        val p = pack
        val cases = realPassages(p)
        assertTrue("No real UK roundabout cases for GPS replay", cases.size >= 2)
        var insideChecked = 0
        for (c in cases) {
            val eng = NavigationEngine(c.route)
            val roundIndex = eng.steps.indexOfFirst { it.isRoundabout }
            assertTrue(roundIndex > 0)
            val exitAlong = eng.alongOf(point(p, p.from[c.leaving]), 15.0)!!
            val entryLoc = point(p, p.from[c.entry])
            eng.update(entryLoc, null, 5.0, 5.0)
            for (e in c.ringEdges) {
                val a = point(p, p.from[e])
                val b = point(p, p.to[e])
                val fix = Geo.interpolate(a, b, 0.5)
                val along = eng.alongOf(fix, 10.0) ?: continue
                if (along <= eng.stepStart(roundIndex) + 6.0 ||
                    exitAlong - along <= 9.0) continue
                val bearing = Geo.bearing(a, b)
                val pr = eng.update(fix, bearing, 8.0, 5.0)
                // Source-independent positioning assertion: the exact edge point
                // must still map to that stretch, or it is an explicit test failure.
                assertTrue(
                    "GPS map matching diverged on actual roundabout way=${c.sourceWay} node=${c.sourceNode}",
                    pr.offRoute < 12.0
                )
                assertTrue(
                    "PREMATURE NEXT TURN: still on mapped ring, way=${c.sourceWay} node=${c.sourceNode}, " +
                        "along=${pr.along} exit=${exitAlong}, step=${pr.nextStep}",
                    pr.onRoundabout && pr.nextStep == roundIndex
                )
                val spoken = roundaboutCirculationInstruction(
                    eng.steps[roundIndex].exit, pr.distanceToNext, eng.steps[roundIndex].name)
                assertTrue("Must guide to exit, not a following right turn: $spoken",
                    spoken.startsWith("Take the ") || spoken == "Take the exit")
                insideChecked++
            }
        }
        assertTrue("No qualifying real ring-internal GPS samples: graph or replay is unavailable", insideChecked >= 5)
        println("J27_REAL_UK_RING_INSIDE_CORRECT=$insideChecked/$insideChecked; source=app DMN pack; legalTruth=provisional")
    }
    @Test fun longMappedRingAtStockportInterchangeMustNotExpireAtArbitrary300MetreLimit() {
        val p = pack
        // These are published approximate historical interchange locators, not claims
        // about the private user's exact entry or contemporary lane/legal layout.
        val pyramidJ1 = LatLon(53.40913, -2.17411)
        val portwoodJ27 = LatLon(53.41632, -2.14879)
        val focusCases = listOf("Pyramid J1" to pyramidJ1, "Portwood J27" to portwoodJ27)
        val found = focusCases.mapNotNull { (name, focus) ->
            realPassages(p, minRingMetres = 315.0, focus = focus, maxCases = 1)
                .firstOrNull()?.let { name to it }
        }
        assertTrue(
            "BLOCKED/UNKNOWN: real Greater Manchester graph has no source-backed >=315 m ring passage " +
                "near J1/J27; check actual tagging rather than inventing one", found.isNotEmpty()
        )
        for ((place, c) in found) {
            val eng = NavigationEngine(c.route)
            val index = eng.steps.indexOfFirst { it.isRoundabout }
            assertTrue("Mapped ring not recognised at $place", index > 0)
            val graphExit = eng.alongOf(point(p, p.from[c.leaving]), 15.0)
            assertTrue("Real route exit not found: $place", graphExit != null)
            val actual = eng.maneuverEnd(index)
            assertTrue(
                "$place premature next instruction before graph ring exit: OSM way=${c.sourceWay} " +
                    "exit=$graphExit, complete=$actual, measured ring length=" +
                    c.ringEdges.sumOf { p.metres[it].toDouble() },
                actual >= graphExit!! - 10.0
            )
            val last = c.ringEdges.last()
            val nearExitFix = Geo.interpolate(point(p, p.from[last]), point(p, p.to[last]), 0.5)
            if (eng.alongOf(nearExitFix, 10.0)!! < graphExit - 9.0) {
                val progress = eng.update(nearExitFix,
                    Geo.bearing(point(p, p.from[last]), point(p, p.to[last])), 8.0, 5.0)
                assertTrue("$place: still on actual ring but the next turn was promoted",
                    progress.onRoundabout && progress.nextStep == index)
            }
        }
    }


    /** J27-03: a source-mapped exit must survive the real application's saved-trip JSON. */
    @Test fun mappedExitRemainsCorrectAfterSavedTripJsonRoundTrip() {
        val p = pack
        val cases = realPassages(
            p, minRingMetres = 315.0, focus = LatLon(53.40913, -2.17411), maxCases = 1
        )
        assertTrue("MISSING REAL STOCKPORT LONG-RING CASE", cases.isNotEmpty())
        val c = cases.first()
        val index = c.route.steps.indexOfFirst { it.isRoundabout }
        assertTrue("Expected mapped roundabout step", index >= 0)
        val originalExit = c.route.steps[index].roundaboutExitLocation
        assertTrue("DMN has not supplied an explicit graph exit", originalExit != null)
        val restored = com.drivemate.app.data.TripStore.route(
            com.drivemate.app.data.TripStore.routeJson(c.route)
        )
        assertEquals("Saved-trip recovery lost the source-mapped ring exit",
            originalExit, restored.steps[index].roundaboutExitLocation)
        val eng = NavigationEngine(restored)
        val graphExit = eng.alongOf(point(p, p.from[c.leaving]), 15.0)
        assertTrue("Restored path no longer matches the real graph exit", graphExit != null)
        assertTrue("Saved trip advanced to following turn while still circulating",
            eng.maneuverEnd(index) >= graphExit!! - 10.0)
    }

    /** Unknown providers must not silently acquire a DMN-derived ring boundary. */
    @Test fun missingMappedExitRemainsUnknownAfterSavedTripRecovery() {
        val c = realPassages(pack).firstOrNull()
        assertTrue("No source-backed roundabout available", c != null)
        val unknown = c!!.route.copy(steps = c.route.steps.map { st ->
            if (st.isRoundabout) st.copy(roundaboutExitLocation = null) else st
        })
        val restored = com.drivemate.app.data.TripStore.route(
            com.drivemate.app.data.TripStore.routeJson(unknown)
        )
        assertTrue("Unknown graph exits must stay unknown after restore",
            restored.steps.filter { it.isRoundabout }.all { it.roundaboutExitLocation == null })
        val i = restored.steps.indexOfFirst { it.isRoundabout }
        assertTrue("Restored route lost its roundabout", i >= 0)
        assertTrue("Unknown-exit fallback produced no finite progress boundary",
            NavigationEngine(restored).maneuverEnd(i).isFinite())
    }

    /** Incorrect off-route coordinates must not override the unknown-data fallback. */
    @Test fun impossibleMappedExitIsRejectedInsteadOfChangingRoundaboutProgress() {
        val c = realPassages(pack).firstOrNull()
        assertTrue("No source-backed roundabout available", c != null)
        val i = c!!.route.steps.indexOfFirst { it.isRoundabout }
        assertTrue(i >= 0)
        val without = c.route.copy(steps = c.route.steps.map { st ->
            if (st.isRoundabout) st.copy(roundaboutExitLocation = null) else st
        })
        val impossible = c.route.copy(steps = c.route.steps.map { st ->
            if (st.isRoundabout) st.copy(roundaboutExitLocation = LatLon(0.0, 0.0)) else st
        })
        assertEquals("Off-route false exit must not override unverified fallback",
            NavigationEngine(without).maneuverEnd(i),
            NavigationEngine(impossible).maneuverEnd(i), 0.001)
    }
}
