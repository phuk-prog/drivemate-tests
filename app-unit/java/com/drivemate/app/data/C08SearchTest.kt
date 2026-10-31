package com.drivemate.app.data

import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

/**
 * C08: UK postcode/address/place search on the phone (owner report 31 Oct: the Trentham Estate
 * address with ST4 8JG found nothing from Stockport). Uses the real merge/ranking and offline-list
 * reader with a small list in the published format.
 */
class C08SearchTest {
    private val stockport = LatLon(53.4083, -2.1494)
    private val stoke = LatLon(52.9645, -2.1978)

    private val list = """
        #drivemate-search-offline	1	2026-10-31	Contains OS data
        menai grove	R	Menai Grove	Cheadle, SK8	53.3940	-2.2100
        sk43ab	P		Stockport	53.4158	-2.2123
        st48jg	P		Stoke-on-Trent	52.9645	-2.1978
        stone road	R	Stone Road	Trentham, ST4	52.9600	-2.1950
        trentham	S	Trentham	City of Stoke-on-Trent, ST4	52.9659	-2.1869
        trentham	O	Trentham		57.91478	-4.04764
    """.trimIndent()

    private fun offline(q: String, near: LatLon?) =
        OfflineSearch.searchStream(BufferedReader(StringReader(list)), OfflineSearch.queryKey(q), near)

    private fun local(places: List<Place>) = listOf(SourceOutcome("local", false, Result.success(places)))

    @Test fun reportedFullAddressFindsTheStokePostcodeFromStockport() {
        val q = "Trentham Estate, Stone Road, Trentham, Stoke-on-Trent ST4 8JG"
        val postcodeOnly = Geocoder.fullPostcode.find(q)!!.value
        val found = offline(postcodeOnly, stockport)
        assertTrue("ST4 8JG in the list", found.any { it.keywords == "postcode" })
        val merged = Geocoder.mergeSearch(q, local(found), stockport)
        assertTrue("a result is shown, not 'No places found'", merged.isNotEmpty())
        assertTrue(merged.any { it.keywords == "postcode" && Geocoder.compactCode(it.name) == "st48jg" })
        assertEquals(stoke.lat, merged.first { it.keywords == "postcode" }.pos.lat, 1e-4)
    }

    @Test fun postcodeIsFoundWithOrWithoutTheSpaceAndInAnyCase() {
        for (q in listOf("ST4 8JG", "st48jg", "St4 8jG")) assertTrue(q, offline(q, stockport).any { it.keywords == "postcode" })
    }

    @Test fun aNearbyPlaceRanksAboveTheSameNameFarAway() {
        val ranked = Geocoder.rank("Trentham", offline("trentham", stoke), stoke)
        assertEquals("Stoke's Trentham before the Highland one", 52.9659, ranked.first().pos.lat, 1e-4)
    }

    @Test fun houseNumberFallsBackToTheStreetNotAnInventedHouse() {
        val found = OfflineSearch.withFallbacks(OfflineSearch.key("12 Menai Grove")) { k ->
            OfflineSearch.searchStream(BufferedReader(StringReader(list)), k, stockport)
        }
        assertTrue(found.any { it.name == "Menai Grove" })
    }

    @Test fun anApproximateResultIsNeverAutoSelectedAsTheDestination() {
        val street = Place("Stone Road", "Trentham, ST4", LatLon(52.96, -2.195))
        assertFalse("a street is not the requested postcode",
            Geocoder.canAutoSelectOnlyResult("Trentham Estate ST4 8JG", street, stoke))
        val postcode = Place("ST4 8JG", "Stoke-on-Trent", stoke, keywords = "postcode")
        assertTrue(Geocoder.canAutoSelectOnlyResult("ST4 8JG", postcode, stoke))
        assertFalse("SK8 2EZ is not house 2 of anything", Geocoder.canAutoSelectOnlyResult("SK8 2EZ", postcode, stoke))
    }

    @Test fun nothingIsFabricatedForAPostcodeNotInTheList() {
        assertTrue(offline("ZZ9 9ZZ", stockport).none { it.keywords == "postcode" })
        assertTrue(Geocoder.mergeSearch("ZZ9 9ZZ", local(emptyList()), stockport).isEmpty())
    }
}
