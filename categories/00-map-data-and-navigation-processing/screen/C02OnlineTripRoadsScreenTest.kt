package com.drivemate.tests.cat00

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.dmn.Dmn
import com.drivemate.app.dmn.DmnTiles
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * C02.5 (owner report 31 Oct: Home in Cheadle, 30 mi from Stoke, "No suitable driving route
 * found"): routing must work online without the whole-UK offline download. With an empty road
 * folder and the real published pieces, the app fetches only this trip's pieces, then routes;
 * the route is reused later with no network at all.
 */
@RunWith(AndroidJUnit4::class)
class C02OnlineTripRoadsScreenTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Test fun stokeToCheadleRoutesOnlineFromOnlyThisTripsPieces() {
        val root = File(app.cacheDir, "c02-online-trip").apply { deleteRecursively(); require(mkdirs()) }
        val dir = File(root, "dmn")
        val oldContext = Dmn.context; val oldSource = Dmn.source; val oldTestRouter = Dmn.testRouter
        val oldText = DmnTiles.getText; val oldDownload = DmnTiles.download
        try {
            Dmn.forget(); DmnTiles.forget(); Dmn.testRouter = null
            Dmn.context = object : ContextWrapper(app) { override fun getNoBackupFilesDir(): File = root }
            val trip = listOf(LatLon(52.9850, -2.1950), LatLon(53.3940, -2.2100)) // near Stoke -> Cheadle
            var pieces = 0
            DmnTiles.download = { url, target, limit -> pieces++; oldDownload(url, target, limit) }

            assertTrue("this trip's road pieces download online",
                Dmn.prepareOnline(trip, System.nanoTime() + 240_000_000_000L))
            assertEquals(DmnTiles.Offline.READY, DmnTiles.offline(dir, trip))
            assertTrue("only this trip's pieces, not the whole UK ($pieces)", pieces in 1..60)

            val routes = Dmn.route(trip, null)
            assertTrue("a real route Stoke -> Cheadle", routes.isNotEmpty())
            val km = routes.first().distance / 1000.0
            val crow = Geo.distance(trip[0], trip[1]) / 1000.0
            assertTrue("plausible road distance ($km km vs $crow km straight)", km in crow..crow * 2.0)

            // Kept for next time: the same trip routes with no network.
            DmnTiles.forget(); Dmn.forget()
            DmnTiles.getText = { throw AssertionError("no metadata with no signal") }
            DmnTiles.download = { _, _, _ -> throw AssertionError("no download with no signal") }
            assertTrue(Dmn.prepareOnline(trip, System.nanoTime() + 5_000_000_000L))
            assertTrue(Dmn.route(trip, null).isNotEmpty())
        } finally {
            DmnTiles.getText = oldText; DmnTiles.download = oldDownload
            Dmn.context = oldContext; Dmn.source = oldSource; Dmn.testRouter = oldTestRouter
            Dmn.forget(); DmnTiles.forget()
            root.deleteRecursively()
        }
    }
}
