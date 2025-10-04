package com.example.billingapps

import android.graphics.drawable.Drawable

/**
 * File ini adalah pusat dari semua model data yang digunakan bersama di seluruh aplikasi.
 * Dengan memusatkan model data di sini, kita memastikan konsistensi dan menghindari error
 * akibat ketidakcocokan tipe data antar activity.
 */

// 1. Model data dasar untuk informasi aplikasi dari perangkat lokal.
data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null
)

// 2. Model data yang digabungkan, berisi informasi dari server dan lokal.
// Ini adalah model utama yang digunakan untuk menampilkan daftar di UI.
data class MergedAppInfo(
    val id: String,                      // Diambil dari server
    val name: String,                 // Diambil dari lokal (nama aplikasi)
    val packageName: String,          // Kunci utama untuk penggabungan
    val icon: Drawable,               // Diambil dari lokal (ikon aplikasi)
    var statusBlock: String,          // Diambil dari server, bisa diubah
    var isWhitelist: Boolean          // Diambil dari server, bisa diubah
)

// 3. Sealed interface untuk mengelola state UI saat memuat daftar aplikasi
// yang digabungkan (MergedAppInfo). Ini menangani state Loading, Success, dan Error.
sealed interface MergedAppListUiState {
    object Loading : MergedAppListUiState
    data class Success(val apps: List<MergedAppInfo>) : MergedAppListUiState
    data class Error(val message: String) : MergedAppListUiState
}

// 4. Sealed interface LAMA, mungkin masih berguna untuk kasus sederhana
// yang hanya memuat data lokal tanpa interaksi server.
sealed interface AppListUiState {
    object Loading : AppListUiState
    data class Success(val apps: List<AppInfo>) : AppListUiState
}

