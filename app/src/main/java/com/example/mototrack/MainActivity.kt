package com.example.mototrack

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.mototrack.Fragment.HistoryFragment
import com.example.mototrack.Fragment.HomeFragment
import com.example.mototrack.Fragment.MapFragment
import com.example.mototrack.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference

    // simpan fragment aktif sekarang
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔹 ambil uid user yang login
        val uid = auth.currentUser?.uid
        if (uid != null) {
            dbRef = FirebaseDatabase.getInstance().getReference("users").child(uid)

            dbRef.child("nama").get().addOnSuccessListener { snapshot ->
                val namaUser = snapshot.value?.toString() ?: "Guest"
                binding.topAppBar.findViewById<TextView>(R.id.tvUserName).text = namaUser
            }.addOnFailureListener {
                binding.topAppBar.findViewById<TextView>(R.id.tvUserName).text = "Guest"
            }
        } else {
            binding.topAppBar.findViewById<TextView>(R.id.tvUserName).text = "Guest"
        }

        // default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), R.id.nav_home, "Home")
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment(), R.id.nav_home, "Home")
                    true
                }
                R.id.nav_map -> {
                    loadFragment(MapFragment(), R.id.nav_map, "Map")
                    true
                }
                R.id.nav_history -> {
                    loadFragment(HistoryFragment(), R.id.nav_history, "History")
                    true
                }
                R.id.nav_logout -> {
                    auth.signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment, menuId: Int, title: String) {
        if (activeFragment != null && activeFragment!!::class == fragment::class) return

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(fragment::class.java.simpleName)
            .commit()

        activeFragment = fragment
        binding.bottomNav.selectedItemId = menuId

        // update title
        binding.topAppBar.findViewById<TextView>(R.id.tvTitle).text = title
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        val fm = supportFragmentManager
        if (fm.backStackEntryCount > 1) {
            fm.popBackStack()
            val current = fm.fragments.lastOrNull()
            when (current) {
                is HomeFragment -> binding.bottomNav.selectedItemId = R.id.nav_home
                is MapFragment -> binding.bottomNav.selectedItemId = R.id.nav_map
                is HistoryFragment -> binding.bottomNav.selectedItemId = R.id.nav_history
            }
            activeFragment = current
        } else {
            super.onBackPressed() // keluar aplikasi iya
        }
    }
}
