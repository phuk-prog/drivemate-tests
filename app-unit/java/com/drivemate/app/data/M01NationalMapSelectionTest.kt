package com.drivemate.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * M01 regression: an already installed Scottish regional archive must not override the
 * published one-piece UK archive. Otherwise a zoomed-out map can draw only a bounded region.
 * This uses the real production MapData selection, not a copy of its decision algorithm.
 */
class M01NationalMapSelectionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun visibleOnlineUkMapDoesNotShrinkToAnInstalledScottishRegion() {
        val regional = temp.newFile("installed-scotland.pmtiles")
        val national = MapData.Download(
            "https://github.com/phuk-prog/drivemate-map/releases/download/map-data-ukmap-r40883954274-a1/drivemate-v1.pmtiles",
            MapData.Asset("b".repeat(64), 1_781_936_174L, "map-data-ukmap-r40883954274-a1"),
            MapLayout.OWN, "map-data-ukmap-r40883954274-a1"
        )
        val scotlandTag = "map-data-region-scotland-r40828955970-a1"
        val online = OwnMap.selectMap(national, regional, MapLayout.OWN, scotlandTag,
            networkAvailable = true)
        assertEquals("pmtiles://${national.url}", online.tiles)
        assertEquals(national.snapshotTag, online.snapshotTag)
        val offline = OwnMap.selectMap(national, regional, MapLayout.OWN, scotlandTag,
            networkAvailable = false)
        assertEquals("pmtiles://file://${regional.absolutePath}", offline.tiles)
        assertEquals(scotlandTag, offline.snapshotTag)
        val countryFile = temp.newFile("installed-whole-uk.pmtiles")
        val localUk = OwnMap.selectMap(national, countryFile, MapLayout.OWN,
            national.snapshotTag, networkAvailable = true)
        assertEquals("pmtiles://file://${countryFile.absolutePath}", localUk.tiles)
    }

    @Test fun publishedWholeUkWinsWhenScottishRegionIsLocallyInstalled() {
        val sha = "a".repeat(64)
        val regionTag = "map-data-region-scotland-r40828955970-a1"
        val region = PublishedRegionCatalog.Region(
            "scotland", "Scotland",
            PublishedRegionCatalog.Bounds(-8.7, 54.5, -0.2, 60.9),
            PublishedRegionCatalog.Asset(regionTag, sha, 300),
            PublishedRegionCatalog.Asset(regionTag, sha, 700),
            "drivemate-v1.pmtiles"
        )
        val national = MapData.Asset(
            "b".repeat(64), 1_781_936_174L, "map-data-ukmap-r40883954274-a1"
        )
        val regionalRoot = temp.newFolder("regional")
        File(regionalRoot, RegionPackFiles.fileName(region)).writeText("cached older region")

        val ukField = MapData::class.java.getDeclaredField("ukMap").apply { isAccessible = true }
        val activeField = MapData::class.java.getDeclaredField("activeRegion").apply { isAccessible = true }
        val rootField = RegionPacks::class.java.getDeclaredField("root").apply { isAccessible = true }
        val oldUk = ukField.get(null)
        val oldRegion = activeField.get(null)
        val oldRoot = rootField.get(null)
        try {
            ukField.set(null, national)
            activeField.set(null, region)
            rootField.set(null, regionalRoot)
            assertNotNull("Existing Scottish regional archive really is installed", RegionPacks.local(region))

            val choice = MapData.mapArchiveDownload()
            assertNotNull("The verified published whole-UK archive is available", choice)
            assertEquals(national.tag, choice!!.snapshotTag)
            assertEquals(MapLayout.OWN, choice.layout)
            assertEquals(MapReleaseSource.base + "/" + national.tag + "/drivemate-v1.pmtiles", choice.url)
            assertEquals(national.sha256, choice.asset.sha256)
            assertEquals(national.bytes, choice.asset.bytes)
            assertEquals("The download worker must choose the same national map",
                national.tag, MapData.nationalMapForDownload()?.snapshotTag)
            activeField.set(null, null)
            assertEquals("A failed or missing regional lookup must not hide the UK archive",
                national.tag, MapData.mapArchiveDownload()?.snapshotTag)
        } finally {
            ukField.set(null, oldUk)
            activeField.set(null, oldRegion)
            rootField.set(null, oldRoot)
        }
    }
    @Test fun installedWholeUkAlwaysWinsAheadOfRegionalOfflineFallback() {
        val ukTag = "map-data-ukmap-r40883954274-a1"
        val regionTag = "map-data-region-scotland-r40828955970-a1"
        assertEquals(false, OwnMap.useRegionalFallback(ukTag, ukTag, "scotland", false))
        assertEquals(false, OwnMap.useRegionalFallback(ukTag, ukTag, "scotland", true))
        assertEquals(true, OwnMap.useRegionalFallback(ukTag, regionTag, "scotland", false))
        assertEquals(false, OwnMap.useRegionalFallback(ukTag, regionTag, "scotland", true))
        assertEquals(true, OwnMap.useRegionalFallback(regionTag, null, "scotland", true))
        assertEquals(false, OwnMap.useRegionalFallback(regionTag, null, "wales", false))
    }

}
