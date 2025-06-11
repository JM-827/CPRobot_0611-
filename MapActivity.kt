package com.example.campuspatrolrobot

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.campuspatrolrobot.ui.theme.CampusPatrolRobotAppTheme
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.Query

class MapActivity : ComponentActivity() {
    private lateinit var googleMap: GoogleMap

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted && coarseLocationGranted) {
            Log.d("MapActivity", "Location permissions granted")
        } else {
            Log.e("MapActivity", "Location permissions denied")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.e("MapActivity", "Google Play Services unavailable: $resultCode")
            if (GoogleApiAvailability.getInstance().isUserResolvableError(resultCode)) {
                GoogleApiAvailability.getInstance().getErrorDialog(this, resultCode, 1)?.show()
            }
            finish()
            return
        } else {
            Log.d("MapActivity", "Google Play Services available")
        }

        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        try {
            setContent {
                CampusPatrolRobotAppTheme(darkTheme = true) {
                    MapScreen()
                }
            }
        } catch (e: Exception) {
            Log.e("MapActivity", "Error in onCreate: ${e.message}", e)
            finish()
        }
    }

    @Composable
    fun MapScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val mapView = remember { MapView(context) }
        var currentMarker by remember { mutableStateOf<Marker?>(null) }
        var currentState by remember { mutableStateOf("00") }
        var stateTimestamp by remember { mutableStateOf("알 수 없음") }
        val firestore = Firebase.firestore

        LaunchedEffect(Unit) {
            mapView.onCreate(null)
            mapView.onStart()
            mapView.onResume()
        }

        DisposableEffect(Unit) {
            onDispose {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }

        AndroidView(
            factory = { mapView },
            modifier = modifier.fillMaxSize(),
            update = { mapView ->
                mapView.getMapAsync { map ->
                    googleMap = map
                    if (googleMap == null) {
                        Log.e("MapActivity", "GoogleMap initialization failed")
                        return@getMapAsync
                    }
                    Log.d("MapActivity", "GoogleMap initialized successfully")
                    googleMap.uiSettings.isZoomControlsEnabled = true
                    googleMap.uiSettings.isMapToolbarEnabled = true
                    googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark))
                    googleMap.moveCamera(CameraUpdateFactory.zoomTo(18f))
                    val initialLatLng = LatLng(37.5665, 126.9780)
                    currentMarker = googleMap.addMarker(
                        MarkerOptions()
                            .position(initialLatLng)
                            .title("로봇 초기 위치")
                            .snippet("상태: 초기화")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                    )
                    googleMap.moveCamera(CameraUpdateFactory.newLatLng(initialLatLng))
                }
            }
        )

        // 실시간 GPS 데이터 조회
        LaunchedEffect(Unit) {
            firestore.collection("Realtime_Map_Data")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("MapActivity", "Firestore listen failed: ${e.message}")
                        return@addSnapshotListener
                    }
                    snapshot?.documents?.firstOrNull()?.let { document ->
                        val data = document.data
                        val lat = data?.get("latitude") as? Double ?: 37.5665
                        val lng = data?.get("longitude") as? Double ?: 126.9780
                        val gpsTimestamp = data?.get("timestamp") as? String ?: "알 수 없음"
                        val stateText = when (currentState) {
                            "00" -> "순찰 중"
                            "01" -> "순찰 완료"
                            "10" -> "정지 상태"
                            "11" -> "문제 발생"
                            else -> "알 수 없음"
                        }
                        Log.d("MapActivity", "Received GPS: lat=$lat, lng=$lng")
                        val newLatLng = LatLng(lat, lng)
                        currentMarker?.remove()
                        currentMarker = googleMap.addMarker(
                            MarkerOptions()
                                .position(newLatLng)
                                .title("로봇 위치")
                                .snippet("상태: $stateText\n상태 시간: $stateTimestamp\nGPS 시간: $gpsTimestamp")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                        )
                        currentMarker?.showInfoWindow()
                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLng(newLatLng),
                            1000,
                            null
                        )
                        Log.d("MapActivity", "Marker updated to lat: $lat, lng: $lng")
                    } ?: run {
                        Log.w("MapActivity", "No documents in Realtime_Map_Data")
                    }
                }
        }

        // 로봇 상태 조회
        LaunchedEffect(Unit) {
            firestore.collection("Robot_State")
                .document("current_state")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("MapActivity", "Robot_State listen failed: ${e.message}")
                        return@addSnapshotListener
                    }
                    snapshot?.data?.let { data ->
                        currentState = data["state"] as? String ?: "00"
                        stateTimestamp = data["timestamp"] as? String ?: "알 수 없음"
                        Log.d("MapActivity", "Received state: $currentState")
                        val stateText = when (currentState) {
                            "00" -> "순찰 중"
                            "01" -> "순찰 완료"
                            "10" -> "정지 상태"
                            "11" -> "문제 발생"
                            else -> "알 수 없음"
                        }
                        currentMarker?.snippet = "상태: $stateText\n상태 시간: $stateTimestamp\nGPS 시간: ${currentMarker?.snippet?.substringAfter("GPS 시간: ") ?: "알 수 없음"}"
                        currentMarker?.showInfoWindow()
                    } ?: run {
                        Log.w("MapActivity", "No document in Robot_State")
                    }
                }
        }
    }
}