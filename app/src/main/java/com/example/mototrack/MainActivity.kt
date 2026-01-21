package com.example.mototrack

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.mototrack.Fragment.*
import com.example.mototrack.databinding.ActivityMainBinding
import com.example.mototrack.service.MotorMonitorService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 🔔 Permission Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        // 🚀 START SERVICE
        val serviceIntent = Intent(this, MotorMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        auth = FirebaseAuth.getInstance()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = auth.currentUser?.uid
        if (uid != null) {
            dbRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
            dbRef.child("nama").get().addOnSuccessListener {
                binding.topAppBar.findViewById<TextView>(R.id.tvUserName)
                    .text = it.value?.toString() ?: "Guest"
            }
        }

        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), R.id.nav_home, "Home")
        }

        binding.bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment(), R.id.nav_home, "Home")
                R.id.nav_map -> loadFragment(MapFragment(), R.id.nav_map, "Map")
                R.id.nav_history -> loadFragment(HistoryFragment(), R.id.nav_history, "History")
                R.id.nav_logout -> {
                    auth.signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
            true
        }
    }

    private fun loadFragment(fragment: Fragment, menuId: Int, title: String) {
        if (activeFragment?.javaClass == fragment.javaClass) return

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()

        activeFragment = fragment
        binding.bottomNav.selectedItemId = menuId
        binding.topAppBar.findViewById<TextView>(R.id.tvTitle).text = title
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
