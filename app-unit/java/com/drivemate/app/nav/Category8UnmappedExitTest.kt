package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Category 8.3 (Yew Street / Edgeley fixture, drivemate-map 3f2894e): OSM carries no exit tags on the
 * Armoury Roundabout, so the app must never state an exit ordinal. Announce after the exit instead,
 * with the neutral "Take the exit" wording, and say "next exit" only when mapped junctions confirm it.
 */
class Category8UnmappedExitTest {
    @Test fun unmappedExitNeverBecomesAnOrdinal() {
        val card = roundaboutCirculationInstruction(exit = 0, distance = 40.0, road = "Mercian Way")
        assertEquals("Take the exit onto Mercian Way", card)
        assertFalse(card.contains(Regex("\\d")))
        assertFalse(card.contains("st exit") || card.contains("nd exit") || card.contains("rd exit") || card.contains("th exit"))
    }

    @Test fun unmappedExitStaysNeutralFarFromTheExit() {
        assertEquals("Take the exit", roundaboutCirculationInstruction(exit = 0, distance = 300.0, road = ""))
    }

    @Test fun voiceMatchesCardWhenExitIsUnmapped() {
        assertEquals("Take the exit, left", roundaboutExitPrompt("left", nextExitKnown = false, exit = 0))
    }

    @Test fun nextExitOnlyWhenConfirmed() {
        assertEquals("Take the next exit", roundaboutCirculationInstruction(exit = 0, distance = 40.0, road = "", nextExitKnown = true))
    }
}
