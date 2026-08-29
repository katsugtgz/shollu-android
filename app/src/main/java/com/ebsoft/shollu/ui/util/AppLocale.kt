package com.ebsoft.shollu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.ebsoft.shollu.SholluApplication
import com.ebsoft.shollu.data.model.AppLanguage
import java.util.Locale

/** Maps the in-app language preference to the Locale used for date formatting. */
fun localeFor(language: AppLanguage): Locale = when (language) {
    AppLanguage.INDONESIAN -> Locale("in", "ID")
    AppLanguage.ENGLISH -> Locale("en", "")
    AppLanguage.ARABIC -> Locale("ar", "")
}

/**
 * Collects the appLanguage preference from the application scope so screens that do not receive
 * it as a parameter still format dates in the selected language.
 */
@Composable
fun rememberAppLanguage(): AppLanguage {
    val context = LocalContext.current
    val flow = (context.applicationContext as? SholluApplication)?.preferences?.appLanguage
    return if (flow != null) {
        flow.collectAsState(initial = AppLanguage.INDONESIAN).value
    } else {
        AppLanguage.INDONESIAN
    }
}

@Composable
fun rememberAppLocale(): Locale = localeFor(rememberAppLanguage())
