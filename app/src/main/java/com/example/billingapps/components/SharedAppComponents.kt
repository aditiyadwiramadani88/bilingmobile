package com.example.billingapps.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.billingapps.AppInfo
import com.example.billingapps.AppListUiState
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * File ini berisi data class dan Composable yang digunakan bersama oleh
 * SelectBlockAppActivity dan SelectWhitelistAppActivity untuk menghindari duplikasi kode.
 */

// Data class dan sealed interface bisa tetap di package utama jika lebih sering dipakai di luar komponen,
// atau dipindahkan ke sini juga. Untuk sekarang kita pindahkan semua.

/**
 * Composable untuk menampilkan daftar aplikasi dalam LazyColumn.
 * @param apps Daftar aplikasi yang akan ditampilkan.
 * @param selectedApps Set berisi package name aplikasi yang terpilih.
 * @param onAppSelected Lambda yang dipanggil saat checkbox aplikasi diubah.
 */
@Composable
fun ColumnScope.AppList(
    apps: List<AppInfo>,
    selectedApps: Set<String>,
    onAppSelected: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.weight(1f)
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppListItem(
                appInfo = app,
                isSelected = selectedApps.contains(app.packageName),
                onCheckedChange = { isSelected ->
                    onAppSelected(app.packageName, isSelected)
                }
            )
        }
    }
}

/**
 * Composable untuk menampilkan satu item aplikasi dalam daftar.
 * @param appInfo Informasi aplikasi yang akan ditampilkan.
 * @param isSelected Status terpilih (checked) dari aplikasi.
 * @param onCheckedChange Lambda yang dipanggil saat item diklik atau checkbox diubah.
 */
@Composable
fun AppListItem(
    appInfo: AppInfo,
    isSelected: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isSelected) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = appInfo.icon),
            contentDescription = "${appInfo.name} icon",
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = appInfo.name,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = isSelected,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF6A5AE0)
            )
        )
    }
}

/**
 * Fungsi untuk mendapatkan daftar semua aplikasi yang terinstall di perangkat.
 * @param context Konteks aplikasi.
 * @return List dari AppInfo yang sudah diurutkan berdasarkan nama.
 */
fun getInstalledApps(context: Context): List<AppInfo> {
    val packageManager: PackageManager = context.packageManager
    val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    val appList = mutableListOf<AppInfo>()

    for (app in apps) {
        // Filter untuk hanya menampilkan aplikasi yang bisa dibuka
        if (packageManager.getLaunchIntentForPackage(app.packageName) != null && app.packageName != context.packageName) {
            val appInfo = AppInfo(
                name = packageManager.getApplicationLabel(app).toString(),
                packageName = app.packageName,
                icon = packageManager.getApplicationIcon(app)
            )
            appList.add(appInfo)
        }
    }
    // Mengurutkan daftar berdasarkan nama aplikasi
    return appList.sortedBy { it.name.lowercase() }
}
