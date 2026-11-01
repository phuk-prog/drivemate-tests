package com.drivemate.app.data

import com.drivemate.app.data.OfflineInventory.Area
import com.drivemate.app.data.OfflineInventory.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 1.2 / 1.8: the area row shows its download size and an offline-ready label. */
class OfflineAreaLabelTest {
    private val size = 1_234_000_000L

    @Test
    fun availableAreaShowsDownloadSizeBeforeDownload() {
        val line = OfflineInventory.areaLine(Area("uk-wales", "Wales", size, State.AVAILABLE))
        assertEquals("Wales · download 1.2 GB · not on this phone yet", line)
        assertFalse(line.contains("offline-ready"))
    }

    @Test
    fun usableInstalledAreaIsOfflineReady() {
        val line = OfflineInventory.areaLine(Area("uk-wales", "Wales", size, State.INSTALLED, usable = true))
        assertEquals("Wales · 1.2 GB · offline-ready", line)
    }

    @Test
    fun installedButNotUsableIsNotOfflineReady() {
        val line = OfflineInventory.areaLine(Area("uk-wales", "Wales", size, State.INSTALLED, usable = false))
        assertTrue(line.endsWith("installed, not usable yet"))
        assertFalse(line.contains("offline-ready"))
    }

    @Test
    fun updateAvailableStaysOfflineReadyWithNewerNote() {
        val line = OfflineInventory.areaLine(Area("uk-wales", "Wales", size, State.UPDATE_AVAILABLE, usable = true))
        assertEquals("Wales · 1.2 GB · offline-ready; a newer version is available", line)
    }

    @Test
    fun partlyDownloadedShowsTotalAndSaved() {
        val line = OfflineInventory.areaLine(
            Area("uk-wales", "Wales", size, State.PARTLY_DOWNLOADED, savedBytes = 400_000_000L))
        assertEquals("Wales · download 1.2 GB · partly downloaded (400 MB saved)", line)
    }
}
