package com.example.billingapps.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.billingapps.BlockScreenActivity
import com.example.billingapps.services.background.AppSyncService
import com.example.billingapps.services.background.DeviceStatusPollingService
import com.example.billingapps.services.background.LocationUpdateService

class FocusGuardAccessibilityService : AccessibilityService() {

    // --- Penambahan untuk Pengecekan Realtime ---
    private val handler = Handler(Looper.getMainLooper())
    private var currentForegroundAppPackage: String? = null
    private val CHECK_INTERVAL_MS = 5 * 1000L // 20 detik

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            // Jalankan pengecekan
            checkCurrentAppStatus()
            // Jadwalkan pengecekan berikutnya
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }
    // --- Akhir Penambahan ---

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()

            // Simpan package name aplikasi yang sedang di depan
            currentForegroundAppPackage = packageName

            // Langsung jalankan pengecekan saat ada event, jangan tunggu loop
            checkCurrentAppStatus()
        }
    }

    private fun checkCurrentAppStatus() {
        val packageName = currentForegroundAppPackage ?: return

        // 1. Ambil status 'is_locked' dari server
        val isServerLocked = BlockedAppsManager.getServerLockStatus(this)

        // 2. Cek apakah aplikasi yang dibuka statusnya "active" di internal storage
        val isAppMarkedAsBlocked = InternalStorageManager.isAppBlocked(this, packageName)

        // 3. Cek apakah aplikasi yang dibuka termasuk whitelist (jika iya -> selalu diblokir)
        val isAppWhitelisted = InternalStorageManager.getApps(this).any {
            it.packageName == packageName && it.isWhitelist == 1
        }

        // --- LOGIKA FINAL ---
        if (isAppWhitelisted || (isServerLocked && isAppMarkedAsBlocked)) {
            Log.d("FocusGuardService", "BLOCKING (from check): $packageName | ServerLocked=$isServerLocked, AppBlocked=$isAppMarkedAsBlocked, IsWhitelist=$isAppWhitelisted")
            val intent = Intent(this, BlockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } else {
            Log.v("FocusGuardService", "NOT BLOCKING (from check): $packageName | ServerLocked=$isServerLocked, AppBlocked=$isAppMarkedAsBlocked, IsWhitelist=$isAppWhitelisted")
        }
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo != null && resolveInfo.activityInfo.packageName == packageName
    }

    override fun onInterrupt() {
        Log.d("FocusGuardService", "Service interrupted, stopping periodic check.")
        handler.removeCallbacks(periodicCheckRunnable)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("FocusGuardService", "Service connected, starting periodic check.")
        handler.post(periodicCheckRunnable)

        val pollingIntent = Intent(this, DeviceStatusPollingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(pollingIntent)
        } else {
            startService(pollingIntent)
        }

        Log.d("FocusGuardService", "Starting LocationUpdateService...")
        val locationIntent = Intent(this, LocationUpdateService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(locationIntent)
        } else {
            startService(locationIntent)
        }

        Log.d("FocusGuardService", "Starting LocationUpdateService...")
        val appIntent = Intent(this, AppSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(appIntent)
        } else {
            startService(appIntent)
        }

    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("FocusGuardService", "Service unbound, stopping periodic check.")
        handler.removeCallbacks(periodicCheckRunnable)

        stopService(Intent(this, DeviceStatusPollingService::class.java))
        Log.d("FocusGuardService", "Stopping LocationUpdateService...")
        stopService(Intent(this, LocationUpdateService::class.java))
        return super.onUnbind(intent)
    }
}