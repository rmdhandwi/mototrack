package com.example.mototrack.Fragment

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.SearchView
import androidx.fragment.app.Fragment
import com.example.mototrack.R
import com.example.mototrack.databinding.FragmentHistoryBinding
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
import com.mapbox.maps.plugin.annotation.generated.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private var mapView: MapView? = null
    private var markerManager: PointAnnotationManager? = null

    private var isMapReady = false
    private var pendingTanggal: String? = null

    private val allTanggal = mutableListOf<String>()



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)

        database = FirebaseDatabase.getInstance().getReference("trackingMap")

        loadTanggal()

        binding.btnBack.setOnClickListener {
            showTanggal()
        }

        binding.searchTanggal.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                showTanggalFiltered(newText ?: "")
                return true
            }
        })


        return binding.root
    }



    // ================= LOAD TANGGAL =================
    private fun loadTanggal() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return

                allTanggal.clear()

                for (dateSnap in snapshot.children) {
                    dateSnap.key?.let { allTanggal.add(it) }
                }

                showTanggalFiltered("")
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showTanggalFiltered(keyword: String) {
        binding.containerTanggal.removeAllViews()

        allTanggal
            .filter { it.contains(keyword, ignoreCase = true) }
            .sortedDescending() // 🔥 terbaru di atas
            .forEach { tanggal ->
                val btn = Button(requireContext()).apply {
                    text = tanggal
                    setOnClickListener { showMap(tanggal) }
                }
                binding.containerTanggal.addView(btn)
            }
    }


    // ================= TAMPILKAN MAP =================
    private fun showMap(tanggal: String) {
        binding.layoutTanggal.visibility = View.GONE
        binding.layoutMap.visibility = View.VISIBLE

        initMapbox()

        if (isMapReady) {
            loadHistory(tanggal)
        } else {
            pendingTanggal = tanggal
        }
    }


    private fun showTanggal() {
        binding.layoutMap.visibility = View.GONE
        binding.layoutTanggal.visibility = View.VISIBLE

        markerManager?.deleteAll()
        markerManager = null

        mapView?.onDestroy()
        mapView = null
    }

    // ================= MAPBOX =================
    private fun initMapbox() {
        if (mapView != null) return

        val accessToken = getString(R.string.mapbox_access_token)

        val resourceOptions = ResourceOptions.Builder()
            .accessToken(accessToken)
            .build()

        mapView = MapView(
            requireContext(),
            MapInitOptions(requireContext(), resourceOptions)
        )

        binding.mapContainer.addView(mapView)

        mapView!!.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            addMarkerIcon()

            markerManager = mapView!!
                .annotations
                .createPointAnnotationManager()

            isMapReady = true

            // 🔥 JIKA ADA REQUEST SEBELUM MAP READY
            pendingTanggal?.let {
                loadHistory(it)
                pendingTanggal = null
            }
        }
    }


    private fun addMarkerIcon() {
        mapView?.getMapboxMap()?.getStyle { style ->
            val bitmap = BitmapFactory.decodeResource(
                resources,
                R.drawable.ic_motor_resized
            )
            style.addImage("motor-icon", bitmap)
        }
    }

    // ================= LOAD HISTORY =================
    private fun loadHistory(tanggal: String) {
        val historyRef = database.child(tanggal).child("history")

        historyRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || mapView == null || markerManager == null) return

                val points = mutableListOf<Point>()
                markerManager?.deleteAll()

                for (child in snapshot.children) {
                    val lat = child.child("lat").getValue(Double::class.java)
                    val lon = child.child("lon").getValue(Double::class.java)
                    if (lat != null && lon != null) {
                        points.add(Point.fromLngLat(lon, lat))
                    }
                }

                if (points.isEmpty()) return

                // 🟢 MARKER AWAL
                markerManager?.create(
                    PointAnnotationOptions()
                        .withPoint(points.first())
                        .withIconImage("motor-icon")
                        .withTextField("Awal")
                        .withIconSize(1.0)
                        .withSymbolSortKey(10.0) // 🔥 MARKER PALING DEPAN
                )

                // 🔴 MARKER AKHIR
                if (points.size > 1) {
                    markerManager?.create(
                        PointAnnotationOptions()
                            .withPoint(points.last())
                            .withIconImage("motor-icon")
                            .withTextField("Akhir")
                            .withIconSize(1.0)
                            .withSymbolSortKey(10.0) // 🔥 MARKER PALING DEPAN
                    )
                }

                drawPolyline(points)

                mapView?.getMapboxMap()?.setCamera(
                    CameraOptions.Builder()
                        .center(points.last())
                        .zoom(15.0)
                        .build()
                )
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }


    private fun drawPolyline(points: List<Point>) {
        if (points.size < 2) return
        val mapboxMap = mapView?.getMapboxMap() ?: return
        val lineString = LineString.fromLngLats(points)

        mapboxMap.getStyle { style ->
            val sourceId = "history-source"
            val layerId = "history-layer"

            if (!style.styleSourceExists(sourceId)) {
                style.addSource(
                    geoJsonSource(sourceId) { geometry(lineString) }
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

    // ================= LIFECYCLE =================
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }

    override fun onDestroyView() {
        super.onDestroyView()

        markerManager?.deleteAll()
        markerManager = null

        mapView?.onDestroy()
        mapView = null
        _binding = null
    }
}


