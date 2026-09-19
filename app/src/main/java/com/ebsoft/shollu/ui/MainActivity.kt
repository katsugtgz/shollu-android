package com.ebsoft.shollu.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ebsoft.shollu.SholluApplication
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.engine.GpsOffset
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.receiver.ReminderAlarmScheduler
import com.ebsoft.shollu.ui.navigation.Screen
import com.ebsoft.shollu.ui.screens.calendar.CalendarScreen
import com.ebsoft.shollu.ui.screens.home.HomeScreen
import com.ebsoft.shollu.ui.screens.home.ScheduleEntry
import com.ebsoft.shollu.ui.screens.qibla.QiblaCompassScreen
import com.ebsoft.shollu.ui.screens.scheduler.SchedulerScreen
import com.ebsoft.shollu.ui.screens.settings.LocationPickerDialog
import com.ebsoft.shollu.ui.screens.settings.SettingsScreen
import com.ebsoft.shollu.ui.theme.SholluTheme
import com.ebsoft.shollu.data.update.InstallResult
import com.ebsoft.shollu.data.update.UpdateCheck
import com.ebsoft.shollu.data.update.UpdateOffer
import com.ebsoft.shollu.ui.update.UpdatePromptDialog
import android.location.LocationManager
import com.google.android.gms.location.Priority
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.Locale

class MainActivity : ComponentActivity() {

    private var fusedLocationClient: FusedLocationProviderClient? = null

