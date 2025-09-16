package com.example.mototrack.Fragment

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.mototrack.R
import com.example.mototrack.databinding.FragmentMapBinding
import com.google.firebase.database.*
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.ResourceOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import java.text.SimpleDateFormat
import java.util.*

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private lateinit var dbRef: DatabaseReference

    // Manager untuk marker Mapbox
    private val markerManager: PointAnnotationManager? by lazy {
        mapView?.annotations?.createPointAnnotationManager()
    }

    // 🔹 Ambil key tanggal hari ini untuk struktur Firebase
    private fun getTodayKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)

        // 🔹 Ambil token Mapbox dari res/values/strings.xml
        val accessToken = getString(R.string.mapbox_access_token)

        if (accessToken.isEmpty() || accessToken == "YOUR_MAPBOX_ACCESS_TOKEN") {
            throw IllegalArgumentException(
                "Mapbox access token belum diatur! Tambahkan di res/values/strings.xml"
            )
        }

        val resourceOptions = ResourceOptions.Builder()
            .accessToken(accessToken)
            .build()

        val mapInitOptions = MapInitOptions(requireContext(), resourceOptions)

        // Buat MapView
        mapView = MapView(requireContext(), mapInitOptions)
        binding.mapContainer.addView(mapView)

        // 🔹 Load style Mapbox (harus internet aktif)
        mapView?.getMapboxMap()?.loadStyleUri(Style.MAPBOX_STREETS) {
            addMarkerIcon()
            listenTodayTracking()
        }

        return binding.root
    }

    // 🔹 Dengarkan data realtime dari Firebase
    private fun listenTodayTracking() {
        val todayKey = getTodayKey()
        dbRef = FirebaseDatabase.getInstance().getReference("trackingMap").child(todayKey)

        // Posisi terbaru → marker
        dbRef.child("current").addValueEventListener(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lon = snapshot.child("longitude").getValue(Double::class.java)
                if (lat != null && lon != null) {
                    updateMarker(lat, lon)

                    binding.tvLatitude.text = "Latitude : $lat"
                    binding.tvLongitude.text = "Longitude : $lon"
                }

            }
            override fun onCancelled(error: DatabaseError) {
                // Tangani error bila gagal akses Firebase
            }
        })

        // Riwayat perjalanan → garis polyline
        dbRef.child("history").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val points = mutableListOf<Point>()
                for (child in snapshot.children) {
                    val lat = child.child("lat").getValue(Double::class.java)
                    val lon = child.child("lon").getValue(Double::class.java)
                    if (lat != null && lon != null) {
                        points.add(Point.fromLngLat(lon, lat))
                    }
                }
                drawPolyline(points)
            }

            override fun onCancelled(error: DatabaseError) {
                // Tangani error bila gagal akses Firebase
            }
        })
    }

    private fun addMarkerIcon() {
        mapView?.getMapboxMap()?.getStyle { style ->
            // Pastikan drawable ada di res/drawable/ic_motor.png
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_motor_resized)
            style.addImage("motor-icon", bitmap)
        }
    }


    // 🔹 Update marker ke posisi terbaru
    private fun updateMarker(lat: Double, lon: Double) {
        val mapboxMap = mapView?.getMapboxMap() ?: return

        // Geser kamera ke lokasi terbaru
        mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(lon, lat))
                .zoom(16.0) // bisa diubah sesuai kebutuhan
                .build()
        )

        // Hapus marker lama
        markerManager?.deleteAll()

        // Tambahkan marker baru dengan icon + text label
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(lon, lat))
            .withIconImage("motor-icon") // pakai icon custom
            .withTextField("Lokasi Motor") // nama yang muncul di atas marker
            .withTextOffset(listOf(0.0, -2.0)) // supaya teks muncul di atas marker
            .withTextSize(12.0)

        markerManager?.create(pointAnnotationOptions)
    }


    // 🔹 Gambar polyline dari riwayat
    private fun drawPolyline(points: List<Point>) {
        val mapboxMap = mapView?.getMapboxMap() ?: return

        if (points.size < 2) return

        val lineString = LineString.fromLngLats(points)

        mapboxMap.getStyle { style ->
            val sourceId = "route-source"
            val layerId = "route-layer"

            if (!style.styleSourceExists(sourceId)) {
                style.addSource(
                    geoJsonSource(sourceId) {
                        geometry(lineString)
                    }
                )
            } else {
                style.getSourceAs<GeoJsonSource>(sourceId)?.geometry(lineString)
            }

            if (!style.styleLayerExists(layerId)) {
                style.addLayer(
                    lineLayer(layerId, sourceId) {
                        lineColor("#FF0000")
                        lineWidth(4.0)
                    }
                )
            }
        }
    }


    // 🔹 Lifecycle MapView
    @SuppressLint("Lifecycle")
    override fun onStart() { super.onStart(); mapView?.onStart() }

    @SuppressLint("Lifecycle")
    override fun onStop() { super.onStop(); mapView?.onStop() }

    @SuppressLint("Lifecycle")
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }

    @SuppressLint("Lifecycle")
    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
        mapView = null
        _binding = null
    }
}
