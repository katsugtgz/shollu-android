package com.ebsoft.shollu.ui.screens.qibla

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.engine.QiblaCalculator
import kotlin.math.abs

/** Current display rotation (Surface.ROTATION_*), refreshed on configuration change. */
@Composable
private fun rememberDisplayRotation(context: Context): Int {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        @Suppress("DEPRECATION")
        (context as? Activity)?.windowManager?.defaultDisplay?.rotation
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation
            } else {
                null
            } ?: Surface.ROTATION_0
    }
}

@Composable
fun QiblaCompassScreen(
    selectedCity: City,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    var sensorAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var sensorAvailable by remember { mutableStateOf(true) }
    val displayRotation = rememberDisplayRotation(context)

    val qiblaBearing = remember(selectedCity) {
        QiblaCalculator.calculateBearing(selectedCity.latitude, selectedCity.longitude).toFloat()
    }
    val distanceKm = remember(selectedCity) {
        QiblaCalculator.calculateDistanceKm(selectedCity.latitude, selectedCity.longitude)
    }

    // Magnetic declination for the selected city so the magnetic compass azimuth can be
    // compared against the true-north Qibla bearing.
    val nowMillis = remember { System.currentTimeMillis() }
    val declination = remember(selectedCity, nowMillis) {
        QiblaCalculator.magneticDeclinationDegrees(selectedCity.latitude, selectedCity.longitude, nowMillis).toFloat()
    }

    // Compass Sensor Listener (re-registered when the display rotation changes)
    DisposableEffect(displayRotation) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)

        val hasRotationVector = rotationVectorSensor != null
        val hasAccelMag = accelerometerSensor != null && magneticSensor != null

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val remappedMatrix = FloatArray(9)
            private val orientationValues = FloatArray(3)
            private val gravityValues = FloatArray(3)
            private val geomagneticValues = FloatArray(3)
            private var hasGravity = false
            private var hasGeomagnetic = false

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        updateAzimuthFromRotationMatrix()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravityValues, 0, 3)
                        hasGravity = true
                        if (hasGeomagnetic) {
                            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geomagneticValues)) {
                                updateAzimuthFromRotationMatrix()
                            }
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                        System.arraycopy(event.values, 0, geomagneticValues, 0, 3)
                        hasGeomagnetic = true
                        if (hasGravity) {
                            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geomagneticValues)) {
                                updateAzimuthFromRotationMatrix()
                            }
                        }
                    }
                    @Suppress("DEPRECATION")
                    Sensor.TYPE_ORIENTATION -> {
                        val degree = (event.values[0] + 360f) % 360f
                        val delta = ((degree - azimuth + 540f) % 360f) - 180f
                        azimuth = (azimuth + 0.15f * delta + 360f) % 360f
                    }
                }
            }

            private fun updateAzimuthFromRotationMatrix() {
                // Compensate for the current display rotation so the azimuth follows the
                // direction the user faces (fixes the 90-degree offset in landscape).
                val (axisX, axisY) = QiblaCalculator.remapAxesForDisplayRotation(displayRotation)
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
                SensorManager.getOrientation(remappedMatrix, orientationValues)
                var degree = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                degree = (degree + 360f) % 360f
                // Shortest angular distance low-pass smoothing avoiding 360° jump
                val delta = ((degree - azimuth + 540f) % 360f) - 180f
                azimuth = (azimuth + 0.15f * delta + 360f) % 360f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                sensorAccuracy = accuracy
            }
        }

        var registered = false
        if (hasRotationVector) {
            registered = sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (hasAccelMag) {
            val accelRegistered = sensorManager.registerListener(listener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            val magneticRegistered = sensorManager.registerListener(listener, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            registered = accelRegistered || magneticRegistered
        } else {
            @Suppress("DEPRECATION")
            val orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
            if (orientationSensor != null) {
                registered = sensorManager.registerListener(listener, orientationSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        sensorAvailable = registered

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Compare the true-north azimuth (magnetic azimuth + declination) with the true-north
    // Qibla bearing; confirmation is suppressed while no sensor is feeding azimuth updates.
    val trueAzimuth = QiblaCalculator.qiblaTrueBearingFromMagnetic(azimuth.toDouble(), declination.toDouble()).toFloat()
    val diff = (trueAzimuth - qiblaBearing + 360f) % 360f
    val isAligned = sensorAvailable && (diff < 3f || diff > 357f)

    val animatedAzimuth by animateFloatAsState(
        targetValue = -azimuth,
        animationSpec = spring(stiffness = 300f),
        label = "compassAzimuth"
    )

    // Theme roles resolved in composable scope so the Canvas draw lambdas stay token-driven
    // (issue #17) — the bearing/compass math above is untouched.
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Info
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Penunjuk Arah Kiblat",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${selectedCity.name} • ${String.format("%.0f", distanceKm)} km ke Ka'bah",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Center Compass Canvas
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
            // Rotating Compass Dial
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(animatedAzimuth)
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 12.dp.toPx()

                // Outer Dial
                drawCircle(
                    color = primaryColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )

                // Cardinal Markers (N, E, S, W)
                drawCircle(
                    color = tertiaryColor,
                    radius = radius - 16.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Qibla Target Needle pointing to Kaaba
            val qiblaNeedleAngle = (qiblaBearing - trueAzimuth)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(qiblaNeedleAngle)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val needleLength = size.minDimension / 2 - 24.dp.toPx()

                    // Top Kaaba Needle (Gold / Green)
                    val pathNorth = Path().apply {
                        moveTo(center.x, center.y - needleLength)
                        lineTo(center.x + 16.dp.toPx(), center.y)
                        lineTo(center.x, center.y - 12.dp.toPx())
                        lineTo(center.x - 16.dp.toPx(), center.y)
                        close()
                    }
                    drawPath(pathNorth, tertiaryColor)

                    // South Needle
                    val pathSouth = Path().apply {
                        moveTo(center.x, center.y + needleLength * 0.7f)
                        lineTo(center.x + 12.dp.toPx(), center.y)
                        lineTo(center.x, center.y + 12.dp.toPx())
                        lineTo(center.x - 12.dp.toPx(), center.y)
                        close()
                    }
                    drawPath(pathSouth, onSurfaceVariantColor)

                    // Center Pivot
                    drawCircle(color = primaryColor, radius = 8.dp.toPx(), center = center)
                    drawCircle(color = tertiaryColor, radius = 4.dp.toPx(), center = center)
                }
            }

            if (isAligned) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(tertiaryColor.copy(alpha = 0.2f), CircleShape)
                )
            }
        }

        // Bottom Alignment Status & Calibration
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!sensorAvailable) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kompas tidak tersedia pada perangkat ini. Arah kiblat: ${String.format("%.1f", qiblaBearing)}° dari utara sejati.",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (isAligned) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = tertiaryColor.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = tertiaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tepat Menghadap Ka'bah! (${String.format("%.1f", qiblaBearing)}°)",
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                Text(
                    text = "Arah Ka'bah: ${String.format("%.1f", qiblaBearing)}° • Kompas (utara sejati): ${String.format("%.1f", trueAzimuth)}°",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Letakkan HP pada permukaan datar. Jauhkan dari benda bermagnet untuk akurasi terbaik.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
