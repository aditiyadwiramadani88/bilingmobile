package com.example.billingapps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.billingapps.api.RetrofitClient
import com.example.billingapps.api.apps.AppInfoResponse
import com.example.billingapps.api.apps.AppsRetrofitClient
import com.example.billingapps.api.apps.BulkInsertAppItem
import com.example.billingapps.api.apps.BulkInsertAppsRequest
import com.example.billingapps.services.*
import com.example.billingapps.services.background.FrameCaptureService
import com.example.billingapps.services.background.LocationUpdateService
import com.example.billingapps.services.background.StatusService
import com.example.billingapps.ui.theme.BillingAppsTheme
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {

    // --- PERUBAHAN 1: Pindahkan launcher ke level Activity ---
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            Toast.makeText(this, "Location permission granted.", Toast.LENGTH_SHORT).show()
            startLocationUpdateService(this)
        } else {
            Toast.makeText(this, "Location permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupPusherService()
        // --- PERUBAHAN 2: Panggil pengecekan izin di onCreate ---
        checkAndRequestLocationPermissions()

        setContent {
            BillingAppsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FocusGuardScreen()
                }
            }
        }
    }

    private fun checkAndRequestLocationPermissions() {
        val locationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val arePermissionsGranted = locationPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (arePermissionsGranted) {
            startLocationUpdateService(this)
        } else {
            locationPermissionLauncher.launch(locationPermissions)
        }
    }

    private fun setupPusherService() {
        val sharedPreferences = getSharedPreferences("BillingAppPrefs", MODE_PRIVATE)
        val deviceId = sharedPreferences.getString("deviceId", null)
        if (deviceId != null) {
            with(sharedPreferences.edit()) {
                putString("broadcastToken", MyApp.TOKEN)
                apply()
            }
            Log.d("HomeActivity", "Broadcast token ensured in SharedPreferences.")
            val statusIntent = Intent(this, StatusService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(statusIntent)
            } else {
                startService(statusIntent)
            }
        } else {
            Log.w("HomeActivity", "Device ID not found. Services will not start.")
        }
    }
}

// --- Ambil semua aplikasi terinstall (user + system) ---
private fun getInstalledApps(context: Context): List<AppInfo> {
    val packageManager: PackageManager = context.packageManager
    val appInfoList = mutableListOf<AppInfo>()

    val installedApplications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    Log.d("GetInstalledApps", "Total installed apps: ${installedApplications.size}")

    for (applicationInfo in installedApplications) {
        if (applicationInfo.packageName == context.packageName) continue
        val appName = packageManager.getApplicationLabel(applicationInfo).toString()
        val packageName = applicationInfo.packageName
        appInfoList.add(AppInfo(name = appName, packageName = packageName))
    }

    return appInfoList.sortedBy { it.name.lowercase() }
}

