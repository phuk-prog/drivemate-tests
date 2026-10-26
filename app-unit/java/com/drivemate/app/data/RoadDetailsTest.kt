package com.drivemate.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner, 26 Oct 2026: "the map should contain all information possible": extra driving tags kept on the phone. */
class RoadDetailsTest {
    @Test fun drivingTagsAreKeptAndTheRestDropped() {
        val kept = RoadDetails.keep(mapOf("highway" to "primary", "name" to "Mill Lane", "maxheight" to "4.2",
            "access:conditional" to "no @ (Mo-Fr 08:00-09:30)", "surface" to "asphalt"))
        assertEquals(mapOf("maxheight" to "4.2", "access:conditional" to "no @ (Mo-Fr 08:00-09:30)", "surface" to "asphalt"), kept)
    }

    @Test fun roadsWithOnlyCommonTagsAreNotKept() {
        assertNull(RoadDetails.keep(mapOf("highway" to "residential", "surface" to "asphalt", "lit" to "yes")))
        assertNull(RoadDetails.keep(mapOf("highway" to "residential")))
    }

    @Test fun theListCoversSizeTimeLimitAndRoadRules() {
        for (tag in listOf("maxheight", "maxwidth", "maxweight", "maxlength", "hgv", "oneway:conditional",
                "maxspeed:variable", "maxspeed:type", "overtaking", "incline", "ford", "toll", "hov:lanes"))
            assertTrue(tag, tag in RoadDetails.DRIVING_TAGS)
    }
}
