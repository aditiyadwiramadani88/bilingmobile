package com.example.billingapps.services.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.billingapps.R
import com.example.billingapps.api.apps.AppInfoResponse
import com.example.billingapps.api.apps.AppsRetrofitClient
import com.example.billingapps.api.apps.BulkInsertAppItem
import com.example.billingapps.api.apps.BulkInsertAppsRequest
import com.example.billingapps.AppInfo
import com.example.billingapps.services.InternalStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppSyncService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var syncRunnable: Runnable

    companion object {
        const val NOTIFICATION_ID = 4
        const val CHANNEL_ID = "AppSyncServiceChannel"
        // --- PERUBAHAN: Konstanta ACTION dihapus karena tidak lagi diperlukan ---
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Langsung setup runnable saat service dibuat
        setupSyncRunnable()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // --- PERUBAHAN: Service langsung berjalan tanpa memeriksa action ---
        startForeground(NOTIFICATION_ID, createNotification())

        // Hapus callback sebelumnya (jika ada) dan posting yang baru untuk memastikan hanya ada satu loop
        handler.removeCallbacks(syncRunnable)
        handler.post(syncRunnable)
        Log.d("AppSyncService", "Sync service started and running.")

        // START_STICKY memastikan service akan coba dijalankan ulang oleh sistem jika dimatikan
        return START_STICKY
    }

    private fun setupSyncRunnable() {
        syncRunnable = object : Runnable {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    Log.d("AppSyncService", "Running scheduled app sync...")
                    val success = syncInstalledApps(this@AppSyncService)
                    if (success) {
                        Log.d("AppSyncService", "Periodic sync successful.")
                    } else {
                        Log.e("AppSyncService", "Periodic sync failed.")
                    }
                }
                // Jadwalkan eksekusi berikutnya setelah 3 detik
                handler.postDelayed(this, 3000)
            }
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Sync Service")
            .setContentText("Menyinkronkan aplikasi di latar belakang.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ganti dengan ikon notifikasi Anda
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "App Sync Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Sangat penting untuk menghapus callback saat service dihancurkan
        handler.removeCallbacks(syncRunnable)
        Log.d("AppSyncService", "Sync service destroyed.")
    }
}

// --- FUNGSI DARI AppSyncUtils.kt SEKARANG BERADA DI SINI ---

private fun getInstalledApps(context: Context): List<AppInfo> {
    val packageManager: PackageManager = context.packageManager
    val appInfoList = mutableListOf<AppInfo>()

    val installedApplications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    Log.d("GetInstalledApps", "Total installed apps: ${installedApplications.size}")

    for (applicationInfo in installedApplications) {
        if (applicationInfo.packageName == context.packageName) continue
        val appName = packageManager.getApplicationLabel(applicationInfo).toString()
        val packageName = applicationInfo.packageName
        appInfoList.add(AppInfo(name = appName, packageName = packageName))
    }

    return appInfoList.sortedBy { it.name.lowercase() }
}

private suspend fun syncInstalledApps(context: Context): Boolean {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
    val deviceId = sharedPreferences.getString("deviceId", null)
    if (deviceId.isNullOrEmpty()) {
        Log.e("SyncInstalledApps", "Device ID not found.")
        return false
    }

    return try {
        val installedApps = getInstalledApps(context)
        val cachedApps = InternalStorageManager.getApps(context)
        val cachedPackages = cachedApps.map { it.packageName }.toSet()

        val newApps = installedApps.filterNot { cachedPackages.contains(it.packageName) }

        if (newApps.isNotEmpty()) {
            Log.d("SyncInstalledApps", "Found ${newApps.size} new apps to sync.")
            val bulkRequestItems = newApps.map { appInfo ->
                BulkInsertAppItem(
                    appName = appInfo.name,
                    packageName = appInfo.packageName,
                    statusBlock = "non_aktif",
                    isWhitelist = false
                )
            }
            val bulkRequest = BulkInsertAppsRequest(apps = bulkRequestItems)
            val bulkResponse = AppsRetrofitClient.instance.bulkInsertApps(deviceId, bulkRequest)
            if (!bulkResponse.isSuccessful) {
                Log.e("SyncInstalledApps", "Bulk insert failed: HTTP ${bulkResponse.code()}")
                return false
            }
        }

        val allAppsResponse = AppsRetrofitClient.instance.getAllAppsByDevice(deviceId)
        if (!allAppsResponse.isSuccessful) {
            Log.e("SyncInstalledApps", "Fetch failed: HTTP ${allAppsResponse.code()}")
            return false
        }

        val allServerApps: List<AppInfoResponse> = allAppsResponse.body() ?: emptyList()
        InternalStorageManager.saveApps(context, allServerApps)
        Log.d("SyncInstalledApps", "Synced ${allServerApps.size} apps to storage.")

        true
    } catch (e: Exception) {
        Log.e("SyncInstalledApps", "Unexpected exception: ${e.message}", e)
        false
    }
}

