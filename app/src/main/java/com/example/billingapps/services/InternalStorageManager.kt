package com.example.billingapps.services

import android.content.Context
import com.example.billingapps.api.apps.AppInfoResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

/**
 * Kelas helper untuk mengelola penyimpanan daftar aplikasi di internal storage.
 * Menggunakan format JSON untuk menyimpan dan membaca data.
 */
object InternalStorageManager {

    private const val APPS_FILE_NAME = "synced_apps_cache.json"
    private val gson = Gson()

    /**
     * Menyimpan daftar aplikasi ke file di internal storage.
     * @param context Konteks aplikasi.
     * @param apps List AppInfoResponse yang akan disimpan.
     */
    fun saveApps(context: Context, apps: List<AppInfoResponse>) {
        try {
            val jsonString = gson.toJson(apps)
            context.openFileOutput(APPS_FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(jsonString.toByteArray())
            }
        } catch (e: IOException) {
            e.printStackTrace()
            // Handle exception, misalnya dengan logging
        }
    }

    /**
     * Mengambil daftar aplikasi dari file di internal storage.
     * @param context Konteks aplikasi.
     * @return List AppInfoResponse yang tersimpan, atau list kosong jika file tidak ada atau terjadi error.
     */
    fun getApps(context: Context): List<AppInfoResponse> {
        return try {
            val file = context.getFileStreamPath(APPS_FILE_NAME)
            if (!file.exists()) {
                return emptyList()
            }
            val jsonString = file.reader().readText()
            val type = object : TypeToken<List<AppInfoResponse>>() {}.type
            gson.fromJson(jsonString, type) ?: emptyList()
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun isAppBlocked(context: Context, packageName: String): Boolean {
        val apps = getApps(context)
        val app = apps.find { it.packageName == packageName }
        return app?.statusBlock == "active"
    }

    /**
     * Menghapus cache aplikasi dari internal storage.
     * @param context Konteks aplikasi.
     */
    fun clearApps(context: Context) {
        try {
            val file = context.getFileStreamPath(APPS_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
