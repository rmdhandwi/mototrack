package com.example.mototrack

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mototrack.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var isPasswordVisible = false // tambahkan flag
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().getReference("users")

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()

        // inisialisasi binding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // kalau masih mau pakai insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.etPassword.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = 2 // posisi drawableEnd (kanan)
                val drawable = binding.etPassword.compoundDrawables[drawableEnd]
                if (drawable != null && event.rawX >= (binding.etPassword.right - drawable.bounds.width())) {
                    // toggle visibility
                    isPasswordVisible = !isPasswordVisible
                    if (isPasswordVisible) {
                        binding.etPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        binding.etPassword.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_lock_resized, 0, R.drawable.ic_visibility_resized, 0
                        )
                    } else {
                        binding.etPassword.inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        binding.etPassword.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_lock_resized, 0, R.drawable.ic_visibility_off_resized, 0
                        )
                    }
                    // pindahkan cursor ke akhir teks
                    binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
                    return@setOnTouchListener true
                }
            }
            false
        }

        binding.tvDaftar.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // login button
        binding.btnLogin.setOnClickListener {
            val usernameInput = binding.etUsername.text.toString().trim().lowercase()
            val password = binding.etPassword.text.toString().trim()

            if (usernameInput.isEmpty()) {
                binding.etUsername.error = "Username tidak boleh kosong"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.etPassword.error = "Password tidak boleh kosong"
                return@setOnClickListener
            }

            // 🔎 cari user berdasarkan username
            database.orderByChild("username").equalTo(usernameInput)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val userSnap = snapshot.children.first() // ambil user pertama
                            val email = userSnap.child("email").getValue(String::class.java)

                            if (email != null) {
                                // login pakai email & password di Firebase Auth
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            Toast.makeText(this@LoginActivity, "Login berhasil!", Toast.LENGTH_SHORT).show()
                                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                            finish()
                                        } else {
                                            binding.etPassword.error = "Password salah, coba lagi"
                                        }
                                    }
                            } else {
                                binding.etUsername.error = "Email tidak ditemukan untuk user ini"
                            }
                        } else {
                            binding.etUsername.error = "Username belum terdaftar"
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        binding.etUsername.error = "Terjadi kesalahan: ${error.message}"
                    }
                })
        }

    }
}