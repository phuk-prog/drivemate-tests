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

    @Test fun parseableOldPackIsNotAssumedToBeMissing() {
        installedPiece("0".repeat(64))
        // Current API reports *potential offline availability*, not release freshness.
        // Do not ban valid last-week fallback on an unreviewed assumption.
        assertEquals(DmnTiles.Offline.READY, readiness())
    }
}
