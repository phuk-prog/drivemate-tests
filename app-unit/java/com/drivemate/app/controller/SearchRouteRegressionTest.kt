package com.drivemate.app.controller

import com.drivemate.app.data.Place
import com.drivemate.app.nav.LatLon
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Category 4.5 (search regressions): the search controller that feeds the destination
 * choice. A fake provider stands in for the network and the offline index, so these checks
 * need no connection. Covered: online results pass through unchanged; a late result from an
 * earlier search is dropped; a provider failure is reported rather than turned into a guess;
 * an empty answer stays empty. The search-to-route preview on a device is not covered here.
 */
class SearchRouteRegressionTest {
    private val nearStockport = LatLon(53.41, -2.16)
    private val stokeHome = Place("12 Example Road", "Stoke-on-Trent ST4 1AB", LatLon(53.0, -2.18))

    private class FixedProvider(private val answer: List<Place>) : SearchProvider {
        override suspend fun search(query: String, near: LatLon?, thorough: Boolean, useTomTom: Boolean): List<Place> = answer
    }

    private class FailingProvider : SearchProvider {
        override suspend fun search(query: String, near: LatLon?, thorough: Boolean, useTomTom: Boolean): List<Place> =
            throw IOException("search source unreachable")
    }

    @Test fun onlineResultsPassThroughUnchanged() = runBlocking {
        val controller = SearchController(FixedProvider(listOf(stokeHome)))
        val ticket = controller.nextGeneration()
        val found = controller.searchCurrent(ticket, "12 Example Road Stoke", nearStockport)
        assertEquals(listOf(stokeHome), found)
    }

    @Test fun lateResultFromAnEarlierSearchIsDropped() = runBlocking {
        val controller = SearchController(FixedProvider(listOf(stokeHome)))
        val earlier = controller.nextGeneration()
        controller.nextGeneration()
        assertNull("a superseded search must not be shown", controller.searchCurrent(earlier, "Stoke", nearStockport))
    }

    @Test fun providerFailureIsReportedNotTurnedIntoAGuess() = runBlocking {
        val controller = SearchController(FailingProvider())
        val ticket = controller.nextGeneration()
        try {
            controller.searchCurrent(ticket, "12 Example Road Stoke", nearStockport)
            fail("a failed search returned a result instead of failing")
        } catch (failed: IOException) {
            assertTrue(failed.message.orEmpty().contains("unreachable"))
        }
    }

    @Test fun emptyAnswerStaysEmpty() = runBlocking {
        val controller = SearchController(FixedProvider(emptyList()))
        val ticket = controller.nextGeneration()
        assertEquals(emptyList<Place>(), controller.searchCurrent(ticket, "zzz no such place", nearStockport))
    }
}
