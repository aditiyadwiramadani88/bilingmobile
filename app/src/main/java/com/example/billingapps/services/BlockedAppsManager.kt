package com.example.billingapps.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Mengelola status penguncian yang diterima dari server.
 * Tanggung jawab utamanya adalah menyimpan dan mengambil status 'is_locked'.
 */
object BlockedAppsManager {

    private const val PREFS_NAME = "BillingAppPrefs"
    private const val KEY_SERVER_LOCK = "server_lock"

    /**
     * Menyimpan status 'is_locked' yang didapat dari server ke SharedPreferences.
     * @param context Konteks aplikasi.
     * @param isLocked Status terkunci dari server.
     */
    fun saveServerLockStatus(context: Context, isLocked: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SERVER_LOCK, isLocked).apply()
        Log.d("BlockedAppsManager", "Server lock status saved: $isLocked")
    }

    /**
     * Mengambil status 'is_locked' terakhir yang disimpan dari SharedPreferences.
     * @param context Konteks aplikasi.
     * @return Boolean status terkunci. Default-nya adalah 'false'.
     */
    fun getServerLockStatus(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Default value adalah 'false', artinya tidak terkunci jika tidak ada data
        return prefs.getBoolean(KEY_SERVER_LOCK, false)
    }
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setServerLockStatus(context: Context, isLocked: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SERVER_LOCK, isLocked).apply()
    }



}

