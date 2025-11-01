package com.example.billingapps.services

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    /**
     * Called when the user has enabled this as a device admin.
     */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdmin", "Device admin enabled")
        Toast.makeText(context, "Device admin enabled", Toast.LENGTH_SHORT).show()
    }

    /**
     * Called when the user has disabled this as a device admin.
     */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdmin", "Device admin disabled")
        Toast.makeText(context, "Device admin disabled", Toast.LENGTH_SHORT).show()
    }

    companion object {
        /**
         * Helper method to get the ComponentName for this receiver.
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, MyDeviceAdminReceiver::class.java)
        }
    }
}