@Composable
fun FocusGuardScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE) }

    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityServiceEnabled(context)) }
    var blockedAppsCount by remember { mutableStateOf(0) }
    var whitelistedAppsCount by remember { mutableStateOf(0) }
    var isLocked by remember { mutableStateOf<Boolean?>(null) }
    var isLoadingLockStatus by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) }

    // State untuk status screen capture
    var isCaptureRunning by remember {
        mutableStateOf(prefs.getBoolean(ScreenCaptureActivity.PREF_KEY_IS_CAPTURE_RUNNING, false))
    }

    val reloadCountsFromStorage: () -> Unit = {
        val appsFromStorage = InternalStorageManager.getApps(context)
        blockedAppsCount = appsFromStorage.count { it.statusBlock == "active" }
        whitelistedAppsCount = appsFromStorage.count { it.isWhitelist == 1 }
    }

    val fetchLockStatus: () -> Unit = {
        coroutineScope.launch {
            isLoadingLockStatus = true
            isLocked = getDeviceLockStatus(context)
            isLoadingLockStatus = false
        }
    }

    // --- PERUBAHAN 3: Hapus logika izin dari LaunchedEffect ---
    LaunchedEffect(Unit) {
        reloadCountsFromStorage()
        fetchLockStatus()
    }

    val appSelectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        reloadCountsFromStorage()
    }

    val whitelistAppLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        reloadCountsFromStorage()
    }

    DisposableEffect(Unit) {
        val activity = context as ComponentActivity
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = checkAccessibilityServiceEnabled(context)
                reloadCountsFromStorage()
                fetchLockStatus()
                // Refresh status screen capture saat kembali ke activity
                isCaptureRunning = prefs.getBoolean(ScreenCaptureActivity.PREF_KEY_IS_CAPTURE_RUNNING, false)
            }
        }
        activity.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            activity.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F2F5))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "TimeBill", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(text = "Mode: active", fontSize = 16.sp, color = Color.Gray)
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSyncing = true
                            val success = syncInstalledApps(context)
                            if (success) {
                                reloadCountsFromStorage()
                                Toast.makeText(context, "Sync successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Sync failed. Check logs.", Toast.LENGTH_LONG).show()
                            }
                            isSyncing = false
                        }
                    },
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync Apps")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Apps")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.CheckCircle, label = "Blocking Apps", count = blockedAppsCount, iconColor = Color.Red)
                StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Close, label = "Whitelist Apps", count = whitelistedAppsCount, iconColor = Color(0xFF00875A))
            }
            Spacer(modifier = Modifier.height(24.dp))
            AccessibilityStatus(
                isEnabled = isAccessibilityEnabled,
                onActivateClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            OptionCard(
                modifier = Modifier.clickable { appSelectionLauncher.launch(Intent(context, SelectBlockAppActivity::class.java)) },
                icon = Icons.Default.CheckCircle,
                title = "Set Block Apps",
                subtitle = "Atur aplikasi yang akan diblokir",
                iconColor = Color(0xFF6A5AE0)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OptionCard(
                modifier = Modifier.clickable { whitelistAppLauncher.launch(Intent(context, SelectWhitelistAppActivity::class.java)) },
                icon = Icons.Default.Close,
                title = "Set Whitelist Apps",
                subtitle = "Pilih aplikasi yang diizinkan",
                iconColor = Color(0xFF00875A)
            )
            // --- KARTU SCREEN CAPTURE DINAMIS ---
            Spacer(modifier = Modifier.height(16.dp))
            if (isCaptureRunning) {
                OptionCard(
                    modifier = Modifier.clickable {
                        stopScreenCapture(context)
                        // Update UI langsung
                        isCaptureRunning = false
                    },
                    icon = Icons.Filled.Close,
                    title = "Hentikan Perekaman Layar",
                    subtitle = "Service sedang berjalan",
                    iconColor = Color.Gray
                )
            } else {
                OptionCard(
                    modifier = Modifier.clickable { startScreenCapture(context) },
                    icon = Icons.Filled.PlayArrow,
                    title = "Mulai Perekaman Layar",
                    subtitle = "Aktifkan service untuk tangkapan layar",
                    iconColor = Color(0xFFE74C3C)
                )
            }
            // ----------------------------------------------------
            Spacer(modifier = Modifier.weight(1f))
            LockStatusDisplay(isLocked = isLocked, isLoading = isLoadingLockStatus)
        }
    }
}

public fun startLocationUpdateService(context: Context) {
    val serviceIntent = Intent(context, LocationUpdateService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
    } else {
        context.startService(serviceIntent)
    }
    Log.d("HomeActivity", "Attempting to start LocationUpdateService.")
}

