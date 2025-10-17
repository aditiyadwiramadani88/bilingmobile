package com.example.billingapps.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.billingapps.services.FocusGuardUsageStatsService
import com.example.billingapps.services.background.DeviceStatusPollingService
import com.example.billingapps.services.background.LocationUpdateService
import com.example.billingapps.hasUsageStatsPermission
import com.example.billingapps.services.background.AppSyncService

class StartServicesOnBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "✅ Device booted, checking permissions...")

            val hasUsageStats = hasUsageStatsPermission(context)
            val canOverlay = Settings.canDrawOverlays(context)

            if (hasUsageStats && canOverlay) {
                Log.d("BootReceiver", "🟢 Starting FocusGuardUsageStatsService + background services")

                // Jalankan FocusGuardUsageStatsService
                val usageServiceIntent = Intent(context, FocusGuardUsageStatsService::class.java)
                ContextCompat.startForegroundService(context, usageServiceIntent)

                // Jalankan DeviceStatusPollingService
                val pollingIntent = Intent(context, DeviceStatusPollingService::class.java)
                ContextCompat.startForegroundService(context, pollingIntent)

                // Jalankan LocationUpdateService
                val locationIntent = Intent(context, LocationUpdateService::class.java)
                ContextCompat.startForegroundService(context, locationIntent)
                // Jalankan appSync
                val appSyncIntent = Intent(context, AppSyncService::class.java)
                ContextCompat.startForegroundService(context, appSyncIntent)
            } else {
                Log.w("BootReceiver", "⚠️ Service tidak dijalankan. Izin belum lengkap:")
                Log.w("BootReceiver", "UsageStats=$hasUsageStats | Overlay=$canOverlay")
            }
        }
    }
}
