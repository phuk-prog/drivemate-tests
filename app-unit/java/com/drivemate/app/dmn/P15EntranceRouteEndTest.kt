package com.drivemate.app.dmn

import com.drivemate.app.nav.LatLon
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * P15.2 (owner, 31 Oct): end a route on the road nearest the destination's mapped OSM entrance,
 * not the road nearest its pin; without a usable entrance, behave exactly as before.
 *
 * Small hand-made road map (not real UK roads): Front Street and Back Lane, 56 m apart, joined by
 * Side Road. The pin is 17 m from Back Lane; the house's mapped front door is on Front Street.
 * This proves the app's choice of route end only, not that any real entrance is mapped correctly.
 */
class P15EntranceRouteEndTest {
    @get:Rule val folder = TemporaryFolder()

    private val start = LatLon(53.40000, -2.14990)
    private val pin = LatLon(53.40035, -2.14900)
    private fun door(kind: String = "main", access: String = "", lat: Double = 53.40012, parking: Boolean = false, id: Long = 5) =
        DmnEntrances.Entrance(id, LatLon(lat, -2.14900), kind, parking, access)

    private var oldNear: (LatLon) -> List<DmnEntrances.Entrance> = Dmn.entrancesNear
    private var oldRouter: DmnRouter? = null

    @Before fun setUp() {
        oldNear = Dmn.entrancesNear; oldRouter = Dmn.testRouter
        DmnTiles.forget()
    }

    @After fun tearDown() {
        Dmn.entrancesNear = oldNear; Dmn.testRouter = oldRouter
        DmnTiles.forget()
    }

    // --- choosing the entrance ---

    @Test fun mainEntranceWinsOverANearerSideDoor() {
        val side = door("service", lat = 53.40030, id = 7)
        assertEquals(5L, DmnEntrances.choose(pin, listOf(side, door()))?.osmNode)
    }

    @Test fun exitsEmergencyDoorsAndNoAccessAreNeverChosen() {
        for (bad in listOf(door("exit"), door("emergency"), door("no"), door("yes", access = "no"))) {
            assertNull(bad.toString(), DmnEntrances.choose(pin, listOf(bad)))
        }
        assertNotNull(DmnEntrances.choose(pin, listOf(door("yes", access = "private")))) // a home's own door
        assertNotNull(DmnEntrances.choose(pin, listOf(door("", parking = true))))
    }

    @Test fun anEntranceFurtherThan30mIsAnotherBuildings() {
        assertNull(DmnEntrances.choose(pin, listOf(door(lat = 53.39990)))) // about 50 m
        assertNull(DmnEntrances.choose(pin, emptyList()))
    }

    @Test fun entranceFileReadsAsPublishedAndRefusesCutOffOrForeignFiles() {
        val text = file(listOf("5\t534001200\t-21490000\tmain\t\t", "7\t534003000\t-21490000\t\tparking_entrance\tprivate"))
        val rows = DmnEntrances.parse(text)
        assertEquals(listOf(5L, 7L), rows.map { it.osmNode })
        assertEquals(53.40012, rows[0].at.lat, 1e-9)
        assertTrue(rows[1].parking && rows[1].entrance.isEmpty() && rows[1].access == "private")
        for (bad in listOf(text.dropLast(1), text.replace("#drivemate-entrances\t1", "#drivemate-entrances\t2"),
                           text.replace("\tmain\t\t", "\tmain\t"))) {
            assertTrue(runCatching { DmnEntrances.parse(bad) }.isFailure)
        }
    }

    // --- reading the files on the phone ---

    @Test fun phoneReadsOnlyStampedEntranceFilesTheIndexNames() {
        val gz = gzip(file(listOf("5\t534001200\t-21490000\tmain\t\t")).toByteArray())
        val name = "dmn-ent-213_-9.tsv.gz"
        File(folder.root, name).writeBytes(gz)
        index(JSONObject().put("213_-9", JSONObject().put("file", name).put("sha256", sha(gz)).put("bytes", gz.size)))
        assertEquals(emptyList<Long>(), DmnTiles.entrances(folder.root, pin).map { it.osmNode }) // no stamp yet
        File(folder.root, "$name.sha256").writeText(sha(gz))
        DmnTiles.forget()
        assertEquals(listOf(5L), DmnTiles.entrances(folder.root, pin).map { it.osmNode })
        File(folder.root, "$name.sha256").writeText("0".repeat(64)) // not this index's copy
        DmnTiles.forget()
        assertEquals(emptyList<Long>(), DmnTiles.entrances(folder.root, pin))
    }

    @Test fun anOlderOrDamagedEntranceListNeverStopsTheRoadMap() {
        index(null)
        assertEquals(emptyList<Long>(), DmnTiles.entrances(folder.root, pin))
        DmnTiles.forget()
        index(JSONObject().put("213_-9", JSONObject().put("file", "../evil").put("sha256", "x").put("bytes", -1)))
        assertEquals(emptyList<Long>(), DmnTiles.entrances(folder.root, pin))
        // The index is still read: its road piece on the phone still counts as ready.
        assertEquals(DmnTiles.Offline.READY, DmnTiles.offline(folder.root, listOf(start, pin)))
    }

