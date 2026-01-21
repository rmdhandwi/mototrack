package com.example.mototrack.service

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mototrack.R
import com.example.mototrack.model.ModelMotor
import com.google.firebase.database.*

class MotorMonitorService : Service() {

    private lateinit var motorRef: DatabaseReference
    private var lastMotor = ModelMotor()
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()

        // 🔹 Foreground notification (WAJIB)
        startForeground(1, createForegroundNotification())

        motorRef = FirebaseDatabase.getInstance().getReference("Motor")

        motorRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val motor = snapshot.getValue(ModelMotor::class.java) ?: return

                // 🚨 GETAR TERDETEKSI
                if (!lastMotor.statusGetar && motor.statusGetar) {
                    showAlertNotification()
                    if (motor.statusBuzzer) startAlarm()
                }

                // 📴 GETAR BERHENTI
                if (lastMotor.statusGetar && !motor.statusGetar) {
                    stopAlarm()
                }

                lastMotor = motor
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    // ================= FOREGROUND NOTIFICATION =================

    private fun createForegroundNotification(): Notification {
        val channelId = "motor_monitor"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Motor Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MotoTrack Aktif")
            .setContentText("Memantau keamanan motor")
            .setOngoing(true)
            .build()
    }

    // ================= ALERT NOTIFICATION =================

    private fun showAlertNotification() {
        val channelId = "motor_alert"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Motor",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Motor terdeteksi bergerak"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 500, 500)

                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🚨 MOTOR BERGERAK!")
            .setContentText("Motor terdeteksi bergerak")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(99, notification)
    }

    // ================= ALARM CUSTOM =================

    private fun startAlarm() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.sirine)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
