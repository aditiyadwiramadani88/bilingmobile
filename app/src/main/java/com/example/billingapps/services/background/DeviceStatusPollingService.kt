package com.example.billingapps.services.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.billingapps.AdActivity
import com.example.billingapps.R
import com.example.billingapps.api.RetrofitClient
import com.example.billingapps.services.BlockedAppsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DeviceStatusPollingService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pollingRunnable: Runnable

    companion object {
        const val TAG = "DeviceStatusPolling"
        private const val POLLING_INTERVAL_MS = 3 * 1000L // 3 detik
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "DeviceStatusPollingChannel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Polling Service creating...")
        setupPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶️ Polling Service started.")
        startForeground(NOTIFICATION_ID, createNotification())
        handler.post(pollingRunnable)
        return START_STICKY
    }

    private fun setupPolling() {
        pollingRunnable = Runnable {
            scope.launch {
                checkDeviceStatus()
            }
            handler.postDelayed(pollingRunnable, POLLING_INTERVAL_MS)
        }
    }

    private suspend fun checkDeviceStatus() {
        Log.d(TAG, "Running polling check for device status.")
        val sharedPreferences =
            applicationContext.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
        val deviceId = sharedPreferences.getString("deviceId", null)

        if (deviceId == null) {
            Log.e(TAG, "Device ID not found. Cannot perform check.")
            return
        }

        try {
            val apiService = RetrofitClient.instance

            // --- Panggilan API Pertama: getDeviceStatus ---
            val response = apiService.getDeviceStatus(deviceId)

            if (response.isSuccessful) {
                val deviceStatusResponse = response.body()
                val isLockedFromServer = deviceStatusResponse?.data?.isLocked
                if (isLockedFromServer != null) {
                    Log.i(TAG, "✅ API call [getDeviceStatus] successful. Server is_locked status: $isLockedFromServer")
                    BlockedAppsManager.saveServerLockStatus(applicationContext, isLockedFromServer)
                } else {
                    Log.w(TAG, "is_locked status is null in response.")
                }

                val pendingAd = deviceStatusResponse?.data?.availablePendingAds?.getOrNull(0)
                if (pendingAd != null) {
                    Log.i(TAG, "🎉 Pending Ad found: ${pendingAd.name}. Launching AdActivity.")
                    launchAdActivity(pendingAd, deviceId)
                } else {
                    Log.d(TAG, "No pending ads found.")
                }
            } else {
                Log.e(TAG, "API call [getDeviceStatus] failed with code: ${response.code()} and message: ${response.message()}")
            }

            // --- Panggilan API Kedua (Digabung): sendStatus ---
            val sendStatusResponse = apiService.sendStatus(deviceId)
            if (sendStatusResponse.isSuccessful) {
                Log.i(TAG, "✅ API call [sendStatus] successful.")
            } else {
                Log.e(TAG, "API call [sendStatus] failed with code: ${sendStatusResponse.code()} and message: ${sendStatusResponse.message()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ An exception occurred during API calls", e)
        }
    }

    private fun launchAdActivity(ad: com.example.billingapps.api.Ad, deviceId: String) {
        val intent = Intent(applicationContext, AdActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AdActivity.EXTRA_AD_ID, ad.id)
            putExtra(AdActivity.EXTRA_AD_NAME, ad.name)
            putExtra(AdActivity.EXTRA_AD_TYPE, ad.type)
            putExtra(AdActivity.EXTRA_AD_CONTENT, ad.content)
            putExtra(AdActivity.EXTRA_AD_DURATION, ad.durationSeconds)
            putExtra(AdActivity.EXTRA_DEVICE_ID, deviceId)
        }
        startActivity(intent)
    }


    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Device Status Service",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.description = "Channel for silent device status polling."
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Billing Apps")
            .setContentText("Checking device status.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "⏹ Polling Service destroying...")
        handler.removeCallbacks(pollingRunnable)
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
