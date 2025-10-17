package com.example.billingapps.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import com.example.billingapps.BlockScreenActivity
import com.example.billingapps.R

class FocusGuardUsageStatsService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentForegroundAppPackage: String? = null
    private val CHECK_INTERVAL_MS = 3000L

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            checkCurrentAppStatus()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")

        createNotificationChannelIfNeeded(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        handler.post(periodicCheckRunnable)

        // Tidak menjalankan service lain, hanya UsageStatsService ini saja

        return START_STICKY
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusGuard aktif")
            .setContentText("Memantau aplikasi yang sedang digunakan...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun getForegroundAppPackage(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 1000 * 5
        val usageEvents: UsageEvents = usageStatsManager.queryEvents(beginTime, endTime)
        var lastPackage: String? = null
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }

        return lastPackage
    }

    private fun checkCurrentAppStatus() {
        val packageName = getForegroundAppPackage() ?: return

        // Jika user buka recent apps atau home launcher
        if (packageName.contains("launcher") || packageName.contains("systemui") || packageName.contains("recents")) {
            Log.d(TAG, "User mungkin buka recent apps/home. Tutup BlockScreenActivity.")
            val intent = Intent("ACTION_CLOSE_BLOCK_SCREEN")
            sendBroadcast(intent)
            return
        }

        // Logic lama tetap jalan
        if (packageName == currentForegroundAppPackage) return
        currentForegroundAppPackage = packageName

        val isServerLocked = BlockedAppsManager.getServerLockStatus(this)
        val isAppMarkedAsBlocked = InternalStorageManager.isAppBlocked(this, packageName)
        val isAppWhitelisted = InternalStorageManager.getApps(this).any {
            it.packageName == packageName && it.isWhitelist == 1
        }

        if (isAppWhitelisted || (isServerLocked && isAppMarkedAsBlocked)) {
            Log.w(TAG, "BLOCKING: $packageName")
            val intent = Intent(this, BlockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(periodicCheckRunnable)
        Log.d(TAG, "Service destroyed, stopping checks.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FocusGuardUsageStats"
        private const val NOTIFICATION_ID = 5
        private const val CHANNEL_ID = "focus_guard_channel"

        fun createNotificationChannelIfNeeded(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "FocusGuard Monitoring",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Channel untuk service FocusGuard"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
