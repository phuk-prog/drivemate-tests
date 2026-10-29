package com.drivemate.app.dmn

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * Category 1 source-backed RED pretest for DMN offline readiness with a stale stamp.
 *
 * Exact production DmnTiles.offlineArea() against an on-disk DMNPACK2 *test fixture*.
 * This is not a live UK route, proof of installed UK map coverage or road-law truth.
 * Do not weaken the failed stale-stamp assertion to obtain a green release gate.
 */
class DmnTilesStampTest {
    @get:Rule val folder = TemporaryFolder()

    private val square = "213_-9"
    private val fileName = "dmn-$square.dmnpack"
    private val indexName = "dmn-uk.json"

    @Before fun resetBefore() {
        DmnTiles.forget()
    }

    @After fun resetAfter() {
        DmnTiles.forget()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun installedPiece(stamp: String?, damagePack: Boolean = false): String {
        val raw = requireNotNull(
            javaClass.classLoader!!.getResourceAsStream("dmn/dmn-test.dmnpack")
        ) { "Expected frozen DMNPACK2 writer fixture missing" }.use { it.readBytes() }
        // Use real test-writer bytes, not invented UK road geometry or live release bytes.
        File(folder.root, fileName).writeBytes(if (damagePack) byteArrayOf(0, 1, 2, 3) else raw)
        val hash = sha256(raw)
        if (stamp != null) {
            File(folder.root, "$fileName.sha256").writeText(if (stamp == "CURRENT") hash else stamp)
        }
        val entry = JSONObject().put("file", fileName).put("sha256", hash).put("bytes", raw.size)
        val index = JSONObject().put("schema", 1).put("format", "DMNPACK2")
            .put("release", "dmn-uk-20261028-15")
            .put("tiles", JSONObject().put(square, entry))
        File(folder.root, indexName).writeText(index.toString())
        return hash
    }

    private fun readiness(): DmnTiles.Offline = DmnTiles.offlineArea(
        folder.root,
        doubleArrayOf(-2.25, 53.25, -2.25, 53.25)
    )

    @Test fun currentVerifiedStampAndLocalPieceReportReady() {
        installedPiece("CURRENT")
        assertEquals(DmnTiles.Offline.READY, readiness())
    }

    @Test fun pieceWithNoStampMustNotReportReady() {
        installedPiece(null)
        assertEquals(DmnTiles.Offline.MISSING, readiness())
    }

    @Test fun unreadablePieceWithStaleStampCannotReportOfflineReady() {
        installedPiece("0".repeat(64), damagePack = true)
        // Deliberately unreadable pack: no offline DMN router can open it.
        // Source DmnTiles.held() sees only existence of two files and reports READY.
        // A parseable older pack is a separate fallback policy question, NOT
        // something this regression requires us to reject.
        assertEquals(DmnTiles.Offline.MISSING, readiness())
    }

    @Test fun fullUkFetchRechecksStampedBytesAndRepairsSameSizeCorruption() {
        val good = requireNotNull(javaClass.classLoader!!.getResourceAsStream("dmn/dmn-test.dmnpack"))
            .use { it.readBytes() }
        installedPiece("CURRENT")
        val existing = File(folder.root, fileName)
        val damaged = good.clone().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        existing.writeBytes(damaged)
        assertEquals(good.size.toLong(), existing.length())
        val originalDownload = DmnTiles.download
        val originalGetText = DmnTiles.getText
        try {
            DmnTiles.getText = { File(folder.root, indexName).readText() }
            var repairs = 0
            DmnTiles.download = { _, destination, _ ->
                repairs++
                destination.writeBytes(good)
            }
            DmnTiles.fetchAll(folder.root)
            assertEquals("The matching stamp must not hide changed bytes", 1, repairs)
            org.junit.Assert.assertArrayEquals(good, existing.readBytes())
        } finally {
            DmnTiles.download = originalDownload
            DmnTiles.getText = originalGetText
        }
    }

    @Test fun missingPublishedCorridorBlockRequiresCompleteMainRoadFallback() {
        // Fixture metadata, not a claim of genuine UK road or legal coverage.
        // In the old selection, one existing regional block was enough to
        // report READY across a long drive with multiple missing blocks.
        val raw = requireNotNull(
            javaClass.classLoader!!.getResourceAsStream("dmn/dmn-test.dmnpack")
        ).use { it.readBytes() }
        val hash = sha256(raw)
        fun piece(name: String, held: Boolean = true): JSONObject {
            if (held) {
                File(folder.root, name).writeBytes(raw)
                File(folder.root, "$name.sha256").writeText(hash)
            }
            return JSONObject().put("file", name).put("sha256", hash).put("bytes", raw.size)
        }
        val tiles = JSONObject()
            .put("213_-9", piece("dmn-213_-9.dmnpack"))
            .put("206_-1", piece("dmn-206_-1.dmnpack"))
        val regions = JSONObject().put("main-25_-2", piece("dmn-main-25_-2.dmnpack"))
        val mainName = "dmn-main-uk.dmnpack"
        val mainEntry = piece(mainName, held = false)
        val index = JSONObject().put("schema", 1).put("format", "DMNPACK2")
            .put("release", "dmn-uk-20261028-15")
            .put("tiles", tiles).put("regions", regions).put("main", mainEntry)
        File(folder.root, indexName).writeText(index.toString())
        val trip = listOf(
            com.drivemate.app.nav.LatLon(53.4, -2.2),
            com.drivemate.app.nav.LatLon(51.5, -0.1)
        )
        assertEquals(
            "One region cannot silently substitute for the full corridor/main roads",
            DmnTiles.Offline.PARTIAL, DmnTiles.offline(folder.root, trip)
        )
        piece(mainName)
        DmnTiles.forget()
        assertEquals(
            "Once the complete main-road fallback is installed, all required pieces are present",
            DmnTiles.Offline.READY, DmnTiles.offline(folder.root, trip)
        )
    }

    @Test fun parseableOldPackIsNotAssumedToBeMissing() {
        installedPiece("0".repeat(64))
        // Current API reports *potential offline availability*, not release freshness.
        // Do not ban valid last-week fallback on an unreviewed assumption.
        assertEquals(DmnTiles.Offline.READY, readiness())
    }
}
