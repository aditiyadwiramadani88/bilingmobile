package com.example.billingapps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.billingapps.api.apps.AppInfoResponse
import com.example.billingapps.api.apps.AppsRetrofitClient
import com.example.billingapps.api.apps.BulkUpdateAppItem
import com.example.billingapps.api.apps.BulkUpdateAppsRequest
import com.example.billingapps.services.InternalStorageManager
import com.example.billingapps.ui.theme.BillingAppsTheme
import kotlinx.coroutines.launch

// Model data lokal untuk UI, agar bisa dimodifikasi tanpa mengubah data asli
data class SelectableApp(
    val appName: String,
    val packageName: String,
    var isBlocked: Boolean // Status yang bisa diubah oleh user
)

class SelectBlockAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BillingAppsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LockingAppsScreen { finish() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockingAppsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var appState by remember { mutableStateOf<List<SelectableApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Memuat aplikasi dari Internal Storage saat pertama kali layar dibuka
    LaunchedEffect(Unit) {
        isLoading = true
        val appsFromStorage = InternalStorageManager.getApps(context)
        appState = appsFromStorage.map {
            SelectableApp(
                appName = it.appName,
                packageName = it.packageName,
                isBlocked = it.statusBlock.equals("active", ignoreCase = true)
            )
        }.sortedWith(compareByDescending<SelectableApp> { it.isBlocked }.thenBy { it.appName.lowercase() }) // DIUBAH DI SINI
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locking Apps", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                modifier = Modifier.padding(horizontal = 8.dp) // Menambahkan padding agar tidak terlalu mepet
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isSaving = true
                        val success = saveBlockedApps(context, appState)
                        if (success) {
                            Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show()
                            onBackPressed() // Kembali ke halaman Home
                        } else {
                            Toast.makeText(context, "Failed to save changes.", Toast.LENGTH_LONG).show()
                        }
                        isSaving = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp), // Membuat sudut lebih bulat
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0)),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Lock Selected", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Kontrol Select/Deselect All
                Text(
                    text = "Select/Deselect All",
                    color = Color(0xFF6A5AE0),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable {
                            val allSelected = appState.all { it.isBlocked }
                            appState = appState.map { it.copy(isBlocked = !allSelected) }
                        }
                )

                // Search Bar - Updated UI
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    placeholder = { Text("Search apps", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        disabledContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true
                )

                // Daftar Aplikasi
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val filteredApps = appState.filter {
                        it.appName.contains(searchQuery, ignoreCase = true)
                    }
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            onCheckedChange = { newCheckedState ->
                                // Update state saat checkbox diubah
                                appState = appState.map {
                                    if (it.packageName == app.packageName) {
                                        it.copy(isBlocked = newCheckedState)
                                    } else {
                                        it
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppRow(app: SelectableApp, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    // Mengambil ikon aplikasi secara dinamis
    val appIcon: Drawable? = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null // Jika aplikasi tidak ditemukan, tidak ada ikon
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!app.isBlocked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Menampilkan icon aplikasi
        if (appIcon != null) {
            Image(
                bitmap = appIcon.toBitmap().asImageBitmap(),
                contentDescription = "${app.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
            // Placeholder jika icon tidak ditemukan
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.LightGray, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = app.appName,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Checkbox(
            checked = app.isBlocked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF6A5AE0),
                uncheckedColor = Color.Gray
            )
        )
    }
}

/**
 * Fungsi untuk menyimpan perubahan, mengupdate backend, dan memperbarui internal storage.
 * @return true jika berhasil, false jika gagal.
 */
private suspend fun saveBlockedApps(context: Context, appState: List<SelectableApp>): Boolean {
    val sharedPreferences = context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
    val deviceId = sharedPreferences.getString("deviceId", null)
    if (deviceId.isNullOrEmpty()) {
        Log.e("SaveBlockedApps", "Device ID not found.")
        return false
    }

    try {
        // 1. Kirim bulk update ke server
        val itemsToUpdate = appState.map {
            BulkUpdateAppItem(
                appName =it.appName,
                packageName = it.packageName,
                statusBlock = if (it.isBlocked) "active" else "non_aktif",
                isWhitelist = null // Tidak diubah di halaman ini
            )
        }
        val request = BulkUpdateAppsRequest(apps = itemsToUpdate)
        val updateResponse = AppsRetrofitClient.instance.bulkUpdateApps(deviceId, request)

        if (!updateResponse.isSuccessful) {
            Log.e("SaveBlockedApps", "API update error: ${updateResponse.errorBody()?.string()}")
            return false
        }
        Log.d("SaveBlockedApps", "Successfully updated apps on server.")

        // 2. Jika berhasil, ambil semua data terbaru dari server
        val allAppsResponse = AppsRetrofitClient.instance.getAllAppsByDevice(deviceId)
        if (!allAppsResponse.isSuccessful) {
            Log.e("SaveBlockedApps", "Failed to fetch all apps after update: ${allAppsResponse.errorBody()?.string()}")
            // Meski gagal fetch, update dianggap berhasil, tapi data lokal tidak ter-refresh
            return true
        }

        // 3. Simpan data terbaru ke Internal Storage
        val latestAppsFromServer: List<AppInfoResponse> = allAppsResponse.body() ?: emptyList()
        InternalStorageManager.saveApps(context, latestAppsFromServer)
        Log.d("SaveBlockedApps", "Successfully synced latest apps to internal storage.")

        return true

    } catch (e: Exception) {
        Log.e("SaveBlockedApps", "Exception during save process", e)
        return false
    }
}
