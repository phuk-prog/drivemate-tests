package com.drivemate.app.map

import org.junit.Assert.assertEquals
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

    @Test fun enabledPointerTurnsOffOnRevokeAndReturnsOnlyAfterRegrant() {
        // Models the permission state supplied by Android Settings on every refresh.
        val permissionByStage = listOf(true, false, false, true)
        val enabledByStage = permissionByStage.map { granted ->
            LocationComponentPermissionPolicy.enabled(granted, reliablePointer = true)
        }
        assertEquals(listOf(true, false, false, true), enabledByStage)
        // Restored permission still cannot reveal a stale/unreliable navigation position.
        assertFalse(LocationComponentPermissionPolicy.enabled(true, reliablePointer = false))
    }
}
