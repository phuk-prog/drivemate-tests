package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.3: only provider and location-mode broadcasts re-evaluate location services. */
class LocationAvailabilityPolicyTest {
    @Test fun providerChangeTriggersReevaluation() {
        assertTrue(LocationAvailabilityPolicy.triggers("android.location.PROVIDERS_CHANGED"))
    }

    @Test fun locationModeChangeTriggersReevaluation() {
        assertTrue(LocationAvailabilityPolicy.triggers("android.location.MODE_CHANGED"))
    }

    @Test fun unrelatedAndMissingActionsDoNotTrigger() {
        assertFalse(LocationAvailabilityPolicy.triggers("android.intent.action.BATTERY_CHANGED"))
        assertFalse(LocationAvailabilityPolicy.triggers(null))
        assertFalse(LocationAvailabilityPolicy.triggers(""))
    }
}
