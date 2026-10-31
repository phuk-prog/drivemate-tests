package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C03.4: the car arrow glides between GPS fixes, never shuffles back, and stays bounded. */
class C03ArrowSmoothnessTest {
    private val second = 1_000_000_000L
    private val frame = second / 60 // 60 Hz screen ticks

    private fun ticks(p: ArrowPredictor, from: Long, until: Long): List<Double> =
        generateSequence(from) { it + frame }.takeWhile { it <= until }.map { p.alongAt(it)!! }.toList()

    @Test fun betweenFixesTheArrowMovesForwardEveryFrameInSmallSteps() {
        val p = ArrowPredictor()
        val speed = 31.0 // ~70 mph
        p.onFix(10 * second, 500.0, speed, 0.0, usable = true)
        val path = ticks(p, 10 * second, 11 * second)
        for ((a, b) in path.zipWithNext()) {
            assertTrue("never backwards between fixes", b >= a)
            assertTrue("each 60 Hz step is at most one frame of travel", b - a <= speed / 60 + 1e-9)
        }
        assertEquals(500.0 + speed, path.last(), 1e-3) // 60 frames of whole nanoseconds end 40 ns short
    }

    @Test fun aSmallStepBackFromANewFixIsHeldThenTheArrowCarriesOn() {
        val p = ArrowPredictor()
        p.onFix(10 * second, 500.0, 20.0, 0.0, usable = true)
        val before = p.alongAt(11 * second)!! // 520 m
        // New fix lands 8 m behind what is drawn: a normal GPS wobble, not a real correction.
        p.onFix(11 * second, before - 8.0, 20.0, 0.0, usable = true)
        assertEquals(before, p.alongAt(11 * second)!!, 1e-9)
        val path = ticks(p, 11 * second, 12 * second)
        assertTrue(path.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(path.last() > before)
    }

    @Test fun aLargeCorrectionMovesTheArrowBack() {
        val p = ArrowPredictor()
        p.onFix(10 * second, 500.0, 20.0, 0.0, usable = true)
        val before = p.alongAt(11 * second)!!
        p.onFix(11 * second, before - ArrowPredictor.BACK_TOLERANCE_M - 5.0, 20.0, 0.0, usable = true)
        assertEquals(before - ArrowPredictor.BACK_TOLERANCE_M - 5.0, p.alongAt(11 * second)!!, 1e-9)
    }

    @Test fun predictionStopsAtTwoSecondsAndEndsWhenTheFixIsStale() {
        val p = ArrowPredictor()
        p.onFix(10 * second, 500.0, 25.0, 0.0, usable = true)
        val atCap = p.alongAt(12 * second)!!
        assertEquals(550.0, atCap, 1e-6)
        assertEquals("no further travel after MAX_SECONDS", atCap, p.alongAt(12 * second + second / 2)!!, 1e-9)
        assertNull("no prediction from a stale fix", p.alongAt(13 * second + 1))
    }

    @Test fun brakingStopsTheArrowWhereTheCarWouldStop() {
        val p = ArrowPredictor()
        p.onFix(10 * second, 500.0, 6.0, -4.0, usable = true) // stops after 1.5 s, 4.5 m on
        val path = ticks(p, 10 * second, 12 * second)
        assertTrue(path.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(504.5, path.last(), 1e-6)
    }

    @Test fun untrustedSlowOrInvalidFixesDoNotAnimate() {
        val p = ArrowPredictor()
        p.onFix(10 * second, 500.0, 20.0, 0.0, usable = false)
        assertNull(p.alongAt(10 * second + frame))
        p.onFix(10 * second, 500.0, ArrowPredictor.MIN_SPEED - 0.1, 0.0, usable = true)
        assertNull(p.alongAt(10 * second + frame))
        p.onFix(10 * second, Double.NaN, 20.0, 0.0, usable = true)
        assertNull(p.alongAt(10 * second + frame))
        p.onFix(10 * second, 500.0, 20.0, 0.0, usable = true)
        assertNull("time before the fix is not extrapolated", p.alongAt(10 * second - 1))
    }
}
