package com.drivemate.tests.cat07

import android.app.Application
import android.location.Location
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.*
import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real session with synthetic fixes. Reflection remains test-only, with no shipped test hooks. */
@RunWith(AndroidJUnit4::class)
class RoadAlertsSessionScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun call(s: NavigationSession, name: String, vararg args: Any?) =
        NavigationSession::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(s,*args)
    @Suppress("UNCHECKED_CAST")
    private fun <T> state(s: NavigationSession, name: String, value: T) {
        val field = NavigationSession::class.java.getDeclaredField(name + "\$delegate").apply { isAccessible = true }
        (field.get(s) as MutableState<T>).value = value
    }
    private fun fix(p: LatLon, elapsedMs: Long) = Location("gps").apply {
        latitude=p.lat; longitude=p.lon; accuracy=3f; speed=10f; bearing=0f
        elapsedRealtimeNanos=elapsedMs*1_000_000; time=1_700_000_000_000
    }
    @Test fun q7_3_5_sessionRejectsParallelCameraAndResetsAfterGapOrUncertainFix() {
        lateinit var s: NavigationSession
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        compose.runOnIdle { s=NavigationSession(app) }
        compose.waitUntil(30000) { s.settingsReady }
        try {
            compose.runOnIdle {
                val p=LatLon(53.0,-2.0)
                val end=Geo.offset(p,0.0,1000.0)
                val road=LocalRoadMatcher.Match("synthetic", "Synthetic road",p,0.0,listOf(p to end),1.0)
                NavigationSession::class.java.getDeclaredField("matchedRoad").apply { isAccessible=true }.set(s,road)
                state(s,"positionReliable",true)
                val parallel=Hazard(7001,HazardType.AVERAGE_SPEED,Geo.offset(p,90.0,25.0),0,fixed=true,limitKmh=50.0)
                state(s,"roadAlerts",listOf(parallel))
                call(s,"updateAverageSpeed",fix(p,1000))
                assertNull("Nearby parallel road camera cannot start a measurement",s.avgSpeed)
                val own=parallel.copy(id=7002,pos=p)
                state(s,"roadAlerts",listOf(own))
                call(s,"updateAverageSpeed",fix(p,2000))
                assertNotNull(s.avgSpeed)
                assertNull(s.avgSpeed!!.averageKmh)
                call(s,"updateAverageSpeed",fix(Geo.offset(p,0.0,100.0),12000))
                assertEquals(36.0,s.avgSpeed!!.averageKmh!!,0.1)
                call(s,"updateAverageSpeed",fix(Geo.offset(p,0.0,110.0),23001))
                assertNull("Gap invalidates the displayed average",s.avgSpeed)
                call(s,"resetAverageSpeed")
                call(s,"updateAverageSpeed",fix(p,25000))
                assertNotNull(s.avgSpeed)
                state(s,"positionReliable",false)
                call(s,"updateAverageSpeed",fix(p,26000))
                assertNull("An uncertain fix cannot retain a measured average",s.avgSpeed)
            }
        } finally { compose.runOnIdle { s.close() } }
    }
}
