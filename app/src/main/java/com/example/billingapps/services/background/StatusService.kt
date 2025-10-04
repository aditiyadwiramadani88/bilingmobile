package com.example.billingapps.services.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.billingapps.R
import com.example.billingapps.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class StatusService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val CHANNEL_ID = "status_channel"
    private val PREFS_NAME = "BillingAppPrefs"
    private val KEY_DEVICE_ID = "deviceId"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val deviceId = sharedPrefs.getString(KEY_DEVICE_ID, null)

        if (deviceId != null) {
            startForeground(4, buildNotification("Syncing status...")) // Ganti ID ke 4

            scope.launch {
                while (isActive) {
                    try {
                        val response = RetrofitClient.instance.sendStatus(deviceId)
                        if (response.isSuccessful) {
                            // Tidak perlu update notifikasi agar tetap silent
                        } else {
                            // Tidak perlu update notifikasi
                        }
                    } catch (e: IOException) {
                        // Tidak perlu update notifikasi
                    } catch (e: Exception) {
                        // Tidak perlu update notifikasi
                    }
                    delay(30_000) // 30 detik
                }
            }
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(content: String): Notification {
        // Intent dikosongkan agar tidak membuka apa-apa saat diklik
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(), // Intent kosong
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Billing Apps")
            .setContentText("Services are running.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            // --- UBAH KE PRIORITY_MIN ---
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    // Fungsi update notifikasi bisa dihapus karena tidak akan kita gunakan lagi
    // private fun updateNotification(content: String) { ... }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TimeBill Background Service",
                // --- UBAH KE IMPORTANCE_MIN ---
                NotificationManager.IMPORTANCE_MIN
            )
            channel.description = "Channel for silent background services."
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
