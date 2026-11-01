package com.drivemate.app.nav

import com.drivemate.app.data.TrafficDetourMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 11 (traffic detours): the Auto, Ask and Never choice is read from the saved name, or
 * from the earlier "always ask" switch when there is no name. Never stops automatic offers only.
 */
class C11TrafficDetourModeTest {
    /** 11: a saved name wins; without one, the earlier "always ask" switch decides (on = Ask, off = Auto). */
    @Test fun q11_trafficDetourModeFromSavedNameOrLegacySwitch() {
        assertEquals(TrafficDetourMode.NEVER, TrafficDetourMode.fromStored("NEVER", legacyAlwaysAsk = true))
        assertEquals(TrafficDetourMode.AUTO, TrafficDetourMode.fromStored("AUTO", legacyAlwaysAsk = true))
        assertEquals(TrafficDetourMode.ASK, TrafficDetourMode.fromStored(null, legacyAlwaysAsk = true))
        assertEquals(TrafficDetourMode.AUTO, TrafficDetourMode.fromStored(null, legacyAlwaysAsk = false))
        assertEquals("an unknown name falls back to the legacy switch", TrafficDetourMode.ASK,
            TrafficDetourMode.fromStored("SOMETIMES", legacyAlwaysAsk = true))
    }

    /** 11: Never blocks automatic traffic offers; Auto and Ask still allow them. */
    @Test fun q11_neverBlocksAutomaticTrafficOffersOnly() {
        assertFalse(RouteSwitchPolicy.automaticOfferAllowed(TrafficDetourMode.NEVER))
        assertTrue(RouteSwitchPolicy.automaticOfferAllowed(TrafficDetourMode.ASK))
        assertTrue(RouteSwitchPolicy.automaticOfferAllowed(TrafficDetourMode.AUTO))
    }
}
