package com.drivemate.app

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Basic renderer proof: a plain map with a fixed style paints one known symbol. */
class PlainMapTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun report(phase: String, vararg fields: Pair<String, Any>) {
        val json = JSONObject().put("phase", phase)
        fields.forEach { json.put(it.first, it.second) }
        instrumentation.sendStatus(2, Bundle().apply { putString("category_a", json.toString()) })
    }

    private fun <T> onMain(block: () -> T): T {
        var value: T? = null
        var error: Throwable? = null
        instrumentation.runOnMainSync { try { value = block() } catch (t: Throwable) { error = t } }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST") return value as T
    }

    private fun waitFor(phase: String, timeoutMs: Long, condition: () -> Boolean) {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (condition()) { report(phase, "passed" to true); return }
            SystemClock.sleep(100)
        }
        report("failure-detail", "check_id" to phase, "failure_kind" to "timeout", "timeout_ms" to timeoutMs.toInt())
        throw AssertionError(phase)
    }

    private fun amberPixels(map: MapLibreMap): Int {
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        onMain { map.snapshot { image -> bitmap = image; latch.countDown() } }
        if (!latch.await(10, TimeUnit.SECONDS)) throw AssertionError("snapshot-timeout")
        val image = bitmap ?: throw AssertionError("snapshot-missing")
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return pixels.count { color ->
            val red = color shr 16 and 255; val green = color shr 8 and 255; val blue = color and 255
            red > 220 && green in 110..210 && blue < 80
        }
    }

    @Test fun plainMapPaintsKnownSymbol() {
        val activity = instrumentation.startActivitySync(
            android.content.Intent(instrumentation.context, PlainMapActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as PlainMapActivity
        try {
            waitFor("plain-map-activity", 30_000) { activity.map != null }
            val map = activity.map!!
            val imageId = "plain-known-image"
            onMain {
                val style = Style.Builder().fromJson(
                    """{"version":8,"sources":{},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#FFFFFF"}}]}""")
                map.setStyle(style) { loaded ->
                    val image = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                    image.eraseColor(Color.rgb(255, 160, 0))
                    loaded.addImage(imageId, image)
                    loaded.addSource(GeoJsonSource("plain-source",
                        Feature.fromGeometry(Point.fromLngLat(-2.2374, 53.4808))))
                    loaded.addLayer(SymbolLayer("plain-layer", "plain-source").withProperties(
                        PropertyFactory.iconImage(imageId),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)))
                    map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        LatLng(53.4808, -2.2374), 15.0))
                }
            }
            waitFor("plain-map-style", 30_000) { onMain { map.style?.getLayer("plain-layer") != null } }
            var amber = 0
            waitFor("plain-map-paints", 15_000) { amber = amberPixels(map); amber > 100 }
            assertTrue("plain-map-paints", amber > 100)
        } finally {
            onMain { activity.finish() }
        }
    }
}
