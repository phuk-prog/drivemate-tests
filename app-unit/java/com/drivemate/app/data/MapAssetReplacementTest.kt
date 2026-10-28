package com.drivemate.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** TF-08 actual-file cache safety, not published-download or offline-navigation acceptance.
 * Uses the existing authentic Manchester source fixture; no road geometry is manufactured.
 */
class MapAssetReplacementTest {
    private fun source(): ByteArray = javaClass.getResourceAsStream(
        "/cat3-published-manchester-lanes-3.json"
    )!!.use { it.readBytes() }

    private fun withCache(check: (File, ByteArray, MapData.Asset) -> Unit) {
        val directory = Files.createTempDirectory("map-asset-contract-").toFile()
        try {
            val bytes = source()
            val destination = File(directory, "lanes.json").apply { writeBytes(bytes) }
            check(destination, bytes, MapData.Asset(MapData.sha256(bytes), bytes.size.toLong(), "map-data-contract"))
        } finally { directory.deleteRecursively() }
    }

    private fun assertOnlyCache(destination: File) {
        assertEquals(listOf(destination.name), destination.parentFile!!.list()!!.sorted())
    }

    @Test fun corruptSameLengthReplacementPreservesExistingCacheAndCleansTemporaryFiles() {
        withCache { destination, original, asset ->
            val corrupt = original.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
            try {
                MapData.replaceValidated(destination, asset) { temporary -> temporary.writeBytes(corrupt) }
                fail("Corrupt bytes must not replace the existing cache")
            } catch (_: IOException) { /* integrity rejection is expected */ }
            assertArrayEquals(original, destination.readBytes())
            assertOnlyCache(destination)
        }
    }

    @Test fun interruptedReplacementPreservesCacheThenSuccessfulRetryIsReusedWithoutDownload() {
        withCache { destination, original, asset ->
            try {
                MapData.replaceValidated(destination, asset) { temporary ->
                    temporary.writeBytes(original.copyOfRange(0, original.size / 2))
                    File(temporary.path + ".part").writeBytes(byteArrayOf(1, 2))
                    throw IOException("Controlled interrupted transfer")
                }
                fail("An interrupted transfer must not be accepted")
            } catch (_: IOException) { /* simulated transport failure, not invented map data */ }
            assertArrayEquals(original, destination.readBytes())
            assertOnlyCache(destination)
            MapData.replaceValidated(destination, asset) { temporary -> temporary.writeBytes(original) }
            assertArrayEquals(original, destination.readBytes())
            assertOnlyCache(destination)
            var transferred = false
            MapData.downloadAsset(destination, asset) { transferred = true }
            assertFalse("Verified installed bytes should be reused", transferred)
        }
    }
}
