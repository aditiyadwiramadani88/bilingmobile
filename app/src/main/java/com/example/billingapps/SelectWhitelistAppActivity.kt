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
// --- PERUBAHAN IMPORT ---
import com.example.billingapps.api.apps.AppInfoResponse
import com.example.billingapps.api.apps.AppsRetrofitClient
import com.example.billingapps.api.apps.BulkUpdateAppItem
import com.example.billingapps.api.apps.BulkUpdateAppsRequest
import com.example.billingapps.services.InternalStorageManager
import com.example.billingapps.ui.theme.BillingAppsTheme
import kotlinx.coroutines.launch

data class SelectableWhitelistApp(
    val appName: String,
    val packageName: String,
    var isWhitelisted: Boolean
)

class SelectWhitelistAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BillingAppsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WhitelistAppsScreen { finish() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistAppsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var appState by remember { mutableStateOf<List<SelectableWhitelistApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        // Asumsi InternalStorageManager mengembalikan List<AppInfoResponse> dari package .api.apps
        val appsFromStorage = InternalStorageManager.getApps(context)
        appState = appsFromStorage.map {
            SelectableWhitelistApp(
                appName = it.appName,
                packageName = it.packageName,
                // --- PERBAIKAN: Konversi Int ke Boolean ---
                isWhitelisted = it.isWhitelist == 1
            )
        }.sortedBy { it.appName.lowercase() }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whitelist Apps", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isSaving = true
                        val success = saveWhitelistedApps(context, appState)
                        if (success) {
                            Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show()
                            onBackPressed()
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
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0)),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Save Whitelist", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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
                Text(
                    text = "Select/Deselect All",
                    color = Color(0xFF6A5AE0),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable {
                            val allSelected = appState.all { it.isWhitelisted }
                            appState = appState.map { it.copy(isWhitelisted = !allSelected) }
                        }
                )

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

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val filteredApps = appState.filter {
                        it.appName.contains(searchQuery, ignoreCase = true)
                    }
                    items(filteredApps, key = { it.packageName }) { app ->
                        WhitelistAppRow(
                            app = app,
                            onCheckedChange = { newCheckedState ->
                                appState = appState.map {
                                    if (it.packageName == app.packageName) {
                                        it.copy(isWhitelisted = newCheckedState)
                                    } else it
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
fun WhitelistAppRow(app: SelectableWhitelistApp, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val appIcon: Drawable? = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!app.isWhitelisted) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon.toBitmap().asImageBitmap(),
                contentDescription = "${app.appName} icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
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
            checked = app.isWhitelisted,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF6A5AE0),
                uncheckedColor = Color.Gray
            )
        )
    }
}

private suspend fun saveWhitelistedApps(context: Context, appState: List<SelectableWhitelistApp>): Boolean {
    val sharedPreferences = context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
    val deviceId = sharedPreferences.getString("deviceId", null)
    if (deviceId.isNullOrEmpty()) {
        Log.e("SaveWhitelistApps", "Device ID not found.")
        return false
    }

    try {
        // --- PERBAIKAN: Sesuaikan payload dengan model baru ---
        val itemsToUpdate = appState.map {
            BulkUpdateAppItem(
                appName = it.appName, // appName sekarang wajib diisi
                packageName = it.packageName,
                statusBlock = null, // Kirim null agar tidak mengubah status block
                isWhitelist = it.isWhitelisted
            )
        }
        val request = BulkUpdateAppsRequest(apps = itemsToUpdate)

        // --- PERBAIKAN: Gunakan AppsRetrofitClient ---
        val updateResponse = AppsRetrofitClient.instance.bulkUpdateApps(deviceId, request)

        if (!updateResponse.isSuccessful) {
            Log.e("SaveWhitelistApps", "API update error: ${updateResponse.errorBody()?.string()}")
            return false
        }

        // --- PERBAIKAN: Gunakan AppsRetrofitClient ---
        val allAppsResponse = AppsRetrofitClient.instance.getAllAppsByDevice(deviceId)
        if (!allAppsResponse.isSuccessful) {
            Log.e("SaveWhitelistApps", "Failed to fetch all apps after update: ${allAppsResponse.errorBody()?.string()}")
            return true
        }

        val latestAppsFromServer: List<AppInfoResponse> = allAppsResponse.body() ?: emptyList()
        InternalStorageManager.saveApps(context, latestAppsFromServer)
        return true

    } catch (e: Exception) {
        Log.e("SaveWhitelistApps", "Exception during save process", e)
        return false
    }
}

