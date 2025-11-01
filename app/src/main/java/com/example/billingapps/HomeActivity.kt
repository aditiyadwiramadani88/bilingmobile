package com.example.billingapps

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.billingapps.api.PinLoginRequest
import com.example.billingapps.api.RetrofitClient
import com.example.billingapps.api.apps.AppInfoResponse
import com.example.billingapps.api.apps.AppsRetrofitClient
import com.example.billingapps.api.apps.BulkInsertAppItem
import com.example.billingapps.api.apps.BulkInsertAppsRequest
import com.example.billingapps.services.*
import com.example.billingapps.services.background.FrameCaptureService
import com.example.billingapps.services.background.LocationUpdateService
import com.example.billingapps.ui.theme.BillingAppsTheme
import kotlinx.coroutines.launch
import com.example.billingapps.services.background.AppSyncService
import com.example.billingapps.services.background.DeviceStatusPollingService
import retrofit2.HttpException
import java.io.IOException

class HomeActivity : ComponentActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.all { it }
        if (isGranted) {
            Toast.makeText(this, "Location permission granted.", Toast.LENGTH_SHORT).show()
            startLocationUpdateService(this) // Start service after getting permission
        } else {
            Toast.makeText(this, "Location permission is required for full functionality.", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notifications are recommended for service status.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        Handler(Looper.getMainLooper()).postDelayed({
            checkAndRequestNotificationPermission()
            checkAndRequestLocationPermissions()

            startUsageStatsService(this)
            startDeviceStatusPollingService(this)
            startAppSyncService(this)
        }, 1200)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d("HomeActivity", "Requesting Notification permission.")
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkAndRequestLocationPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val arePermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (arePermissionsGranted) {
            Log.d("HomeActivity", "Location permissions already granted.")
            startLocationUpdateService(this)
        } else {
            Log.d("HomeActivity", "Requesting location permissions.")
            locationPermissionLauncher.launch(requiredPermissions)
        }
    }
}

// --- Service Helper Functions ---

private fun safeStartForegroundService(context: Context, intent: Intent) {
    val serviceName = intent.component?.className ?: "Unknown FGS"
    try {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                    Log.d("ServiceHelper", "Successfully started FGS: $serviceName")
                } else {
                    context.startService(intent)
                    Log.d("ServiceHelper", "Successfully started pre-Oreo service: $serviceName")
                }
            } catch (e: SecurityException) {
                Log.e("ServiceHelper", "SecurityException for $serviceName. Missing permission?", e)
            } catch (e: IllegalStateException) {
                Log.e("ServiceHelper", "IllegalStateException for $serviceName. App not in a valid state to start FGS.", e)
            }
            catch (e: Exception) {
                Log.e("ServiceHelper", "Failed to start FGS '$serviceName': ${e.javaClass.simpleName}", e)
            }
        }, 300)
    } catch (e: Exception) {
        Log.e("ServiceHelper", "Outer catch failed to start FGS '$serviceName'", e)
    }
}

private fun safeStartService(context: Context, intent: Intent) {
    val serviceName = intent.component?.className ?: "Unknown Service"
    try {
        context.startService(intent)
        Log.d("ServiceHelper", "Successfully started background service: $serviceName")
    } catch (e: Exception) {
        Log.e("ServiceHelper", "Failed to start background service '$serviceName': ${e.message}", e)
    }
}

// --- Service Initializers ---

fun startLocationUpdateService(context: Context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Log.w("ServiceInit", "Cannot start LocationUpdateService: permission denied.")
        return
    }
    val serviceIntent = Intent(context, LocationUpdateService::class.java)
    safeStartForegroundService(context, serviceIntent)
}

private fun startUsageStatsService(context: Context) {
    val intent = Intent(context, FocusGuardUsageStatsService::class.java)
    safeStartService(context, intent)
}

private fun startDeviceStatusPollingService(context: Context) {
    val intent = Intent(context, DeviceStatusPollingService::class.java)
    safeStartService(context, intent)
}

private fun startAppSyncService(context: Context) {
    val intent = Intent(context, AppSyncService::class.java)
    safeStartService(context, intent)
}

