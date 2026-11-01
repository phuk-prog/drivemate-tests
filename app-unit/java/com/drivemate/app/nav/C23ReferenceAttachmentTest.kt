package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Category 23.2: a flagged segment carries only a coarse point and a plain map revision. */
class C23ReferenceAttachmentTest {
    @Test fun pointIsRoundedToAboutElevenMetres() {
        val reference = ReferenceAttachment.forSegment(53.4080123, -2.2011987, "gb-2026.10")!!
        assertEquals(53.408, reference.latitude, 0.0)
        assertEquals(-2.2012, reference.longitude, 0.0)
        assertEquals("gb-2026.10", reference.mapRevision)
    }

    @Test fun invalidCoordinatesAreRefused() {
        assertNull(ReferenceAttachment.forSegment(Double.NaN, 0.0, null))
        assertNull(ReferenceAttachment.forSegment(91.0, 0.0, null))
        assertNull(ReferenceAttachment.forSegment(0.0, 181.0, null))
    }

    @Test fun revisionWithFreeTextOrCoordinatesIsDropped() {
        assertNull(ReferenceAttachment.revision("home 53.40,-2.20"))
        assertNull(ReferenceAttachment.revision("   "))
        assertNull(ReferenceAttachment.revision(null))
        assertEquals("gb:2026", ReferenceAttachment.revision(" gb:2026 "))
    }

    @Test fun attachKeepsIdentityAndAnswerAndCoarsensThePoint() {
        val raw = RoadObservation(
            id = "j1:check:SPEED", latitude = 53.4080123, longitude = -2.2011987,
            roadSection = "A6 Hillgate", direction = "north", question = "Is the limit 30?",
            firstLabel = "30 mph", secondLabel = "40 mph", firstProvider = "A", secondProvider = "B",
            observedAtMs = 1_800_000_000_000L, mapRevision = "gb:2026",
        )
        val attached = ReferenceAttachment.attach(raw)
        assertEquals("j1:check:SPEED", attached.id)
        assertEquals(53.408, attached.latitude, 0.0)
        assertEquals(-2.2012, attached.longitude, 0.0)
        assertEquals("gb:2026", attached.mapRevision)
    }
}
