package com.example.billingapps.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_PACKAGE_ADDED ||
            intent?.action == Intent.ACTION_PACKAGE_REPLACED) {

            // Pastikan ini app sendiri yang baru diinstall
            val data: Uri? = intent.data
            val pkgName = data?.schemeSpecificPart
            if (pkgName == context?.packageName) {
                // Langsung buka SplashActivity
                val launchIntent = Intent(context, com.example.billingapps.SplashActivity::class.java)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context?.startActivity(launchIntent)
            }
        }
    }
}
