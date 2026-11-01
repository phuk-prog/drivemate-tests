package com.drivemate.app.map

import com.drivemate.app.nav.ArrowPredictor
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Register 13.3: on a steady drive the arrow moves forward on every 60 Hz frame, including across
 * each fix, and never falls behind the car. Fixes at 0.5 to 2 s intervals (the rates the predictor
 * is built for); the arrow must not hold still for any frame.
 */
class C13SteadyArrowMotionTest {
    private val second = 1_000_000_000L
    private val frame = second / 60
    private val speed = 20.0 // m/s, about 45 mph
    private val start = 10 * second

    /** Arrow position and true car position for each frame of [seconds] of steady driving. */
    private fun drive(intervalMs: Long, seconds: Int): List<Pair<Double, Double>> {
        val p = ArrowPredictor()
        val out = ArrayList<Pair<Double, Double>>()
        val end = start + seconds * second
        var fix = start
        while (fix < end) {
            p.onFix(fix, truth(fix), speed, 0.0, usable = true)
            val next = fix + intervalMs * 1_000_000L
            var t = fix
            while (t < next) {
                out += p.alongAt(t)!! to truth(t)
                t += frame
            }
            fix = next
        }
        return out
    }

    private fun truth(nanos: Long): Double = 500.0 + speed * (nanos - start) / 1e9

    @Test fun arrowAdvancesOnEveryFrameAcrossFixesAtNormalRates() {
        for (intervalMs in listOf(500L, 1_000L, 1_500L, 2_000L)) {
            val path = drive(intervalMs, seconds = 30).map { it.first }
            for ((a, b) in path.zipWithNext()) {
                assertTrue("$intervalMs ms fixes: arrow held still at $b", b - a > 1e-9)
                assertTrue("$intervalMs ms fixes: arrow jumped ${b - a} m in one frame", b - a <= speed / 60 + 1e-9)
            }
        }
    }

    @Test fun arrowStaysOnTheCarWhileDriving() {
        for (intervalMs in listOf(500L, 1_000L, 2_000L)) {
            for ((shown, true_) in drive(intervalMs, seconds = 30)) {
                assertTrue("$intervalMs ms fixes: arrow ${shown - true_} m from the car", kotlin.math.abs(shown - true_) < 0.5)
            }
        }
    }
}
