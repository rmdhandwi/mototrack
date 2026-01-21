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
    private val motorRef = database.child("Motor")

    private lateinit var motorListener: ValueEventListener

    private var currentMotor = ModelMotor()
    private var isUserAction = false // 🔒 cegah konflik realtime

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

    // ================= FIREBASE REALTIME =================
    private fun loadMotorRealtime() {
        motorListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null || isUserAction) return

                snapshot.getValue(ModelMotor::class.java)?.let {
                    currentMotor = it
                    updateUI(it)
                }


            }

            override fun onCancelled(error: DatabaseError) {
                context?.let {
                    Toast.makeText(it, "Firebase error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        motorRef.addValueEventListener(motorListener)
    }

    // ================= CLICK HANDLER =================
    private fun setupClickListeners() {

        binding.cardKelistrikan.setOnClickListener {
            toggleStatus(
                field = "statusKelistrikan",
                newValue = !currentMotor.statusKelistrikan
            )
        }

        binding.cardStarter.setOnClickListener {
            toggleStatus(
                field = "statusStarter",
                newValue = !currentMotor.statusStarter
            )
        }

        binding.cardBuzzer.setOnClickListener {
            toggleStatus(
                field = "statusBuzzer",
                newValue = !currentMotor.statusBuzzer
            )
        }
    }

    private fun toggleStatus(field: String, newValue: Boolean) {
        isUserAction = true

        currentMotor = when (field) {
            "statusKelistrikan" -> currentMotor.copy(statusKelistrikan = newValue)
            "statusStarter" -> currentMotor.copy(statusStarter = newValue)
            "statusBuzzer" -> currentMotor.copy(statusBuzzer = newValue)
            else -> currentMotor
        }

        updateUI(currentMotor)
        animateCardClick(getCardByField(field))

        motorRef.child(field).setValue(newValue)
            .addOnCompleteListener { isUserAction = false }
    }

    private fun getCardByField(field: String): CardView =
        when (field) {
            "statusKelistrikan" -> binding.cardKelistrikan
            "statusStarter" -> binding.cardStarter
            else -> binding.cardBuzzer
        }

    // ================= UPDATE UI =================
    private fun updateUI(motor: ModelMotor) {

        updateCard(
            binding.cardKelistrikan,
            binding.iconKelistrikan,
            binding.tvKelistrikanStatus,
            motor.statusKelistrikan
        )

        updateCard(
            binding.cardStarter,
            binding.iconStarter,
            binding.tvStarterStatus,
            motor.statusStarter
        )

        updateCard(
            binding.cardBuzzer,
            binding.iconBuzzer,
            binding.tvBuzzerStatus,
            motor.statusBuzzer
        )

        updateSensorUI(motor.statusGetar)
    }

    private fun updateSensorUI(isActive: Boolean) {
        if (isActive) {
            binding.cardSensor.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.red)

            binding.iconSensor.setImageResource(R.drawable.ic_shield_on)
            binding.iconSensor.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))

            binding.tvSensorStatus.text = "Motor Bergerak!"
            binding.tvSensorStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        } else {
            binding.cardSensor.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.card_off)

            binding.iconSensor.setImageResource(R.drawable.ic_shield)
            binding.iconSensor.setColorFilter(ContextCompat.getColor(requireContext(), R.color.icon_off))

            binding.tvSensorStatus.text = "Motor Aman"
            binding.tvSensorStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        }
    }

    private fun updateCard(
        card: CardView,
        icon: ImageView,
        statusText: TextView,
        isOn: Boolean
    ) {
        card.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (isOn) R.color.card_on else R.color.card_off
        )

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

    // ================= ANIMASI =================
    private fun animateCardClick(card: CardView) {
        card.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(80)
            .withEndAction {
                card.animate().scaleX(1f).scaleY(1f).setDuration(80)
            }
    }

    // ================= LIFECYCLE =================
    override fun onDestroyView() {
        super.onDestroyView()
        motorRef.removeEventListener(motorListener)
        _binding = null
    }
}

