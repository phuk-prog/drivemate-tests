package com.drivemate.tests.cat05

import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Test

/** Highway Code 148–150: avoid distraction and retain proper control.
 * Source: https://www.gov.uk/guidance/the-highway-code/general-rules-techniques-and-advice-for-all-drivers-and-riders-103-to-158
 * This checks the app's conservative protection; the Code does not prescribe a GPS speed threshold.
 */
class ImmersiveNavigationLawTest {
    @Test fun distractingReplayCannotRunDuringActiveNavigationOrMeasuredMovement() {
        assertFalse(ReplaySafety.allowed(true, true, 0.0, 0))
        assertFalse(ReplaySafety.allowed(true, false, 10.0, 0))
        assertFalse(ReplaySafety.allowed(true, false, null, null))
    }
}