private fun startScreenCapture(context: Context) {
    val intent = Intent(context, ScreenCaptureActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

// --- FUNGSI BARU UNTUK MENGHENTIKAN SERVICE ---
private fun stopScreenCapture(context: Context) {
    Log.d("HomeActivity", "Attempting to stop FrameCaptureService.")
    val intent = Intent(context, FrameCaptureService::class.java).apply {
        action = FrameCaptureService.ACTION_STOP_CAPTURE
    }
    context.startService(intent) // Cukup startService, service akan menangani stop-nya sendiri

    // Update status di SharedPreferences
    val prefs = context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
    with(prefs.edit()) {
        putBoolean(ScreenCaptureActivity.PREF_KEY_IS_CAPTURE_RUNNING, false)
        apply()
    }
}


private suspend fun getDeviceLockStatus(context: Context): Boolean? {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
    val deviceId = sharedPreferences.getString("deviceId", null)
    if (deviceId.isNullOrEmpty()) {
        Log.e("LockStatus", "Device ID not found.")
        return null
    }
    return try {
        val response = RetrofitClient.instance.getDeviceStatus(deviceId)
        if (response.isSuccessful) {
            response.body()?.data?.isLocked.also {
                Log.d("LockStatus", "Successfully fetched lock status: $it")
            }
        } else {
            Log.e("LockStatus", "Failed: HTTP ${response.code()} - ${response.errorBody()?.string()}")
            null
        }
    } catch (e: Exception) {
        Log.e("LockStatus", "Exception: ${e.message}", e)
        null
    }
}

suspend fun syncInstalledApps(context: Context): Boolean {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
    val deviceId = sharedPreferences.getString("deviceId", null)
    if (deviceId.isNullOrEmpty()) {
        Log.e("SyncInstalledApps", "Device ID not found.")
        return false
    }

    return try {
        val installedApps = getInstalledApps(context)
        val cachedApps = InternalStorageManager.getApps(context)
        val cachedPackages = cachedApps.map { it.packageName }.toSet()

        val newApps = installedApps.filterNot { cachedPackages.contains(it.packageName) }

        if (newApps.isNotEmpty()) {
            Log.d("SyncInstalledApps", "Found ${newApps.size} new apps to sync.")
            val bulkRequestItems = newApps.map { appInfo ->
                BulkInsertAppItem(
                    appName = appInfo.name,
                    packageName = appInfo.packageName,
                    statusBlock = "non_aktif",
                    isWhitelist = false
                )
            }
            val bulkRequest = BulkInsertAppsRequest(apps = bulkRequestItems)
            val bulkResponse = AppsRetrofitClient.instance.bulkInsertApps(deviceId, bulkRequest)
            if (!bulkResponse.isSuccessful) {
                Log.e("SyncInstalledApps", "Bulk insert failed: HTTP ${bulkResponse.code()}")
                return false
            }
        }

        val allAppsResponse = AppsRetrofitClient.instance.getAllAppsByDevice(deviceId)
        if (!allAppsResponse.isSuccessful) {
            Log.e("SyncInstalledApps", "Fetch failed: HTTP ${allAppsResponse.code()}")
            return false
        }

        val allServerApps: List<AppInfoResponse> = allAppsResponse.body() ?: emptyList()
        InternalStorageManager.saveApps(context, allServerApps)
        Log.d("SyncInstalledApps", "Synced ${allServerApps.size} apps to storage.")

        true
    } catch (e: Exception) {
        Log.e("SyncInstalledApps", "Unexpected exception: ${e.message}", e)
        false
    }
}

@Composable
fun LockStatusDisplay(isLocked: Boolean?, isLoading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEDEDF4))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = "Lock Status", tint = Color(0xFF6A5AE0))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Lock Status", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                val statusText = when (isLocked) {
                    true -> "Active"
                    false -> "Non Active"
                    null -> "Error"
                }
                val statusColor = when (isLocked) {
                    true -> Color(0xFF00875A)
                    false -> Color.Red
                    null -> Color.Gray
                }
                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun OptionCard(modifier: Modifier = Modifier, icon: ImageVector, title: String, subtitle: String, iconColor: Color) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFEDEDF4))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(32.dp), tint = iconColor)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                Text(text = subtitle, fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, label: String, count: Int, iconColor: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFEDEDF4))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, fontSize = 14.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun AccessibilityStatus(isEnabled: Boolean, onActivateClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isEnabled) Color(0xFFE6F9F0) else Color(0xFFFFF4E5))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Status Aksesibilitas", fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = if (isEnabled) "Active" else "Tidak Active", color = if (isEnabled) Color(0xFF00875A) else Color(0xFFE67E22))
            }
            if (!isEnabled) {
                Button(onClick = onActivateClick, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))) {
                    Text("Aktifkan")
                }
            }
        }
    }
}

fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    val service = "${context.packageName}/${FocusGuardAccessibilityService::class.java.canonicalName}"
    val settingValue = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return settingValue?.contains(service) ?: false
}
