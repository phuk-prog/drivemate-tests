package com.drivemate.app

import android.app.Application
import com.drivemate.app.controller.NavigationSession
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Creates the navigation session the way the app does at startup, on Android 9 (API 28).
 * The app's minimum is API 26, so this must construct on API 28 without a missing platform class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class NavigationSessionStartupTest {
    @Test fun navigationSessionConstructsOnApi28() {
        val app = RuntimeEnvironment.getApplication() as Application
        NavigationSession(app).use { session ->
            assertNotNull(session)
        }
    }
}


/** Real session callbacks; routing/storage remain real except for synthetic state inputs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = DriveMateApp::class)
class SessionSettingsEffectTest {
    private fun withSession(block: (NavigationSession) -> Unit) {
        val app = RuntimeEnvironment.getApplication() as DriveMateApp
        val setup = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        try {
            kotlinx.coroutines.runBlocking { com.drivemate.app.data.SettingsRepository(app, setup).save(com.drivemate.app.data.Settings()) }
            val session = app.navigationSession
            try {
                val deadline = System.nanoTime() + 15_000_000_000L
                while (!session.settingsReady && System.nanoTime() < deadline) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    Thread.sleep(10)
                }
                org.junit.Assert.assertTrue("Real session settings must load", session.settingsReady)
                block(session)
            } finally { session.close() }
        } finally { setup.coroutineContext[kotlinx.coroutines.Job]!!.cancel() }
    }

    private fun state(session: NavigationSession, name: String, value: Any?) {
        val field = NavigationSession::class.java.getDeclaredField(name + "\$delegate")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(session) as androidx.compose.runtime.MutableState<Any?>).value = value
    }

    private fun mode(session: NavigationSession, mode: Mode) {
        val field = NavigationSession::class.java.getDeclaredField("navigationStore")
        field.isAccessible = true
        (field.get(session) as com.drivemate.app.controller.NavigationStateStore).update { it.copy(mode = mode) }
    }

    @Test fun routePreferenceChangesInvalidateThePreviouslyAcceptedPreview() = withSession { session ->
        val a = com.drivemate.app.nav.LatLon(53.48, -2.24)
        val route = com.drivemate.app.data.Route(listOf(a, com.drivemate.app.nav.LatLon(53.50, -2.20)),
            4_000.0, 600.0, emptyList(), "Synthetic preview")
        val changes: List<(com.drivemate.app.data.Settings) -> com.drivemate.app.data.Settings> = listOf(
            { it.copy(preferSimple = !it.preferSimple) },
            { it.copy(avoidRatRuns = !it.avoidRatRuns) })
        for (change in changes) {
            mode(session, Mode.PREVIEW)
            state(session, "routes", listOf(route))
            state(session, "selectedRoute", 0)
            state(session, "previewAvoidanceSnapshot", session.settings.avoid)
            val evidence = com.drivemate.app.nav.AvoidedRouteOptions.Evidence(route, "synthetic", 600.0, emptySet())
            state(session, "previewRouteChoices", listOf(com.drivemate.app.nav.AvoidedRouteOptions.Choice(evidence, null, false, false)))
            org.junit.Assert.assertTrue(session.canStartSelectedRoute)
            session.updateSettings(change(session.settings))
            org.junit.Assert.assertFalse("Old preview must not start after route preferences change", session.canStartSelectedRoute)
        }
    }
    @Test fun activePreferenceChangesRequestFreshRoutesWithoutLosingStops() = withSession { session ->
        val containerField = NavigationSession::class.java.getDeclaredField("container").apply { isAccessible = true }
        val container = containerField.get(session) as com.drivemate.app.controller.ApplicationContainer
        val providerField = com.drivemate.app.controller.RoutePlanningController::class.java.getDeclaredField("provider").apply { isAccessible = true }
        val original = providerField.get(container.routes)
        val requests = java.util.concurrent.atomic.AtomicReference<Triple<Boolean, List<com.drivemate.app.nav.LatLon>, Set<String>>>()
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val provider = object : com.drivemate.app.controller.RoutingProvider {
            override fun route(from: com.drivemate.app.nav.LatLon, to: com.drivemate.app.nav.LatLon, heading: Float?,
                               alternatives: Boolean, stops: List<com.drivemate.app.nav.LatLon>, avoid: Set<String>): com.drivemate.app.data.Router.Result {
                requests.set(Triple(alternatives, stops, avoid)); calls.incrementAndGet()
                return com.drivemate.app.data.Router.Result(emptyList())
            }
            override fun options(from: com.drivemate.app.nav.LatLon, to: com.drivemate.app.nav.LatLon, avoid: Set<String>, heading: Float?) =
                throw AssertionError("Active changes must preserve the trip stops")
        }
        providerField.set(container.routes, provider)
        try {
            session.updateSettings(session.settings.copy(avoidTolls = true))
            state(session, "location", android.location.Location("synthetic").apply { latitude = 53.48; longitude = -2.24 })
            val destination = com.drivemate.app.data.Place("Synthetic destination", "", com.drivemate.app.nav.LatLon(53.50, -2.20))
            val stop = destination.copy(name = "Synthetic stop", pos = com.drivemate.app.nav.LatLon(53.49, -2.23))
            state(session, "selectedPlace", destination)
            state(session, "stops", listOf(stop))
            mode(session, Mode.NAVIGATING)
            val changes = listOf(
                session.settings.copy(preferSimple = true), session.settings.copy(preferSimple = false),
                session.settings.copy(avoidRatRuns = true), session.settings.copy(avoidRatRuns = false))
            for ((index, settings) in changes.withIndex()) {
                session.updateSettings(settings)
                val deadline = System.nanoTime() + 10_000_000_000L
                while ((calls.get() < index + 1 || session.rerouting) && System.nanoTime() < deadline) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    Thread.sleep(10)
                }
                org.junit.Assert.assertEquals("Every live change must request a route", index + 1, calls.get())
                org.junit.Assert.assertEquals(settings.preferSimple || settings.avoidRatRuns, requests.get().first)
                org.junit.Assert.assertEquals(listOf(stop.pos), requests.get().second)
                org.junit.Assert.assertEquals(setOf("toll"), requests.get().third)
                org.junit.Assert.assertEquals(destination, session.selectedPlace)
                org.junit.Assert.assertEquals(listOf(stop), session.stops)
            }
        } finally { providerField.set(container.routes, original) }
    }

    private fun progress(session: NavigationSession, distanceToNext: Double) {
        NavigationSession::class.java.getDeclaredField("progress").apply { isAccessible = true }.set(session,
            com.drivemate.app.nav.Progress(0.0, 0.0, 0, distanceToNext, false, null, 0,
                5_000.0, 500.0, com.drivemate.app.nav.LatLon(53.48, -2.24), 0, null))
    }

    @Test fun batterySaverChangesMapFrameRateImmediatelyWithoutAnotherGpsFix() = withSession { session ->
        mode(session, Mode.NAVIGATING)
        progress(session, 1_000.0)
        val normal = com.drivemate.app.nav.SceneFrameRate.mapFps(session.easeOff, false, visibleOverview = true)
        session.updateSettings(session.settings.copy(batterySaver = true))
        org.junit.Assert.assertEquals(1, session.easeOff)
        org.junit.Assert.assertTrue(com.drivemate.app.nav.SceneFrameRate.mapFps(session.easeOff, false, visibleOverview = true) < normal)
        session.updateSettings(session.settings.copy(batterySaver = false))
        org.junit.Assert.assertEquals(0, session.easeOff)
        org.junit.Assert.assertEquals(normal, com.drivemate.app.nav.SceneFrameRate.mapFps(session.easeOff, false, visibleOverview = true))
        session.updateSettings(session.settings.copy(batterySaver = true))
        NavigationSession::class.java.getDeclaredMethod("applyCommittedSettings", com.drivemate.app.data.Settings::class.java)
            .apply { isAccessible = true }.invoke(session, session.settings.copy(batterySaver = false))
        org.junit.Assert.assertEquals("Committed restore/rollback must also recompute the screen", 0, session.easeOff)
    }

    @Test fun batterySaverPreservesNearTurnDetailAndThermalProtection() = withSession { session ->
        mode(session, Mode.NAVIGATING)
        progress(session, 100.0)
        session.updateSettings(session.settings.copy(batterySaver = true))
        org.junit.Assert.assertEquals("Near a turn the full detail must remain", 0, session.easeOff)
        progress(session, 1_000.0)
        NavigationSession::class.java.getDeclaredMethod("onThermal", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(session, 2)
        session.updateSettings(session.settings.copy(batterySaver = false))
        org.junit.Assert.assertEquals(2, session.easeOff)
        session.updateSettings(session.settings.copy(batterySaver = true))
        org.junit.Assert.assertEquals(2, session.easeOff)
    }

}
