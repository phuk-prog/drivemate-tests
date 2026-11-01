package com.drivemate.tests.cat10

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivemate.app.nav.GpsFilter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category 3.1 simulated timing check on the emulator (owner decision: no device check; use
 * simulated emulator checks). Sixty seconds of simulated fixes at 20 Hz go through the real
 * GpsFilter on the emulator. It checks that nearly every fix is accepted (delivered rate close to
 * the 20 Hz input) and that the 95th-percentile time to accept a fix stays under 20 ms.
 * Scope: software processing time only. It is not a receiver measurement and not a render time.
 */
@RunWith(AndroidJUnit4::class)
class GpsSimulatedTimingScreenTest {
    private val hertz = 20
    private val seconds = 60
    private val minAcceptedShare = 0.95
    private val maxP95Nanos = 20_000_000L

    private fun fix(index: Int) = Location("gps").apply {
        // Moves about 1 m per fix (about 20 m/s) so each fix is a real, consistent step.
        latitude = 53.42 + index * 0.00001
        longitude = -2.26
        accuracy = 5f
        elapsedRealtimeNanos = (100_000L + index * 1000L / hertz) * 1_000_000L
        speed = 20f
    }

    @Test fun twentyHertzSimulatedFixesAreAcceptedAndAcceptLatencyStaysUnderTwentyMilliseconds() {
        val filter = GpsFilter()
        val total = hertz * seconds
        val latencies = LongArray(total)
        var accepted = 0
        var firstAccepted: Location? = null
        for (i in 0 until total) {
            val input = fix(i)
            val start = System.nanoTime()
            val out = filter.accept(input)
            latencies[i] = System.nanoTime() - start
            if (out != null) {
                accepted++
                if (firstAccepted == null) firstAccepted = out
            }
        }
        assertNotNull("the first valid simulated fix must be accepted", firstAccepted)
        val share = accepted.toDouble() / total
        assertTrue("accepted $accepted of $total fixes (share $share)", share >= minAcceptedShare)
        latencies.sort()
        val p95 = latencies[(total * 0.95).toInt().coerceAtMost(total - 1)]
        assertTrue("p95 accept latency ${p95 / 1_000_000.0} ms exceeds 20 ms", p95 <= maxP95Nanos)
    }
}
