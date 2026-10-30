package com.drivemate.app.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Permission changes must override a previously reliable GPS/navigation pointer. */
class LocationComponentPermissionPolicyTest {
    @Test fun revokedPermissionNeverEnablesAnOtherwiseReliablePointer() {
        assertFalse(LocationComponentPermissionPolicy.enabled(false, true))
    }

    @Test fun unreliablePositionNeverEnablesThePointerWithPermission() {
        assertFalse(LocationComponentPermissionPolicy.enabled(true, false))
    }

    @Test fun grantedPermissionAndReliablePositionMayEnablePointer() {
        assertTrue(LocationComponentPermissionPolicy.enabled(true, true))
    }

    @Test fun noPermissionOrReliabilityKeepsThePointerDisabled() {
        assertFalse(LocationComponentPermissionPolicy.enabled(false, false))
    }
}
