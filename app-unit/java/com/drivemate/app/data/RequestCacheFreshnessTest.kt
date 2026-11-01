package com.drivemate.app.data

import com.drivemate.app.nav.LatLon
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Category 4.3 (provider freshness and cache): an answer from a free search source is reused
 * for ten minutes and then asked for again, so a stale answer is never kept longer than the
 * cache time-to-live. Uses an injected clock and fetch; no network is needed.
 */
class RequestCacheFreshnessTest {
    private val place = Place("High Street", "Stockport SK1 1AA", LatLon(53.4083, -2.1494))

    @Test fun answerIsReusedWithinTenMinutesAndFetchedAgainAfter() = runBlocking {
        var clockMs = 0L
        var fetches = 0
        val cache = RequestCache(now = { clockMs }, scope = this)
        val fetch: suspend () -> List<Place> = {
            fetches++
            listOf(place)
        }

        assertEquals(listOf(place), cache.get("high street", fetch))
        assertEquals(1, fetches)

        clockMs = 10 * 60_000L - 1
        assertEquals(listOf(place), cache.get("high street", fetch))
        assertEquals("answer inside the ten-minute window is reused", 1, fetches)

        clockMs = 10 * 60_000L
        assertEquals(listOf(place), cache.get("high street", fetch))
        assertEquals("answer at the ten-minute mark is fetched again", 2, fetches)
    }
}