    private var pendingUnknownSourcesOffer: UpdateOffer? = null
    private var updateOffer by mutableStateOf<UpdateOffer?>(null)
    private var updateProgress by mutableStateOf<Float?>(null)
    private var updateError by mutableStateOf<String?>(null)
    private var showExactAlarmPrompt by mutableStateOf(false)
    private var awaitingExactAlarmGrant = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted || coarseLocationGranted) {
            autoDetectLocation()
        }
    }

    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val offer = pendingUnknownSourcesOffer ?: return@registerForActivityResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.canRequestPackageInstalls()
        ) {
            startUpdateInstall(offer)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestAppPermissions()
        if (needsExactAlarmPromptNow()) {
            showExactAlarmPrompt = true
            awaitingExactAlarmGrant = true
        }

        val app = application as SholluApplication
        val preferences = app.preferences
        val prayerRepo = app.prayerRepository
        val cityRepo = app.cityRepository
        val reminderRepo = app.reminderRepository

        setContent {
            val themeMode by preferences.themeMode.collectAsState(initial = com.ebsoft.shollu.data.model.ThemeMode.EMERALD)
            val appLanguage by preferences.appLanguage.collectAsState(initial = com.ebsoft.shollu.data.model.AppLanguage.INDONESIAN)
            val selectedCity by preferences.selectedCity.collectAsState(initial = City(name = "Jakarta (DKI Jakarta)", province = "DKI Jakarta", country = "Indonesia", latitude = -6.2088, longitude = 106.8456, elevation = 8.0, timezone = 7.0))
            val calculationMethod by preferences.calculationMethod.collectAsState(initial = com.ebsoft.shollu.data.model.CalculationMethod.KEMENAG_RI)
            val asrJuristic by preferences.asrJuristic.collectAsState(initial = com.ebsoft.shollu.data.model.AsrJuristic.STANDARD)
            val ihtiyatMinutes by preferences.ihtiyatMinutes.collectAsState(initial = 2)
            val hijriAdjustment by preferences.hijriAdjustment.collectAsState(initial = 0)
            val customOffsets by preferences.customOffsets.collectAsState(initial = emptyMap())

            val navController = rememberNavController()
            // Activity-scoped cache of Home's city-frame schedule (today+tomorrow), stored with
            // the input snapshot it was computed for. Navigation disposes the Home composable,
            // so the cache lets it re-seed without a loading flash — and Home's key gate hides
            // the cached pair whenever the recorded inputs no longer match (city/settings/date
            // changed since), so stale times are never displayed.
            var homeScheduleCache by remember {
                mutableStateOf<ScheduleEntry?>(null)
            }
            var showLocationPicker by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val check = withContext(Dispatchers.IO) {
                    app.updates.updater.check()
                }
                if (check is UpdateCheck.Available) {
                    updateOffer = check.offer
                }
            }

            val items = listOf(
                Screen.Home,
                Screen.Calendar,
                Screen.Scheduler,
                Screen.Qibla,
                Screen.Settings
            )

            SholluTheme(themeMode = themeMode) {
                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            items.forEach { screen ->
                                val isSelected = currentRoute == screen.route
                                NavigationBarItem(
                                    // Decorative icon: the visible label() supplies the
                                    // accessible destination name — a contentDescription
                                    // here would make TalkBack announce the tab twice.
                                    // alwaysShowLabel must stay true: on narrow AVDs M3 can
                                    // drop unselected labels from the a11y tree (CI release
                                    // gate looks up text="Pengaturan"; only Beranda remained).
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = {
                                        Text(
                                            screen.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    selected = isSelected,
                                    alwaysShowLabel = true,
                                    onClick = {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                popUpTo(Screen.Home.route) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                prayerRepository = prayerRepo,
                                selectedCity = selectedCity,
                                calculationMethod = calculationMethod,
                                asrJuristic = asrJuristic,
                                ihtiyatMinutes = ihtiyatMinutes,
                                customOffsets = customOffsets,
                                hijriAdjustment = hijriAdjustment,
                                cachedSchedule = homeScheduleCache,
                                onScheduleComputed = { homeScheduleCache = it },
                                onNavigateToQibla = { navController.navigate(Screen.Qibla.route) },
                                onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                onNavigateToLocationPicker = { showLocationPicker = true }
                            )
                        }

                        composable(Screen.Calendar.route) {
                            CalendarScreen(
                                prayerRepository = prayerRepo,
                                selectedCity = selectedCity,
                                calculationMethod = calculationMethod,
                                asrJuristic = asrJuristic,
                                ihtiyatMinutes = ihtiyatMinutes,
                                customOffsets = customOffsets,
                                hijriAdjustment = hijriAdjustment
                            )
                        }

                        composable(Screen.Scheduler.route) {
                            SchedulerScreen(reminderRepository = reminderRepo, selectedCity = selectedCity)
                        }

                        composable(Screen.Qibla.route) {
                            QiblaCompassScreen(selectedCity = selectedCity)
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                preferences = preferences,
                                cityRepository = cityRepo,
                                selectedCity = selectedCity,
                                calculationMethod = calculationMethod,
                                asrJuristic = asrJuristic,
                                ihtiyatMinutes = ihtiyatMinutes,
                                hijriAdjustment = hijriAdjustment,
                                themeMode = themeMode,
                                appLanguage = appLanguage,
                                onOpenLocationPicker = { showLocationPicker = true }
                            )
                        }
                    }

                    if (showLocationPicker) {
                        LocationPickerDialog(
                            cityRepository = cityRepo,
                            onCitySelected = { city ->
                                // App-owned IO scope (mirrors processLocation): dialog
                                // dismissal is not blocked and navigating away cannot
                                // cancel the pipeline mid-way.
                                val app = application as SholluApplication
                                app.applicationScope.launch(Dispatchers.IO) {
                                    // Fixed-list city: isGps defaults false, clearing any GPS
                                    // flag from a previous detection (its timezone is canonical).
                                    preferences.updateCity(city)
                                    AlarmScheduler.scheduleNextPrayerAlarms(this@MainActivity)
                                    // Reminders are armed in the city's offset — re-arm them in
                                    // the new frame or they fire at the OLD city's instant.
                                    ReminderAlarmScheduler.scheduleAllActiveReminders(this@MainActivity)
                                    com.ebsoft.shollu.widget.updateSholluWidgets(this@MainActivity)
                                    withContext(Dispatchers.Main) {
                                        showLocationPicker = false
                                    }
                                }
                            },
                            onAutoGpsClick = {
                                autoDetectLocation()
                            },
                            onDismiss = { showLocationPicker = false }
                        )
                    }

                    val offer = updateOffer
                    if (offer != null) {
                        UpdatePromptDialog(
                            offer = offer,
                            progress = updateProgress,
                            error = updateError,
                            onDismiss = {
                                pendingUnknownSourcesOffer = null
                                updateOffer = null
                                updateProgress = null
                                updateError = null
                            },
                            onLater = {
                                pendingUnknownSourcesOffer = null
                                updateOffer = null
                                updateProgress = null
                                updateError = null
                                app.applicationScope.launch(Dispatchers.IO) {
                                    app.updates.updater.snooze(offer.tagName)
                                }
                            },
                            onUpdate = { startUpdateInstall(offer) }
                        )
                    }

                    if (showExactAlarmPrompt) {
                        AlertDialog(
                            onDismissRequest = { showExactAlarmPrompt = false },
                            title = { Text("Izin alarm tepat", fontWeight = FontWeight.Bold) },
                            text = {
                                Text("Shollu memerlukan izin alarm tepat agar pengingat sholat berbunyi pada waktunya.")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showExactAlarmPrompt = false
                                    requestExactAlarmPermission()
                                }) { Text("Izinkan") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExactAlarmPrompt = false }) {
                                    Text("Nanti")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val stillDenied = needsExactAlarmPromptNow()
        if (stillDenied) awaitingExactAlarmGrant = true
        if (AlarmScheduler.shouldReshowExactAlarmPrompt(stillDenied, awaitingExactAlarmGrant)) {
            showExactAlarmPrompt = true
            return
        }
        if (awaitingExactAlarmGrant && !stillDenied) {
            awaitingExactAlarmGrant = false
            val app = application as SholluApplication
            app.applicationScope.launch(Dispatchers.IO) {
                AlarmScheduler.scheduleNextPrayerAlarms(this@MainActivity)
                ReminderAlarmScheduler.scheduleAllActiveReminders(this@MainActivity)
            }
        }
    }

    private fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:$packageName")
        }

    private fun startUpdateInstall(offer: UpdateOffer) {
        val app = application as SholluApplication
        val installer = app.updates.installer
        app.applicationScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                updateError = null
                updateProgress = 0f
            }
            val result = installer.install(offer) { p ->
                app.applicationScope.launch(Dispatchers.Main) {
                    updateProgress = p
                }
            }
            withContext(Dispatchers.Main) {
                when (result) {
                    is InstallResult.Started -> {
                        pendingUnknownSourcesOffer = null
                        updateOffer = null
                        updateProgress = null
                        updateError = null
                    }
                    is InstallResult.NeedsUnknownSources -> {
                        updateProgress = null
                        updateError = "Izinkan instalasi dari sumber ini."
                        pendingUnknownSourcesOffer = offer
                        unknownSourcesLauncher.launch(unknownSourcesSettingsIntent())
                    }
                    is InstallResult.Failed -> {
                        updateProgress = null
                        updateError = result.message
                    }
                }
            }
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun needsExactAlarmPromptNow(): Boolean {
        val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        return AlarmScheduler.needsExactAlarmPrompt(Build.VERSION.SDK_INT, canSchedule)
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    private fun autoDetectLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Toast.makeText(this, "Izin lokasi diperlukan untuk deteksi otomatis", Toast.LENGTH_SHORT).show()
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient = client

        try {
            client.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        processLocation(location)
                    } else {
                        requestCurrentLocationFallback()
                    }
                }
                .addOnFailureListener {
                    requestCurrentLocationFallback()
                }
        } catch (e: SecurityException) {
            requestLocationManagerFallback()
        }
    }

    private fun requestCurrentLocationFallback() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return
        val client = fusedLocationClient ?: return

        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        processLocation(location)
                    } else {
                        requestLocationManagerFallback()
                    }
                }
                .addOnFailureListener {
                    requestLocationManagerFallback()
                }
        } catch (e: SecurityException) {
            requestLocationManagerFallback()
        }
    }

    private fun requestLocationManagerFallback() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                val providers = listOfNotNull(
                    LocationManager.GPS_PROVIDER.takeIf { fineGranted },
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                var bestLocation: Location? = null
                for (provider in providers) {
                    try {
                        if (locationManager.isProviderEnabled(provider)) {
                            val loc = locationManager.getLastKnownLocation(provider)
                            if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                                bestLocation = loc
                            }
                        }
                    } catch (e: Exception) {
                        // ignore provider error
                    }
                }
                if (bestLocation != null) {
                    processLocation(bestLocation)
                } else {
                    Toast.makeText(this, "Tidak dapat mendeteksi lokasi saat ini", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mendeteksi lokasi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processLocation(location: Location) {
        val app = application as SholluApplication
        app.applicationScope.launch(Dispatchers.IO) {
            var locality: String? = null
            var adminArea: String = ""
            var countryName: String = ""

            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val address = addresses?.firstOrNull()
                    if (address != null) {
                        locality = address.locality ?: address.subAdminArea ?: address.featureName
                        adminArea = address.adminArea ?: ""
                        countryName = address.countryName ?: ""
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val finalCityName = locality ?: "GPS (${String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)})"
            val finalCountry = if (countryName.isNotBlank()) countryName else "Koordinat GPS"

            val fallbackHours = GpsOffset.deviceFallbackHours()
            val tz = GpsOffset.offsetHours(
                location.latitude,
                location.longitude,
                fallbackHours,
                finalCountry
            )

            val gpsCity = City(
                name = finalCityName,
                province = adminArea,
                country = finalCountry,
                latitude = location.latitude,
                longitude = location.longitude,
                elevation = location.altitude,
                timezone = tz
            )

            // isGps = true: offset from coordinates via GpsOffset; re-derive on TIMEZONE_CHANGED.
            app.preferences.updateCity(gpsCity, isGps = true)
            AlarmScheduler.scheduleNextPrayerAlarms(this@MainActivity)
            // Reminders are armed in the city's offset — re-arm them in the new frame.
            ReminderAlarmScheduler.scheduleAllActiveReminders(this@MainActivity)
            com.ebsoft.shollu.widget.updateSholluWidgets(this@MainActivity)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Lokasi terdeteksi: $finalCityName", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
