package com.drivemate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `google.navigation:` links as other apps send them (the part after the scheme, decoded). */
class NavigationLinkTest {
    @Test fun coordinates() = assertEquals("53.453500,-2.244780", NavigationLink.destination("q=53.453500,-2.244780"))
    @Test fun withTravelMode() = assertEquals("53.4,-2.2", NavigationLink.destination("q=53.4,-2.2&mode=d"))
    @Test fun modeFirst() = assertEquals("Stockport", NavigationLink.destination("mode=d&q=Stockport"))
    @Test fun anAddress() = assertEquals("10 Downing Street, London", NavigationLink.destination("q=10 Downing Street, London"))
    @Test fun noDestination() {
        assertNull(NavigationLink.destination("mode=d"))
        assertNull(NavigationLink.destination("q="))
        assertNull(NavigationLink.destination(null))
    }
}
