package com.example.mototrack.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.mototrack.R
import com.example.mototrack.databinding.FragmentHomeBinding
import com.example.mototrack.model.ModelMotor
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private var currentMotor = ModelMotor() // menyimpan status lokal

    // Firebase reference & listener sebagai properti kelas
    private val motorRef = database.child("Motor")
    private lateinit var motorListener: ValueEventListener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        loadMotorRealtime()
        setupClickListeners()

        return binding.root
    }

    /** Load data motor dari Firebase */
    private fun loadMotorRealtime() {
        motorListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val motor = snapshot.getValue(ModelMotor::class.java)
                motor?.let {
                    currentMotor = it
                    if (_binding != null) { // pastikan view masih aktif
                        updateUI(it)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        motorRef.addValueEventListener(motorListener)
    }

    /** Setup klik Card untuk toggle status */
    private fun setupClickListeners() {
        binding.cardKelistrikan.setOnClickListener {
            val newStatus = !currentMotor.statusKelistrikan
            currentMotor = currentMotor.copy(statusKelistrikan = newStatus)
            updateCardTint(binding.cardKelistrikan, binding.iconKelistrikan, binding.tvKelistrikanStatus, newStatus)
            animateCardClick(binding.cardKelistrikan)
            updateMotorStatus("statusKelistrikan", newStatus)
        }

        binding.cardStarter.setOnClickListener {
            val newStatus = !currentMotor.statusStarter
            currentMotor = currentMotor.copy(statusStarter = newStatus)
            updateCardTint(binding.cardStarter, binding.iconStarter, binding.tvStarterStatus, newStatus)
            animateCardClick(binding.cardStarter)
            updateMotorStatus("statusStarter", newStatus)
        }

        binding.cardBuzzer.setOnClickListener {
            val newStatus = !currentMotor.statusBuzzer
            currentMotor = currentMotor.copy(statusBuzzer = newStatus)
            updateCardTint(binding.cardBuzzer, binding.iconBuzzer, binding.tvBuzzerStatus, newStatus)
            animateCardClick(binding.cardBuzzer)
            updateMotorStatus("statusBuzzer", newStatus)
        }
    }

    /** Update status ke Firebase */
    private fun updateMotorStatus(field: String, value: Boolean) {
        motorRef.child(field).setValue(value)
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Gagal memperbarui status: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /** Update semua UI sesuai status motor */
    private fun updateUI(motor: ModelMotor) {
        updateCardTint(binding.cardKelistrikan, binding.iconKelistrikan, binding.tvKelistrikanStatus, motor.statusKelistrikan)
        updateCardTint(binding.cardStarter, binding.iconStarter, binding.tvStarterStatus, motor.statusStarter)
        updateCardTint(binding.cardBuzzer, binding.iconBuzzer, binding.tvBuzzerStatus, motor.statusBuzzer)

        if (motor.statusGetar) {
            binding.cardSensor.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.red)
            binding.iconSensor.setImageResource(R.drawable.ic_shield_on)
            binding.iconSensor.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
            binding.tvSensorStatus.text = "Motor Bergerak!"
            binding.tvSensorStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        } else {
            binding.iconSensor.setImageResource(R.drawable.ic_shield)
            binding.iconSensor.setColorFilter(ContextCompat.getColor(requireContext(), R.color.icon_off))
            binding.tvSensorStatus.text = "Motor Diam"
            binding.tvSensorStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        }
    }

    private fun updateCardTint(card: CardView, icon: ImageView, statusText: TextView, isOn: Boolean) {
        val color = if (isOn) R.color.card_on else R.color.card_off
        card.backgroundTintList = ContextCompat.getColorStateList(requireContext(), color)

        icon.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                if (isOn) R.color.icon_on else R.color.icon_off
            )
        )
        statusText.text = if (isOn) "ON" else "OFF"
        statusText.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isOn) R.color.green else R.color.red
            )
        )
    }

    private fun animateCardClick(card: CardView) {
        card.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                card.animate().scaleX(1f).scaleY(1f).duration = 100
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hentikan listener sebelum view dihancurkan
        motorRef.removeEventListener(motorListener)
        _binding = null
    }
}
