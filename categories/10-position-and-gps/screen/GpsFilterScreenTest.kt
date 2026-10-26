package com.drivemate.tests.cat10

import com.drivemate.app.nav.GpsFilter

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Location API; GPS filtering only, not proof of physical receiver performance. */
@RunWith(AndroidJUnit4::class)
class GpsFilterScreenTest {
    private fun fix(time: Long, lat: Double = 53.42) = Location("gps").apply {
        latitude = lat; longitude = -2.26; accuracy = 5f
        elapsedRealtimeNanos = time * 1_000_000L; speed = 0f
    }

    @Test fun invalidFirstFixCannotPoisonFilter() {
        val filter = GpsFilter()
        assertNull(filter.accept(fix(100_000, Double.NaN)))
        assertNull(filter.accept(fix(100_000).apply { removeAccuracy() }))
        assertNull(filter.accept(fix(100_000).apply { speed = Float.NaN }))
        assertNotNull(filter.accept(fix(101_000)))
    }

    @Test fun callerMutationCannotChangeTheStoredInitialFix() {
        val filter = GpsFilter()
        val input = fix(100_000)
        val output = filter.accept(input)!!
        input.latitude = 54.0
        output.latitude = 54.0
        val next = filter.accept(fix(101_000))!!
        assertEquals(53.42, next.latitude, 0.000001)
    }

    @Test fun consistentFixesRecoverAfterOutlierButSingleJumpDoesNot() {
        val filter = GpsFilter()
        assertNotNull(filter.accept(fix(100_000, 54.0)))
        assertNull(filter.accept(fix(101_000)))
        assertNull(filter.accept(fix(102_000)))
        assertEquals(53.42, filter.accept(fix(103_000))!!.latitude, 0.000001)
    }
}