private fun startScreenCapture(context: Context) {
    val intent = Intent(context, ScreenCaptureActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun stopScreenCapture(context: Context) {
    Log.d("HomeActivity", "Attempting to stop FrameCaptureService.")
    val intent = Intent(context, FrameCaptureService::class.java).apply {
        action = FrameCaptureService.ACTION_STOP_CAPTURE
    }
    safeStartService(context, intent)

    val prefs = context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean(ScreenCaptureActivity.PREF_KEY_IS_CAPTURE_RUNNING, false).apply()
}

// --- UI and Other Logic ---

@Composable
fun FocusGuardScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE) }
    val deviceId = remember { prefs.getString("deviceId", "Not Found") ?: "Not Found" }

    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityServiceEnabled(context)) }
    var hasUsageStats by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var hasOverlay by remember { mutableStateOf(hasOverlayPermission(context)) }
    var hasDeviceAdmin by remember { mutableStateOf(checkDeviceAdminPermission(context)) }
    var blockedAppsCount by remember { mutableStateOf(0) }
    var whitelistedAppsCount by remember { mutableStateOf(0) }
    var isLocked by remember { mutableStateOf<Boolean?>(null) }
    var isLoadingLockStatus by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) }
    var isCaptureRunning by remember {
        mutableStateOf(prefs.getBoolean(ScreenCaptureActivity.PREF_KEY_IS_CAPTURE_RUNNING, false))
    }
    // State untuk mengontrol dialog konfirmasi logout
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }


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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = checkAccessibilityServiceEnabled(context)
                hasUsageStats = hasUsageStatsPermission(context)
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
                hasOverlay = hasOverlayPermission(context)
                hasDeviceAdmin = checkDeviceAdminPermission(context) // Refresh device admin status
                reloadCountsFromStorage()
                fetchLockStatus()
                isCaptureRunning = prefs.getBoolean(ScreenCaptureActivity.PREF_KEY_IS_CAPTURE_RUNNING, false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 100.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "TimeBill", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(text = "Device ID: $deviceId", fontSize = 12.sp, color = Color.Gray)
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
            UsageStatsStatus(
                isEnabled = hasUsageStats,
                onActivateClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            BatteryOptimizationStatus(
                isIgnoring = isIgnoringBatteryOptimizations,
                onActivateClick = { requestIgnoreBatteryOptimizations(context) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            OverlayPermissionStatus(
                isEnabled = hasOverlay,
                onActivateClick = { requestOverlayPermission(context) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            DeviceAdminStatus(
                isEnabled = hasDeviceAdmin,
                onActivateClick = { requestDeviceAdminPermission(context) }
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
            Spacer(modifier = Modifier.height(16.dp))
            if (isCaptureRunning) {
                OptionCard(
                    modifier = Modifier.clickable {
                        stopScreenCapture(context)
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
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    // Tampilkan dialog konfirmasi, bukan langsung logout
                    showLogoutConfirmDialog = true
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.Red),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout", color = Color.Red)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFFF0F2F5))
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            LockStatusDisplay(isLocked = isLocked, isLoading = isLoadingLockStatus)
        }

        // Panggil dialog jika state-nya true
        if (showLogoutConfirmDialog) {
            LogoutConfirmationDialog(
                deviceId = deviceId,
                onDismiss = { showLogoutConfirmDialog = false },
                onLogoutSuccess = {
                    showLogoutConfirmDialog = false // Tutup dialog
                    // Jalankan logika logout yang sesungguhnya
                    val sharedPreferences = context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
                    with(sharedPreferences.edit()) {
                        clear()
                        apply()
                    }
                    val intent = Intent(context, PinActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                }
            )
        }
    }
}


// --- Composable UI Components ---

/**
 * Composable baru untuk menampilkan dialog konfirmasi logout dengan PIN.
 */
@Composable
fun LogoutConfirmationDialog(
    deviceId: String,
    onDismiss: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Konfirmasi Logout",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Masukkan PIN Anda untuk melanjutkan.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pin = it },
                    label = { Text("PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorMessage != null,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 20.sp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            if (pin.isNotBlank()) {
                                coroutineScope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    try {
                                        val request = PinLoginRequest(pin = pin)
                                        val response = RetrofitClient.instance.loginWithPin(deviceId, request)

                                        if (response.isSuccessful && response.body()?.success == true) {
                                            onLogoutSuccess()
                                        } else {
                                            errorMessage = "PIN yang Anda masukkan salah."
                                        }
                                    } catch (e: IOException) {
                                        errorMessage = "Gagal terhubung ke server."
                                    } catch (e: HttpException) {
                                        errorMessage = "PIN yang Anda masukkan salah."
                                    } catch (e: Exception) {
                                        errorMessage = "Terjadi kesalahan."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else {
                                errorMessage = "PIN tidak boleh kosong."
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Konfirmasi")
                        }
                    }
                }
            }
        }
    }
}

// --- Utility Functions ---

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

private fun getInstalledApps(context: Context): List<AppInfo> {
    val packageManager: PackageManager = context.packageManager
    val appInfoList = mutableListOf<AppInfo>()
    val installedApplications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

    for (applicationInfo in installedApplications) {
        if (applicationInfo.packageName == context.packageName) continue
        val appName = packageManager.getApplicationLabel(applicationInfo).toString()
        val packageName = applicationInfo.packageName
        appInfoList.add(AppInfo(name = appName, packageName = packageName))
    }
    return appInfoList.sortedBy { it.name.lowercase() }
}

private fun getInstalledLauncherApps(context: Context): List<AppInfo> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)

    val resolvedApps = packageManager.queryIntentActivities(intent, 0)
    val appInfoList = mutableListOf<AppInfo>()

    for (resolveInfo in resolvedApps) {
        val appName = resolveInfo.loadLabel(packageManager).toString()
        val packageName = resolveInfo.activityInfo.packageName
        appInfoList.add(AppInfo(name = appName, packageName = packageName))
    }

    return appInfoList.sortedBy { it.name.lowercase() }
}


suspend fun syncInstalledApps(context: Context): Boolean {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
    val deviceId = sharedPreferences.getString("deviceId", null)
    if (deviceId.isNullOrEmpty()) {
        Log.e("SyncInstalledApps", "Device ID not found.")
        return false
    }

    return try {
        val installedApps = getInstalledLauncherApps(context)
        val cachedApps = InternalStorageManager.getApps(context)
        val cachedPackages = cachedApps.map { it.packageName }.toSet()

        val newApps = installedApps.filterNot { cachedPackages.contains(it.packageName) }

        if (newApps.isNotEmpty()) {
            val bulkRequestItems = newApps.map { BulkInsertAppItem(it.name, it.packageName, "non_aktif", false) }
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

fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    val service = "${context.packageName}/${FocusGuardAccessibilityService::class.java.canonicalName}"
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(service) ?: false
}

fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        Log.e("PermissionCheck", "Failed to check usage stats permission", e)
        false
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent().apply {
        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

fun hasOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true // Granted at install time on older versions
    }
}

private fun requestOverlayPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}

fun checkDeviceAdminPermission(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val compName = MyDeviceAdminReceiver.getComponentName(context)
    return dpm.isAdminActive(compName)
}

private fun requestDeviceAdminPermission(context: Context) {
    val compName = MyDeviceAdminReceiver.getComponentName(context)
    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Aplikasi ini memerlukan izin Admin Perangkat untuk memastikan fitur pemblokiran aplikasi berfungsi dengan benar dan mencegah penghapusan paksa.")
    }
    context.startActivity(intent)
}

@Composable
fun BatteryOptimizationStatus(isIgnoring: Boolean, onActivateClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isIgnoring) Color(0xFFE6F9F0) else Color(0xFFFFF4E5))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Status Optimasi Baterai", fontWeight = FontWeight.Bold, color = Color.Black)
                Text(
                    text = if (isIgnoring) "Diabaikan (Direkomendasikan)" else "Aktif (Tidak Direkomendasikan)",
                    color = if (isIgnoring) Color(0xFF00875A) else Color(0xFFE67E22)
                )
            }
            if (!isIgnoring) {
                Button(
                    onClick = onActivateClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))
                ) {
                    Text("Nonaktifkan")
                }
            }
        }
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
                Text(text = statusText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = statusColor, textAlign = TextAlign.End)
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

