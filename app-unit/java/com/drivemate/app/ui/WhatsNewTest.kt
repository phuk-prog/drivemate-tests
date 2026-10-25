package com.drivemate.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app reads exactly what scripts/build_whats_new.py writes. */
class WhatsNewTest {
    private val page = """{"schema":1,"run_url":"https://github.com/x/actions/runs/1","app_commit":"abc","entries":[
        {"id":"position-uncertain-3s","title":"\"Position uncertain\" 3 seconds after GPS is lost","text":"When GPS fixes stop…",
         "facts":["In this build's test it showed 3.3 s after the last fix."],"checklist":["01.01"],
         "media":[{"file":"position-uncertain-3s-0.mp4","kind":"video","caption":"Recorded from the app"},
                  {"file":"position-uncertain-3s-1.webp","kind":"image","caption":"The warning on screen"}],
         "evidence":["a#b"]}],"left_out":[{"id":"arrow-1cm","reasons":["not run"]}]}"""

    @Test fun readsTheBuiltPage() {
        val p = WhatsNew.parse(page)
        assertEquals(1, p.entries.size)
        val e = p.entries.single()
        assertEquals("\"Position uncertain\" 3 seconds after GPS is lost", e.title)
        assertEquals(listOf("In this build's test it showed 3.3 s after the last fix."), e.facts)
        assertTrue(e.media[0].video)
        assertEquals("position-uncertain-3s-1.webp", e.media[1].file)
        assertEquals("https://github.com/x/actions/runs/1", p.runUrl)
    }

    @Test fun leftOutChangesNeverAppear() {
        assertTrue(WhatsNew.parse(page).entries.none { it.title.contains("arrow", ignoreCase = true) })
    }
}
