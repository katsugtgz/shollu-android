package com.ebsoft.shollu.ui.util

import com.ebsoft.shollu.data.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLocaleTest {

    @Test
    fun indonesianMapsToInID() {
        assertEquals(Locale("in", "ID"), localeFor(AppLanguage.INDONESIAN))
    }

    @Test
    fun englishMapsToEn() {
        assertEquals(Locale("en", ""), localeFor(AppLanguage.ENGLISH))
    }

    @Test
    fun arabicMapsToAr() {
        assertEquals(Locale("ar", ""), localeFor(AppLanguage.ARABIC))
    }
}
