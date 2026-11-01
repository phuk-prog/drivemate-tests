package com.drivemate.app.data

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Category 1.2 — the per-area ("Download on Wi-Fi") path: an interrupted package
 * resumes from its own part file, a checksum failure is deleted and retried from
 * zero, and a package that never verifies is reported without installing anything.
 */
class M02RegionalPackageDownloadTest {
    @get:Rule val temp = TemporaryFolder()

    private val base = archive(1)
    private val region = archive(2)
    private val regionName = "region-z8-x1-y2"
    private val catalog = RegionMapCatalog.validate(
        sourceSha256 = sha(byteArrayOf(9)),
        packs = listOf(
            RegionMapCatalog.Pack("base", sha(base), base.size.toLong(), 10L, 10L),
            RegionMapCatalog.Pack(regionName, sha(region), region.size.toLong(), 5L, 5L),
        ),
    )
    private val plan = RegionDownloadPlanner.Plan(
        packages = listOf(regionName),
        companions = emptyList(),
        downloadBytes = region.size.toLong(),
        uncoveredPoints = 0,
    )

    /** Real PMTiles v3 magic plus deterministic filler, so the production verifier accepts it. */
    private fun archive(seed: Int, size: Int = 4096): ByteArray {
        val bytes = ByteArray(size) { ((it * 31 + seed) and 0xff).toByte() }
        "PMTiles".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[7] = 3
        return bytes
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun downloader(
        store: VerifiedRegionalMapStore,
        stage: File,
        fetch: (String, File, (Long) -> Unit) -> Unit,
        attempts: Int = 3,
    ) = RegionalMapDownloader(
        store = store,
        catalog = catalog,
        stagingDir = stage,
        urlFor = { "https://example.invalid/${it.name}.pmtiles" },
        fetch = fetch,
        companionTarget = { null },
        attempts = attempts,
    )

    @Test fun interruptedPackageResumesFromItsPartAndInstalls() {
        val store = VerifiedRegionalMapStore(File(temp.root, "installed"), catalog)
        var calls = 0
        val resumedFrom = mutableListOf<Long>()
        val result = downloader(store, File(temp.root, "stage"), { _, dest, _ ->
            calls++
            if (calls == 1) {
                dest.writeBytes(region.copyOf(1000))
                throw IOException("connection reset")
            }
            resumedFrom += dest.length()
            dest.appendBytes(region.copyOfRange(dest.length().toInt(), region.size))
        }).download(plan)

        assertTrue(result.complete)
        assertEquals(listOf(regionName), result.installed)
        assertEquals(2, calls)
        assertEquals(listOf(1000L), resumedFrom)
        assertNotNull(store.verifiedFileForPackage(regionName))
    }

    @Test fun badChecksumIsDeletedAndRetriedFromZero() {
        val store = VerifiedRegionalMapStore(File(temp.root, "installed"), catalog)
        val corrupt = region.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x55).toByte() }
        var calls = 0
        val startedAt = mutableListOf<Long>()
        val result = downloader(store, File(temp.root, "stage"), { _, dest, _ ->
            calls++
            startedAt += dest.length()
            dest.writeBytes(if (calls == 1) corrupt else region)
        }).download(plan)

        assertTrue(result.complete)
        assertEquals(2, calls)
        // The corrupt full-length part is discarded, so the retry does not resume it.
        assertEquals(listOf(0L, 0L), startedAt)
        assertNotNull(store.verifiedFileForPackage(regionName))
    }

    @Test fun persistentFailureIsReportedAndNothingIsInstalled() {
        val store = VerifiedRegionalMapStore(File(temp.root, "installed"), catalog)
        var calls = 0
        val result = downloader(store, File(temp.root, "stage"), { _, dest, _ ->
            calls++
            dest.writeBytes(ByteArray(16))
        }, attempts = 3).download(plan)

        assertFalse(result.complete)
        assertEquals(3, calls)
        assertTrue(result.installed.isEmpty())
        assertTrue(result.failed.containsKey(regionName))
        assertNull(store.verifiedFileForPackage(regionName))
    }
}