    // --- the route's end ---

    @Test fun routeEndsOnTheFrontDoorsStreetNotTheBackLaneNearestThePin() {
        Dmn.testRouter = DmnRouter(DmnPack.read(ByteArrayInputStream(streets())))
        Dmn.entrancesNear = { emptyList() }
        val before = Dmn.route(listOf(start, pin), heading = 90f).single()
        assertTrue("without an entrance the route goes round to Back Lane", before.points.dropLast(1).any { it.lat > 53.4004 })
        Dmn.entrancesNear = { listOf(door()) }
        val after = Dmn.route(listOf(start, pin), heading = 90f).single()
        assertTrue("with the front door it stays on Front Street", after.points.dropLast(1).all { it.lat < 53.4001 })
        assertEquals(pin, after.points.last()) // the pin is still the destination
        assertTrue(after.metres < before.metres)
    }

    @Test fun unusableOrDistantEntrancesLeaveTheRouteAsBefore() {
        Dmn.testRouter = DmnRouter(DmnPack.read(ByteArrayInputStream(streets())))
        Dmn.entrancesNear = { emptyList() }
        val before = Dmn.route(listOf(start, pin), heading = 90f).single()
        for (ignored in listOf(door("exit"), door(lat = 53.39990), door("yes", access = "no"))) {
            Dmn.entrancesNear = { listOf(ignored) }
            assertEquals(ignored.toString(), before.points, Dmn.route(listOf(start, pin), heading = 90f).single().points)
        }
        Dmn.entrancesNear = { error("entrance file unreadable") }
        assertEquals(before.points, Dmn.route(listOf(start, pin), heading = 90f).single().points)
    }

    // --- helpers ---

    private fun file(rows: List<String>) =
        "#drivemate-entrances\t1\ttest\t© OpenStreetMap contributors, ODbL 1.0\nosm_node\tlat_e7\tlon_e7\tentrance\tamenity\taccess\n" +
            rows.joinToString("") { "$it\n" }

    /** The index, with the frozen writer fixture installed and stamped as its square's road piece. */
    private fun index(entrances: JSONObject?) {
        val raw = requireNotNull(javaClass.classLoader!!.getResourceAsStream("dmn/dmn-test.dmnpack")).use { it.readBytes() }
        File(folder.root, "dmn-213_-9.dmnpack").writeBytes(raw)
        File(folder.root, "dmn-213_-9.dmnpack.sha256").writeText(sha(raw))
        val tile = JSONObject().put("file", "dmn-213_-9.dmnpack").put("sha256", sha(raw)).put("bytes", raw.size)
        val o = JSONObject().put("schema", 1).put("format", "DMNPACK2").put("release", "dmn-uk-20261031-1")
            .put("tiles", JSONObject().put("213_-9", tile))
        if (entrances != null) o.put("entrances", entrances)
        File(folder.root, "dmn-uk.json").writeText(o.toString())
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun gzip(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    /** DMNPACK2 as tools/dmn_pack.py writes it: Front Street 0-1, Side Road 1-2, Back Lane 2-3, both ways. */
    private fun streets(): ByteArray {
        val nodes = listOf(53.4000 to -2.1500, 53.4000 to -2.1480, 53.4005 to -2.1480, 53.4005 to -2.1500)
        val ways = listOf("Front Street", "Side Road", "Back Lane")
        val roads = listOf(Triple(0, 1, 133f), Triple(1, 2, 56f), Triple(2, 3, 133f))
        val buf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        fun text(s: String) { val b = s.toByteArray(); buf.putShort(b.size.toShort()); buf.put(b) }
        buf.put("DMNPACK2".toByteArray()); buf.putInt(nodes.size)
        nodes.forEachIndexed { i, (la, lo) -> buf.putInt(Math.round(la * 1e7).toInt()); buf.putInt(Math.round(lo * 1e7).toInt()); buf.putLong(100L + i) }
        buf.putInt(ways.size)
        ways.forEachIndexed { i, n -> buf.putLong(10L + i); buf.put(0); text(n); text(""); text("residential") }
        buf.putInt(roads.size * 2)
        roads.forEachIndexed { w, (a, b, m) ->
            for ((x, y, d) in listOf(Triple(a, b, 0), Triple(b, a, 1))) {
                buf.putInt(x); buf.putInt(y); buf.putFloat(m); buf.putFloat(30f)
                buf.put(0); buf.put(0); buf.put(0); buf.put(0); buf.putInt(w); buf.putShort(0); buf.put(d.toByte())
            }
        }
        buf.putInt(0); buf.putInt(0); buf.putInt(0); text("{\"format\":\"DMNPACK2\"}")
        return gzip(buf.array().copyOf(buf.position()))
    }
}
