package com.drivemate.app.data

import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 1.8 — seams between regional packages. A journey or home area that
 * crosses a boundary must take both neighbouring detail packages; a point with
 * no detail package must be reported as uncovered, never silently invented.
 */
class M03RegionSeamPlanningTest {
    private val west = "region-z8-x1-y2"
    private val east = "region-z8-x2-y2"

    // Two adjacent detail packages sharing the meridian lon -2.0.
    private val manifest = RegionalMapManifest.parse(
        """
        {
          "schema": 1, "status": "published", "base_zoom_max": 9, "detail_zoom_min": 10,
          "source_sha256": "${"a".repeat(64)}",
          "packages": {
            "base": {"sha256": "${"b".repeat(64)}", "bytes": 1000, "tiles": 10, "verified_tile_payloads": 10},
            "$west": {"sha256": "${"c".repeat(64)}", "bytes": 500, "tiles": 5, "verified_tile_payloads": 5,
                      "bounds": [-3.0, 53.0, -2.0, 54.0]},
            "$east": {"sha256": "${"d".repeat(64)}", "bytes": 700, "tiles": 7, "verified_tile_payloads": 7,
                      "bounds": [-2.0, 53.0, -1.0, 54.0]}
          }
        }
        """.trimIndent(),
        requirePublished = true,
    )

    @Test fun pointOnTheSeamTakesBothNeighbours() {
        val plan = RegionDownloadPlanner.around(manifest, 53.5, -2.0, 0.0)
        assertEquals(listOf("base", west, east), plan.packages)
        assertEquals(0, plan.uncoveredPoints)
        assertEquals(1000L + 500L + 700L, plan.downloadBytes)
    }

    @Test fun marginAcrossTheSeamPullsInTheEastNeighbour() {
        // 0.5 km west of the seam: only the west package contains the point,
        // but a 2 km margin reaches across, so the east package is needed too.
        val plan = RegionDownloadPlanner.around(manifest, 53.5, -2.0 - 0.5 / 67.0, 2.0)
        assertTrue(east in plan.packages)
        assertTrue(west in plan.packages)
        assertEquals(0, plan.uncoveredPoints)
    }

    @Test fun pointFarFromTheSeamTakesOnlyItsOwnPackage() {
        val plan = RegionDownloadPlanner.around(manifest, 53.5, -2.5, 0.0)
        assertEquals(listOf("base", west), plan.packages)
    }

    @Test fun routeAlongTheSeamTakesBothNeighboursWithNoGap() {
        val route = (0..10).map { LatLon(53.0 + it * 0.1, -2.0) }
        val plan = RegionDownloadPlanner.alongRoute(manifest, route, 0.0)
        assertEquals(listOf("base", west, east), plan.packages)
        assertEquals(0, plan.uncoveredPoints)
    }

    @Test fun areaWithNoDetailPackageIsReportedNotInvented() {
        val plan = RegionDownloadPlanner.around(manifest, 51.5, 0.0, 5.0)
        assertEquals(listOf("base"), plan.packages)
        assertEquals(1, plan.uncoveredPoints)
    }
}