@Composable
fun UsageStatsStatus(isEnabled: Boolean, onActivateClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isEnabled) Color(0xFFE6F9F0) else Color(0xFFFFF4E5))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Status Akses Data Penggunaan", fontWeight = FontWeight.Bold, color = Color.Black)
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

@Composable
fun OverlayPermissionStatus(isEnabled: Boolean, onActivateClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isEnabled) Color(0xFFE6F9F0) else Color(0xFFFFF4E5))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Status Izin Tampilan di Atas", fontWeight = FontWeight.Bold, color = Color.Black)
                Text(
                    text = if (isEnabled) "Aktif" else "Tidak Aktif (Dibutuhkan)",
                    color = if (isEnabled) Color(0xFF00875A) else Color(0xFFE67E22)
                )
            }
            if (!isEnabled) {
                Button(
                    onClick = onActivateClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))
                ) {
                    Text("Aktifkan")
                }
            }
        }
    }
}

@Composable
fun DeviceAdminStatus(isEnabled: Boolean, onActivateClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isEnabled) Color(0xFFE6F9F0) else Color(0xFFFFF4E5))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Status Admin Perangkat", fontWeight = FontWeight.Bold, color = Color.Black)
                Text(
                    text = if (isEnabled) "Aktif" else "Tidak Aktif (Dibutuhkan)",
                    color = if (isEnabled) Color(0xFF00875A) else Color(0xFFE67E22)
                )
            }
            if (!isEnabled) {
                Button(
                    onClick = onActivateClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))
                ) {
                    Text("Aktifkan")
                }
            }
        }
    }
}
