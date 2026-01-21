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
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
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

    private var markerManager: PointAnnotationManager? = null
    private var currentListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null

    private var motorMarker: PointAnnotation? = null


    // ===== Ambil tanggal hari ini (yyyy-MM-dd) =====
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

        val accessToken = getString(R.string.mapbox_access_token)
        require(accessToken.isNotBlank()) { "Mapbox token belum diatur!" }

        val resourceOptions = ResourceOptions.Builder()
            .accessToken(accessToken)
            .build()

        mapView = MapView(
            requireContext(),
            MapInitOptions(requireContext(), resourceOptions)
        )

        binding.mapContainer.addView(mapView)

        mapView!!.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style ->

            // 🔥 ADD ICON SETELAH STYLE READY
            val bitmap = BitmapFactory.decodeResource(
                resources,
                R.drawable.ic_motor_resized
            )
            style.addImage("motor-icon", bitmap)

            markerManager = mapView!!
                .annotations
                .createPointAnnotationManager()

            listenTodayTracking()
        }


        return binding.root
    }

    // ===== Firebase Listener =====
    private fun listenTodayTracking() {
        val todayKey = getTodayKey()
        dbRef = FirebaseDatabase.getInstance()
            .getReference("trackingMap")
            .child(todayKey)

        // ---- CURRENT POSITION ----
        currentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || mapView == null) return

                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lon = snapshot.child("longitude").getValue(Double::class.java)

                if (lat != null && lon != null) {
                    updateMarker(lat, lon)
                    binding.tvLatitude.text = "Latitude : $lat"
                    binding.tvLongitude.text = "Longitude : $lon"
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        dbRef.child("current").addValueEventListener(currentListener!!)

        // ---- HISTORY ----
        historyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || mapView == null) return

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

            override fun onCancelled(error: DatabaseError) {}
        }

        dbRef.child("history").addValueEventListener(historyListener!!)
    }

    // ===== Update Marker =====
    private fun updateMarker(lat: Double, lon: Double) {
        val mapboxMap = mapView?.getMapboxMap() ?: return
        val point = Point.fromLngLat(lon, lat)

        mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(point)
                .zoom(16.0)
                .build()
        )

        if (motorMarker == null) {
            // 🔥 BUAT MARKER SEKALI
            markerManager?.create(
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(lon, lat))
                    .withIconImage("motor-icon")
                    .withIconSize(1.0)
                    .withSymbolSortKey(10.0)
            )

        } else {
            // 🔄 UPDATE POSISI
            motorMarker?.point = point
            motorMarker?.let { markerManager?.update(it) }
        }
    }



    // ===== Polyline =====
    private fun drawPolyline(points: List<Point>) {
        if (points.size < 2) return

        val mapboxMap = mapView?.getMapboxMap() ?: return
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
                style.getSourceAs<GeoJsonSource>(sourceId)
                    ?.geometry(lineString)
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

    // ===== Lifecycle =====
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }

    override fun onDestroyView() {
        super.onDestroyView()

        currentListener?.let {
            dbRef.child("current").removeEventListener(it)
        }
        historyListener?.let {
            dbRef.child("history").removeEventListener(it)
        }

        markerManager?.deleteAll()
        markerManager = null

        mapView?.onDestroy()
        mapView = null
        _binding = null
    }
}
