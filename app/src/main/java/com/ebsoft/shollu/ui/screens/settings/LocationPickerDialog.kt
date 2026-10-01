package com.ebsoft.shollu.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.data.repository.CityRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pure city-query matcher behind the location picker's search bar (issue #19).
 *
 * Semantics (JVM-tested in LocationPickerFilterTest, expected values hand-worked from the
 * seed list in res/raw/cities.json):
 *  - blank query returns every city, input order preserved (Indonesia first, name asc);
 *  - matching is case-insensitive (Locale.ROOT) after trimming, against name OR province,
 *    so parenthetical aliases resolve ("solo" -> "Surakarta (Solo)", "mecca" -> "Makkah (Mecca)");
 *  - every whitespace-separated term must match somewhere (AND), e.g. "tangerang selatan"
 *    resolves to exactly "Tangerang Selatan", "jawa barat" to the Jawa Barat cities;
 *  - no match yields an empty list; relative input order is never reordered.
 */
internal fun filterCities(cities: List<City>, query: String): List<City> {
    val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return cities
    return cities.filter { city ->
        val name = city.name.lowercase(Locale.ROOT)
        val province = city.province.lowercase(Locale.ROOT)
        terms.all { term -> term in name || term in province }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerDialog(
    cityRepository: CityRepository,
    onCitySelected: (City) -> Unit,
    onAutoGpsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val catalogFlow = remember(cityRepository) {
        cityRepository.allCities.map<List<City>, List<City>?> { it }
    }
    val catalog by catalogFlow.collectAsState(initial = null)
    // Empty emission = boot-time seeding still in flight, NOT a loaded empty catalog.
    val catalogLoaded = isCatalogLoaded(catalog)
    val allCities = catalog.orEmpty()
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    // Set on the GPS tap; the fix always terminates by closing this dialog (success —
    // the parent dismisses after the write; failure — the parent closes with a toast).
    // While pending, city taps are ignored so a tap landing in the window before the
    // close cannot be clobbered by the incoming GPS write.
    var gpsFixPending by remember { mutableStateOf(false) }

    val query = textFieldState.text.toString()
    val cities = remember(allCities, query) { filterCities(allCities, query) }
    val searchMessage = citySearchMessage(query, cities.size, catalogLoaded)

    val selectCity: (City) -> Unit = { city ->
        if (cityTapCommitsImmediately(gpsFixPending)) {
            onCitySelected(city)
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pilih Kota / Lokasi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // GPS Auto Detect Button
                Button(
                    onClick = {
                        gpsFixPending = true
                        onAutoGpsClick()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deteksi Otomatis via GPS", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                val inputField: @Composable () -> Unit = {
                    SearchBarDefaults.InputField(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                        placeholder = { Text("Cari kota") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Collapsed bar (Material 3 SearchBarState API — not the deprecated
                // expanded/onExpandedChange overload) + results popup when expanded.
                SearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                ExpandedDockedSearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    CityList(
                        cities = cities,
                        onCityClick = selectCity,
                        statusMessage = searchMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    )
                }

                // Browse list while the bar is collapsed.
                if (searchBarState.currentValue == SearchBarValue.Collapsed) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CityList(
                        cities = cities,
                        onCityClick = selectCity,
                        statusMessage = searchMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CityList(
    cities: List<City>,
    onCityClick: (City) -> Unit,
    statusMessage: String?,
    modifier: Modifier = Modifier
) {
    if (statusMessage != null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(cities, key = { it.id }) { city ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCityClick(city) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationCity,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = city.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${city.province} • ${city.country} (${city.latitude}, ${city.longitude})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
