package com.drivemate.app.data

import com.drivemate.app.data.PlacePolicy.Candidate
import com.drivemate.app.data.PlacePolicy.Entrance
import com.drivemate.app.data.PlacePolicy.Freshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** C04.3: ranking and data freshness of UK search results (pure policy, JVM). */
class C04PlaceRankingTest {
    private val today = LocalDate.of(2026, 11, 1)

    private fun c(name: String, match: Double, distance: Double, date: LocalDate? = today) =
        Candidate(name, match, distance, Entrance.UNKNOWN, date)

    @Test
    fun rankingPutsHigherMatchFirstThenNearerThenName() {
        val list = listOf(
            c("Beta", 0.8, 100.0), c("Alpha", 0.8, 100.0), c("Near", 0.8, 10.0), c("Best", 0.95, 900.0),
        )
        assertEquals(listOf("Best", "Near", "Alpha", "Beta"), PlacePolicy.ranked(list).map { it.name })
        assertEquals("Best", PlacePolicy.bestMatch(list)?.name)
    }

    @Test
    fun bestMatchOfEmptyListIsNull() {
        assertNull(PlacePolicy.bestMatch(emptyList()))
    }

    @Test
    fun freshnessLabelsFollowTheAgeThresholds() {
        assertEquals(Freshness.FRESH, PlacePolicy.freshness(LocalDate.of(2026, 10, 2), today))
        assertEquals(Freshness.FRESH, PlacePolicy.freshness(LocalDate.of(2026, 8, 3), today)) // exactly 90 days
        assertEquals(Freshness.AGING, PlacePolicy.freshness(LocalDate.of(2026, 7, 31), today)) // 93 days
        assertEquals(Freshness.AGING, PlacePolicy.freshness(LocalDate.of(2026, 5, 15), today)) // 170 days
        assertEquals(Freshness.STALE, PlacePolicy.freshness(LocalDate.of(2024, 1, 1), today))
    }

    @Test
    fun missingOrFutureDatesAreUnknownNotFresh() {
        assertEquals(Freshness.UNKNOWN, PlacePolicy.freshness(null, today))
        assertEquals(Freshness.UNKNOWN, PlacePolicy.freshness(LocalDate.of(2026, 11, 5), today))
        assertNull(PlacePolicy.ageDays(null, today))
    }

    @Test
    fun ageDaysCountsWholeDays() {
        assertEquals(170L, PlacePolicy.ageDays(LocalDate.of(2026, 5, 15), today))
        assertEquals(0L, PlacePolicy.ageDays(today, today))
    }
}
