package com.drivemate.tests.cat01

import com.drivemate.app.data.Valhalla
import com.drivemate.app.nav.Format
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.TurnInstructions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 1, law tests: the app against the law and official sources only, never the owner's
 * choices. References are in drivemate-app/register/01-turn-by-turn-guidance.md.
 */
class TurnByTurnLawTest {
    private val uk = Format(imperial = true, yards = true)

    /** L4 TSRGD 2016: distances on UK road signs are in miles and yards, never metres or feet. */
    @Test fun ukDistancesAreMilesAndYards() {
        for (m in listOf(30.0, 80.0, 150.0, 300.0, 390.0)) {
            val shown = uk.distance(m)
            assertTrue("$m m shown as $shown", shown.endsWith(" yd"))
        }
        for (m in listOf(500.0, 1_609.344, 24_140.0)) {
            val shown = uk.distance(m)
            assertTrue("$m m shown as $shown", shown.endsWith(" mi"))
        }
    }

    /** L6 National Highways: countdown markers at 300, 200 and 100 yards read the same on the card. */
    @Test fun motorwayCountdownMarkersMatchTheCard() {
        assertEquals("300 yd", uk.distance(274.32))
        assertEquals("200 yd", uk.distance(182.88))
        assertEquals("100 yd", uk.distance(91.44))
    }

    /** L6: advance direction signs at 1 mile and ½ mile read the same on the card. */
    @Test fun advanceSignDistancesMatchTheCard() {
        assertEquals("1.0 mi", uk.distance(1_609.344))
        assertEquals("0.5 mi", uk.distance(804.672))
    }

    /** L4/L5 TSRGD and Traffic Signs Manual ch. 7: road numbers written as on signs, including (M). */
    @Test fun roadNumbersAsOnSigns() {
        assertEquals("Merge onto the M60", TurnInstructions.describe("merge", "slight left", "M60", 0, true))
        assertEquals("Merge onto the A1(M)", TurnInstructions.describe("merge", "slight left", "A1(M)", 0, true))
    }

    /** L1 Highway Code rule 186: UK roundabouts go clockwise (traffic keeps left); not in France. */
    @Test fun ukDrivesOnTheLeft() {
        assertTrue("Manchester", Valhalla.drivesOnLeft(LatLon(53.48, -2.24)))
        assertTrue("Belfast", Valhalla.drivesOnLeft(LatLon(54.60, -5.93)))
        assertFalse("Paris", Valhalla.drivesOnLeft(LatLon(48.85, 2.35)))
    }
}
