package com.drivemate.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drawing several published regions as one map, checked against the real published catalogue
 * (phuk-prog/DriveMate-Map map-regions, 46 regions, fetched 2026-10-24).
 */
class RegionMosaicTest {
    private val catalog = PublishedRegionCatalog.parse(
        javaClass.classLoader!!.getResource("region-catalog-2026-10-24.json")!!.readText())
    private fun region(id: String) = catalog.regions.single { it.id == id }

    /** A screenshot: Radcliffe to Bramhall, Swinton to Chadderton, centred on Didsbury. */
    private val manchesterOverview = PublishedRegionCatalog.Bounds(-2.45, 53.30, -2.05, 53.62)

    @Test fun overviewAcrossGreaterManchesterDrawsTheSurroundingRegions() {
        val primary = catalog.selectForViewport(53.42, -2.23, manchesterOverview)!!
        assertEquals("greater-manchester", primary.id)
        val ids = catalog.neighbours(primary, manchesterOverview.expanded(0.25)).map { it.id }
        val drawn = listOf(primary) + catalog.regions.filter { it.id in ids }
        assertEquals(ids.toString(), PublishedRegionCatalog.Gap.NONE, catalog.gap(manchesterOverview, drawn))
        assertFalse("Manchester lies inside Greater Manchester and adds nothing: $ids", "manchester" in ids)
        assertTrue(ids.size <= PublishedRegionCatalog.MAX_NEIGHBOURS)
    }

    @Test fun neighboursAreCappedAndFillTheMiddleFirst() {
        val wide = PublishedRegionCatalog.Bounds(-3.5, 52.5, -0.5, 54.5)
        val got = catalog.neighbours(region("greater-manchester"), wide)
        assertTrue(got.size <= PublishedRegionCatalog.MAX_NEIGHBOURS)
        // The middle of this view is the Peak District / West Yorkshire border, not its edges.
        val middle = catalog.owner(53.5, -2.0)!!
        assertTrue("$middle not drawn: $got", got.any { it.id == middle.id } || middle.id == "greater-manchester")
    }

    /**
     * Emulator run 40146011887: zoomed out from Princess Road (Preston to Stoke), Cheshire became the
     * main region and Greater Manchester, in the middle of the screen, was left out: Cheshire's
     * bounding box spans Manchester but its archive does not.
     */
    @Test fun zoomedOutFromManchesterKeepsGreaterManchester() {
        val view = PublishedRegionCatalog.Bounds(-2.90, 52.95, -1.60, 53.95)
        val primary = catalog.selectForViewport(53.45, -2.25, view)!!
        val drawn = listOf(primary) + catalog.neighbours(primary, view.expanded(0.25))
        assertTrue(drawn.map { it.id }.toString(), drawn.any { it.id == "greater-manchester" })
        assertTrue(catalog.drawnAt(53.45, -2.25, drawn))
        assertFalse("Cheshire alone does not draw Manchester", catalog.drawnAt(53.45, -2.25, listOf(region("cheshire"))))
    }

    @Test fun noViewMeansNoNeighbours() {
        assertTrue(catalog.neighbours(region("manchester"), null).isEmpty())
        assertTrue(catalog.neighbours(region("manchester"), PublishedRegionCatalog.Bounds(1.0, 1.0, 0.0, 0.0)).isEmpty())
    }

    @Test fun gapSaysWhetherZoomingInOrPublishingIsNeeded() {
        val gm = region("greater-manchester")
        assertEquals(PublishedRegionCatalog.Gap.NOT_DRAWN, catalog.gap(manchesterOverview.expanded(0.5), listOf(gm)))
        val drawn = listOf(gm) + catalog.neighbours(gm, manchesterOverview.expanded(0.5))
        assertEquals(PublishedRegionCatalog.Gap.NONE, catalog.gap(manchesterOverview, drawn))
        // Far out in the Irish Sea, nothing is published.
        assertEquals(PublishedRegionCatalog.Gap.NOT_PUBLISHED,
            catalog.gap(PublishedRegionCatalog.Bounds(-6.0, 53.5, -5.2, 53.9), listOf(gm)))
    }

    private fun style(): JSONObject {
        val layers = JSONArray()
            .put(JSONObject().put("id", "background").put("type", "background"))
            .put(JSONObject().put("id", "road-primary-casing").put("type", "line").put("source", "drivemate"))
            .put(JSONObject().put("id", "road-primary").put("type", "line").put("source", "drivemate"))
            .put(JSONObject().put("id", "signs").put("type", "symbol").put("source", "detail"))
        return JSONObject().put("sources", JSONObject()
            .put("drivemate", JSONObject().put("type", "vector").put("url", "pmtiles://https://example/gm.pmtiles").put("maxzoom", 14))
            .put("detail", JSONObject().put("type", "vector"))).put("layers", layers)
    }

    @Test fun eachNeighbourGetsItsOwnSourceAndACopyOfEveryMapLayerBelowTheOriginal() {
        val s = RegionMosaic.add(style(), listOf("cheshire" to "https://dl/ch.pmtiles", "lancashire" to "https://dl/la.pmtiles"))
        val sources = s.getJSONObject("sources")
        assertEquals("pmtiles://https://dl/ch.pmtiles", sources.getJSONObject("drivemate@cheshire").getString("url"))
        assertEquals(14, sources.getJSONObject("drivemate@lancashire").getInt("maxzoom"))
        val ids = (0 until s.getJSONArray("layers").length()).map { s.getJSONArray("layers").getJSONObject(it).getString("id") }
        assertEquals(listOf("background",
            "road-primary-casing@cheshire", "road-primary-casing@lancashire", "road-primary-casing",
            "road-primary@cheshire", "road-primary@lancashire", "road-primary", "signs"), ids)
        val copy = s.getJSONArray("layers").getJSONObject(1)
        assertEquals("drivemate@cheshire", copy.getString("source"))
    }

    @Test fun noNeighboursLeavesTheStyleUnchanged() {
        val before = style().toString()
        assertEquals(before, RegionMosaic.add(style(), emptyList()).toString())
    }

    @Test fun regionSuffixDoesNotHideACasingLayer() {
        assertEquals("road-primary-casing", RegionMosaic.baseId("road-primary-casing@cheshire"))
        assertTrue(RegionMosaic.baseId("road-primary-casing@cheshire").endsWith("-casing"))
        assertEquals("road-primary", RegionMosaic.baseId("road-primary"))
    }
}
