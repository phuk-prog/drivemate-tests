package com.drivemate.app

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric runs tests on the JVM, where MapLibre's native library cannot load.
 * These shadows make the native calls no-ops for startup tests only. They do not change app code.
 */
@Implements(className = "org.maplibre.android.net.NativeConnectivityListener")
class ShadowNativeConnectivityListener {
    @Implementation
    fun initialize() {
    }
}

@Implements(className = "org.maplibre.android.util.TileServerOptions")
class ShadowTileServerOptions {
    companion object {
        @Implementation
        @JvmStatic
        fun mapLibreConfiguration(): Any? = null
    }
}

@Implements(className = "org.maplibre.android.storage.FileSource")
class ShadowFileSource {
    @Implementation
    fun initialize(path: String?, cache: String?, options: Any?) {
    }
}
