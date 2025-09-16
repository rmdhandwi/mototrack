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
import com.example.mototrack.databinding.ActivityRegisterBinding
import com.example.mototrack.model.ModelUser
import java.util.UUID
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    private var isPasswordVisible = false // tambahkan flag

    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // inisialisasi binding
        binding = ActivityRegisterBinding.inflate(layoutInflater)
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

        auth = FirebaseAuth.getInstance()

        binding.btnDaftar.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val nama = binding.etNama.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            var isValid = true

            // Validasi username
            if (username.isEmpty()) {
                binding.etUsername.error = "Username tidak boleh kosong"
                isValid = false
            }
            if (!username.matches("^[a-z0-9_]+$".toRegex())) { // hanya huruf kecil
                binding.etUsername.error = "Username hanya boleh huruf kecil, angka, dan underscore"
                isValid = false
            }
            if (nama.isEmpty()) {
                binding.etNama.error = "Nama tidak boleh kosong"
                isValid = false
            }
            if (email.isEmpty()) {
                binding.etEmail.error = "Email tidak boleh kosong"
                isValid = false
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Format email tidak valid"
                isValid = false
            }
            if (password.length < 6) {
                binding.etPassword.error = "Password minimal 6 karakter"
                isValid = false
            }

            if (!isValid) return@setOnClickListener

            // Simpan ke Firebase Auth
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: ""
                        val user = ModelUser(userId, nama, username, email)

                        // Simpan detail user ke Realtime Database
                        database.child("users").child(userId).setValue(user)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()

                                // Kembali ke login
                                val intent = Intent(this, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Gagal simpan user: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Register gagal: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

    }
}