package com.drivemate.app.car

import com.drivemate.app.nav.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Category 21.5 pure check: the phone-side trip that the car screen reads survives a car disconnect
 * (listener removed), and a reconnect (new listener) sees the current trip and then later changes.
 * It does not run a car session, so it does not prove Android Auto reconnects; that needs the DHU run.
 */
class C21CarBridgeReconnectTest {
    /** A trip snapshot with no navigation yet; identity (not content) is what the checks compare. */
    private fun snap() = CarSnapshot(nav = null, location = null, route = emptyList(), destination = null, format = Format(imperial = false))

    @Before fun clean() { CarBridge.publish(null) }

    @Test fun q21_5_tripSurvivesCarDisconnect() {
        val s1 = snap()
        CarBridge.publish(s1)
        val stop = CarBridge.listen { }
        stop() // car screen destroyed on disconnect
        assertSame(s1, CarBridge.snapshot)
    }

    @Test fun q21_5_disconnectedListenerIsNotCalled() {
        var calls = 0
        val stop = CarBridge.listen { calls++ }
        stop()
        CarBridge.publish(snap())
        assertEquals(0, calls)
    }

    @Test fun q21_5_reconnectSeesCurrentTripThenLaterChanges() {
        CarBridge.publish(snap())
        CarBridge.listen { }.invoke() // disconnect
        val missed = snap()
        CarBridge.publish(missed) // changes while the car is away
        var calls = 0
        val stop = CarBridge.listen { calls++ } // reconnect
        assertSame(missed, CarBridge.snapshot)
        val later = snap()
        CarBridge.publish(later)
        assertEquals(1, calls)
        assertSame(later, CarBridge.snapshot)
        stop()
    }

    @Test fun q21_5_phoneEndingTripClearsWhatTheCarReads() {
        CarBridge.publish(snap())
        CarBridge.publish(null)
        assertNull(CarBridge.snapshot)
    }
}
