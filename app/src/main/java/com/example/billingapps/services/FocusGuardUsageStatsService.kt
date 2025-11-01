package com.example.billingapps.services

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.billingapps.BlockScreenActivity
import com.example.billingapps.R
import com.example.billingapps.api.RetrofitClient
import kotlinx.coroutines.*

class FocusGuardUsageStatsService : Service() {

    private val TAG = "FocusGuardUsageStats"
    private val CHECK_INTERVAL_MS = 1000L
    private val handler = Handler(Looper.getMainLooper())

    private var lastServerLockStatus = false
    private var lastUnlockTime = 0L
    private var currentForegroundAppPackage: String? = null
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded(this)
        registerReceiver(unlockReceiver, IntentFilter("ACTION_UNLOCK_SUCCESS"), Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startForegroundCheckLoop()
        startPollingServer()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusGuard aktif")
            .setContentText("Memantau aplikasi dan status lock...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun getForegroundAppPackage(): String? {
        if (!hasUsagePermission()) return null
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 5000
        val usageEvents: UsageEvents = usageStatsManager.queryEvents(beginTime, endTime)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startForegroundCheckLoop() {
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                checkCurrentAppStatus()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun checkCurrentAppStatus() {
        val packageName = getForegroundAppPackage() ?: return
        val prefs = getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
        val lastSaved = prefs.getString("lastLockedPackage", null)

        if (lastSaved != packageName) {
            prefs.edit().putString("lastLockedPackage", packageName).apply()
            Log.d(TAG, "📦 Disimpan lastLockedPackage: $packageName")
        }

        val isServerLocked = BlockedAppsManager.getServerLockStatus(this)

        // Update status perubahan dari server
        if (isServerLocked != lastServerLockStatus) {
            Log.d(TAG, "Perubahan status lock server: $lastServerLockStatus → $isServerLocked")
            lastServerLockStatus = isServerLocked
        }

        // Ambil daftar app dari storage (blocked dan whitelist)
        val allApps = InternalStorageManager.getApps(this)
        val isAppBlocked = allApps.any { it.packageName == packageName && it.statusBlock == "active" }
        val isAppWhitelisted = allApps.any { it.packageName == packageName && it.isWhitelist == 1 }

        // 🔒 Jika server lock aktif → hanya blokir app yang tidak di-whitelist
        if (isServerLocked && !isAppWhitelisted) {
            if (isAppBlocked) {
                Log.w(TAG, "BLOCKING (server lock + blocked): $packageName")
                showOverlayBlockScreen()
            } else {
                Log.d(TAG, "Server lock aktif tapi app diizinkan: $packageName")
            }
            return
        }

        // 🔒 Jika server tidak lock, tetap cek app blocked lokal (misal parental mode)
        if (!isServerLocked && isAppBlocked && !isAppWhitelisted) {
            Log.w(TAG, "BLOCKING (local lock only): $packageName")
            showOverlayBlockScreen()
        }
    }


    private fun startPollingServer() {
        val sharedPrefs = getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
        val deviceId = sharedPrefs.getString("deviceId", "indonesia") ?: "indonesia"

        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val response = RetrofitClient.instance.getDeviceStatus(deviceId)
                    if (response.isSuccessful && response.body() != null) {
                        val isServerLocked = response.body()!!.data?.isLocked
                        if (isServerLocked != lastServerLockStatus) {
                            Log.d(TAG, "Polling: Status lock berubah: $lastServerLockStatus → $isServerLocked")
                            lastServerLockStatus = isServerLocked == true

                            if (System.currentTimeMillis() - lastUnlockTime < 5000) {
                                Log.d(TAG, "🕐 Melewati auto-lock karena baru saja unlock.")
                                continue
                            }

                            withContext(Dispatchers.Main) {
                                if (isServerLocked == true) {
                                    showOverlayBlockScreen()
                                } else {
                                    sendBroadcast(Intent("ACTION_CLOSE_BLOCK_SCREEN"))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling server: ${e.message}")
                }
                delay(3000L)
            }
        }
    }

    private fun showOverlayBlockScreen() {
        try {
            val intent = Intent(this, BlockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
            Log.d(TAG, "Menampilkan BlockScreenActivity (overlay)")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menampilkan BlockScreenActivity: ${e.message}")
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        return packageName.contains("launcher") ||
                packageName.contains("systemui") ||
                packageName.contains("recents") ||
                packageName == this.packageName
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "ACTION_UNLOCK_SUCCESS") {
                lastUnlockTime = System.currentTimeMillis()
                lastServerLockStatus = false
                Log.d(TAG, "🔓 Unlock sukses — update lastUnlockTime=$lastUnlockTime")
                sendBroadcast(Intent("ACTION_CLOSE_BLOCK_SCREEN"))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        unregisterReceiver(unlockReceiver)
        Log.d(TAG, "Service destroyed, stopping checks.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 5
        private const val CHANNEL_ID = "focus_guard_channel"

        fun createNotificationChannelIfNeeded(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "com.ui.system",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "System Ui"
                    description = "System Ui"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}