package com.ebsoft.shollu.ui.screens.settings

import com.ebsoft.shollu.data.model.City
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * City-query matching extracted from LocationPickerDialog (issue #19).
 *
 * Expected results are worked by hand from real seed literals
 * (app/src/main/res/raw/cities.json) — never recomputed via the implementation.
 */
class LocationPickerFilterTest {

    private fun city(id: Long, name: String, province: String, country: String = "Indonesia") =
        City(id = id, name = name, province = province, country = country, latitude = 0.0, longitude = 0.0)

    private val seed = listOf(
        city(1, "Jakarta (DKI Jakarta)", "DKI Jakarta"),
        city(2, "Surabaya", "Jawa Timur"),
        city(3, "Bandung", "Jawa Barat"),
        city(6, "Makassar", "Sulawesi Selatan"),
        city(7, "Palembang", "Sumatera Selatan"),
        city(9, "Surakarta (Solo)", "Jawa Tengah"),
        city(14, "Banjarmasin", "Kalimantan Selatan"),
        city(18, "Padang", "Sumatera Barat"),
        city(23, "Serang", "Banten"),
        city(24, "Tangerang", "Banten"),
        city(25, "Tangerang Selatan", "Banten"),
        city(26, "Bekasi", "Jawa Barat"),
        city(27, "Depok", "Jawa Barat"),
        city(28, "Bogor", "Jawa Barat"),
        city(30, "Cirebon", "Jawa Barat"),
        city(31, "Sukabumi", "Jawa Barat"),
        city(32, "Tasikmalaya", "Jawa Barat"),
        city(40, "Mataram (Lombok)", "Nusa Tenggara Barat"),
        city(56, "Makkah (Mecca)", "Makkah", country = "Saudi Arabia"),
        city(57, "Madinah (Medina)", "Madinah", country = "Saudi Arabia"),
        city(63, "Tokyo", "Tokyo", country = "Japan")
    )

    private fun names(cities: List<City>) = cities.map { it.name }

    @Test
    fun testBlankQueryReturnsEveryCityInInputOrder() {
        assertEquals(seed, filterCities(seed, ""))
        assertEquals(seed, filterCities(seed, "   "))
    }

    @Test
    fun testNameMatchIsCaseInsensitiveAndTrimmed() {
        assertEquals(listOf("Bogor"), names(filterCities(seed, "bogor")))
        assertEquals(listOf("Bogor"), names(filterCities(seed, "  BOGOR  ")))
    }

    @Test
    fun testParentheticalAliasMatches() {
        assertEquals(listOf("Surakarta (Solo)"), names(filterCities(seed, "solo")))
        assertEquals(listOf("Makkah (Mecca)"), names(filterCities(seed, "mecca")))
        assertEquals(listOf("Mataram (Lombok)"), names(filterCities(seed, "lombok")))
    }

    @Test
    fun testProvinceMatchKeepsSeedOrder() {
        // Banten in seed order: Serang (23), Tangerang (24), Tangerang Selatan (25)
        assertEquals(
            listOf("Serang", "Tangerang", "Tangerang Selatan"),
            names(filterCities(seed, "banten"))
        )
    }

    @Test
    fun testEveryWhitespaceSeparatedTermMustMatch() {
        // "tangerang" matches both cities, "selatan" only the second
        assertEquals(listOf("Tangerang Selatan"), names(filterCities(seed, "tangerang selatan")))
        // "jawa" + "barat" together = only Jawa Barat cities, in seed order
        assertEquals(
            listOf("Bandung", "Bekasi", "Depok", "Bogor", "Cirebon", "Sukabumi", "Tasikmalaya"),
            names(filterCities(seed, "jawa barat"))
        )
    }

    @Test
    fun testNoMatchYieldsEmptyList() {
        assertEquals(emptyList<City>(), filterCities(seed, "zzz"))
    }

    @Test
    fun testMatchesAreStableAcrossRepeatedFiltering() {
        val once = filterCities(seed, "ta")
        val twice = filterCities(once, "ta")
        assertEquals(once, twice)
    }
}
