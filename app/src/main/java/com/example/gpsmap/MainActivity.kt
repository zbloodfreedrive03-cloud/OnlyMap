package com.example.gpsmap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.*

private val Ink = Color(0xFF080B10)
private val Panel = Color(0xE9161D27)
private val Cyan = Color(0xFF35D7F2)
private val Muted = Color(0xFF8B98A9)

class MainActivity : ComponentActivity() {
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var latestLocation: Location? = null
    private var destination: LatLng? = null
    private var origin: LatLng? = null
    private var userColor = "#35D7F2"
    private var routeProgress by mutableFloatStateOf(0f)
    private var routeReady by mutableStateOf(false)
    private var routeDistanceKm by mutableStateOf(0.0)
    private var followCamera = true
    private val handler = Handler(Looper.getMainLooper())
    private var restoreTask: Runnable? = null
    private var savedZoom = 15.5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Ink, surface = Panel, primary = Cyan,
                onBackground = Color.White, onSurface = Color.White
            )) {
                GpsScreen(
                    progress = routeProgress,
                    routeReady = routeReady,
                    distanceKm = routeDistanceKm,
                    onMapReady = { view, mlMap ->
                        mapView = view
                        map = mlMap
                        mlMap.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style ->
                            addMapLayers(style)
                            mlMap.addOnMapClickListener { point ->
                                destination = point
                                if (origin == null) origin = latestLocation?.let { LatLng(it.latitude, it.longitude) }
                                routeReady = origin != null
                                updateRouteMetrics()
                                drawRoute()
                                true
                            }
                            mlMap.addOnCameraIdleListener {
                                if (followCamera) scheduleCameraRestore()
                            }
                            latestLocation?.let { updateUserPosition(it) }
                        }
                    },
                    onPermissionGranted = { startLocationUpdates() },
                    onRecenter = {
                        latestLocation?.let {
                            map?.animateCamera(
                                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                    LatLng(it.latitude, it.longitude), 16.0
                                ), 650
                            )
                        }
                    },
                    onSetOrigin = {
                        latestLocation?.let {
                            origin = LatLng(it.latitude, it.longitude)
                            routeReady = destination != null
                            updateRouteMetrics()
                            drawRoute()
                        }
                    },
                    onToggleFollow = { followCamera = !followCamera },
                    onChangeIcon = {
                        userColor = if (userColor == "#35D7F2") "#A78BFA"
                        else if (userColor == "#A78BFA") "#FBBF24" else "#35D7F2"
                        map?.style?.let { style ->
                            style.getLayer("user-dot")?.setProperties(circleColor(userColor))
                        }
                    },
                    getMapView = { context ->
                        MapView(context).also { it.onCreate(null) }
                    }
                )
            }
        }
    }

    private fun addMapLayers(style: Style) {
        style.addSource(GeoJsonSource("user-source", Feature.fromGeometry(Point.fromLngLat(0.0, 0.0))))
        style.addLayer(CircleLayer("user-dot", "user-source").withProperties(
            circleRadius(8f), circleColor(userColor),
            circleStrokeColor("#FFFFFF"), circleStrokeWidth(2.5f)
        ))
        style.addSource(GeoJsonSource("route-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(LineLayer("route-line", "route-source").withProperties(
            lineColor("#35D7F2"), lineWidth(4f)
        ))
    }

    private fun updateUserPosition(location: Location) {
        latestLocation = location
        val point = Point.fromLngLat(location.longitude, location.latitude)
        map?.style?.getSourceAs<GeoJsonSource>("user-source")?.setGeoJson(Feature.fromGeometry(point))
        if (followCamera && map?.cameraPosition?.target == null) {
            map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), 15.5
            ), 500)
        }
        updateRouteMetrics()
        drawRoute()
    }

    private fun updateRouteMetrics() {
        val a = origin ?: return
        val b = destination ?: return
        val current = latestLocation ?: return
        val ax = a.longitude * cos(Math.toRadians(a.latitude))
        val ay = a.latitude
        val bx = b.longitude * cos(Math.toRadians(a.latitude))
        val by = b.latitude
        val px = current.longitude * cos(Math.toRadians(a.latitude))
        val py = current.latitude
        val dx = bx - ax
        val dy = by - ay
        val denominator = dx * dx + dy * dy
        val t = if (denominator == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / denominator).coerceIn(0.0, 1.0)
        routeProgress = t.toFloat()
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        routeDistanceKm = result[0] / 1000.0
    }

    private fun drawRoute() {
        val a = origin ?: return
        val b = destination ?: return
        val coordinates = listOf(Point.fromLngLat(a.longitude, a.latitude), Point.fromLngLat(b.longitude, b.latitude))
        val line = LineString.fromLngLats(coordinates)
        map?.style?.getSourceAs<GeoJsonSource>("route-source")?.setGeoJson(Feature.fromGeometry(line))
    }

    private fun scheduleCameraRestore() {
        restoreTask?.let(handler::removeCallbacks)
        restoreTask = Runnable {
            if (!followCamera) return@Runnable
            latestLocation?.let {
                val target = LatLng(it.latitude, it.longitude)
                map?.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(target, savedZoom), 700
                )
            }
        }
        handler.postDelayed(restoreTask!!, 5_000)
    }

    private fun startLocationUpdates() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        try {
            lm.requestLocationUpdates(provider, 1000L, 1f, object : LocationListener {
                override fun onLocationChanged(location: Location) = updateUserPosition(location)
            }, Looper.getMainLooper())
            lm.getLastKnownLocation(provider)?.let(::updateUserPosition)
        } catch (_: SecurityException) { }
    }

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroy() {
        restoreTask?.let(handler::removeCallbacks)
        mapView?.onDestroy()
        super.onDestroy()
    }

    @Composable
    private fun GpsScreen(
        progress: Float,
        routeReady: Boolean,
        distanceKm: Double,
        onMapReady: (MapView, MapLibreMap) -> Unit,
        onPermissionGranted: () -> Unit,
        onRecenter: () -> Unit,
        onSetOrigin: () -> Unit,
        onToggleFollow: () -> Unit,
        onChangeIcon: () -> Unit,
        getMapView: (Context) -> MapView
    ) {
        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            )
        }
        var follow by remember { mutableStateOf(true) }
        var iconIndex by remember { mutableIntStateOf(0) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            hasPermission = result.values.any { it }
            if (hasPermission) onPermissionGranted()
        }

        LaunchedEffect(Unit) {
            if (hasPermission) onPermissionGranted()
            else permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }

        Box(Modifier.fillMaxSize().background(Ink)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    getMapView(context).also { view ->
                        view.getMapAsync { mlMap ->
                            mlMap.uiSettings.isCompassEnabled = false
                            mlMap.uiSettings.isAttributionEnabled = true
                            mlMap.uiSettings.isLogoEnabled = false
                            mlMap.cameraPosition = CameraPosition.Builder().target(LatLng(13.7563, 100.5018)).zoom(11.5).build()
                            onMapReady(view, mlMap)
                        }
                    }
                }
            )
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("GPS MAP", color = Color.White, fontSize = 17.sp, letterSpacing = 3.sp)
                        Text("ULTRA CLEAN  •  V1", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    }
                    Box(Modifier.size(9.dp).background(Cyan, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("LIVE", color = Cyan, fontSize = 11.sp)
                }
                Text("แตะแผนที่เพื่อเลือกปลายทาง", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 9.dp))
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 190.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingActionButton(onClick = onRecenter, containerColor = Panel, contentColor = Cyan) {
                    Icon(Icons.Default.MyLocation, contentDescription = "กลับมาตำแหน่งฉัน")
                }
                FloatingActionButton(onClick = { onChangeIcon(); iconIndex++ }, containerColor = Panel, contentColor = Cyan) {
                    Icon(Icons.Default.Tune, contentDescription = "เปลี่ยนไอคอนตำแหน่ง")
                }
                FloatingActionButton(onClick = { follow = !follow; onToggleFollow() }, containerColor = Panel, contentColor = if (follow) Cyan else Muted) {
                    Icon(Icons.Default.Navigation, contentDescription = "ล็อกกล้อง")
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Panel, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ROUTE PROGRESS", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                        Text(if (routeReady) "ระยะตรง ${String.format("%.2f", distanceKm)} km" else "เลือกปลายทางบนแผนที่", color = Color.White, fontSize = 15.sp)
                    }
                    Text(if (routeReady) "${(progress * 100).toInt()}%" else "— %", color = Cyan, fontSize = 27.sp)
                }
                LinearProgressIndicator(progress = { if (routeReady) progress else 0f }, modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Cyan, trackColor = Color(0xFF303B49))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (routeReady) "เปอร์เซ็นต์คำนวณจากตำแหน่ง GPS บนแนวเส้นตรง" else "แตะปุ่มด้านล่างเพื่อตั้งจุดเริ่มต้น ณ ตำแหน่งปัจจุบัน", color = Muted, fontSize = 11.sp)
                }
                Button(onClick = onSetOrigin, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("ตั้งต้นทางจากตำแหน่งฉัน")
                }
            }
        }
    }
}
