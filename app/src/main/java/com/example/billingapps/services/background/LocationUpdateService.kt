package com.example.billingapps.services.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.billingapps.R
import com.example.billingapps.api.LocationUpdateRequest
import com.example.billingapps.api.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class LocationUpdateService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationRunnable: Runnable
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    companion object {
        const val TAG = "LocationUpdateService"
        private const val UPDATE_INTERVAL_MS = 5 * 1000L // 5 detik
        private const val NOTIFICATION_ID = 2 // ID Notifikasi harus unik per service
        private const val NOTIFICATION_CHANNEL_ID = "LocationUpdateChannel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Location Service creating...")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationSender()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶️ Location Service started.")
        startForeground(NOTIFICATION_ID, createNotification())
        handler.post(locationRunnable)
        return START_STICKY
    }

    private fun setupLocationSender() {
        locationRunnable = Runnable {
            sendCurrentLocation()
            handler.postDelayed(locationRunnable, UPDATE_INTERVAL_MS)
        }
    }

    private fun sendCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Cannot get location.")
            return
        }

        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "📍 Location fetched: Lat: ${location.latitude}, Lon: ${location.longitude}")
                    val sharedPreferences = applicationContext.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
                    val deviceId = sharedPreferences.getString("deviceId", null)

                    if (deviceId.isNullOrEmpty()) {
                        Log.e(TAG, "Device ID not found. Cannot send location.")
                        return@addOnSuccessListener
                    }

                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    val timestamp = sdf.format(Date(location.time))

                    val locationRequest = LocationUpdateRequest(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = if (location.hasAccuracy()) location.accuracy else null,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        speed = if (location.hasSpeed()) location.speed else null,
                        heading = if (location.hasBearing()) location.bearing else null,
                        timestamp = timestamp
                    )
                    scope.launch { sendLocationToApi(deviceId, locationRequest) }
                } else {
                    Log.w(TAG, "Fetched location is null.")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get current location.", e)
            }
    }

    private suspend fun sendLocationToApi(deviceId: String, request: LocationUpdateRequest) {
        try {
            val response = RetrofitClient.instance.sendLocation(deviceId, request)
            if (response.isSuccessful) {
                Log.i(TAG, "✅ Location successfully sent to API. Message: ${response.body()?.message}")
            } else {
                Log.e(TAG, "API call failed with code: ${response.code()} and message: ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ An exception occurred during API call", e)
        }
    }


    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Service",
                // --- UBAH KE IMPORTANCE_MIN ---
                NotificationManager.IMPORTANCE_MIN
            )
            channel.description = "Channel for silent location service notification."
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Billing Apps")
            .setContentText("Location service is running.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            // --- UBAH KE PRIORITY_MIN ---
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "⏹ Location Service destroying...")
        handler.removeCallbacks(locationRunnable)
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
