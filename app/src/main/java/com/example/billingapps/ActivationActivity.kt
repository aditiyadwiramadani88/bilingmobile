package com.example.billingapps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.billingapps.api.RetrofitClient
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

// Konstanta untuk SharedPreferences
private const val PREFS_NAME = "BillingAppPrefs"
private const val KEY_DEVICE_ID = "deviceId"

class ActivationActivity : ComponentActivity() {

    private val TAG = "ActivationActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ActivationScreen(
                isNetworkAvailable = { isNetworkAvailable() }
            )
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}

// Data class untuk menampung informasi dialog (dibuat public agar bisa diakses activity lain)
data class DialogInfo(val title: String, val message: String)

@Composable
fun ActivationScreen(
    isNetworkAvailable: () -> Boolean
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var deviceId by remember {
        mutableStateOf(sharedPreferences.getString(KEY_DEVICE_ID, "") ?: "")
    }
    var isLoading by remember { mutableStateOf(false) }
    var dialogInfo by remember { mutableStateOf<DialogInfo?>(null) }
    var isActivationSuccess by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val TAG = "ActivationScreen"

    // Cek koneksi internet saat pertama kali compose
    LaunchedEffect(Unit) {
        if (!isNetworkAvailable()) {
            dialogInfo = DialogInfo("Koneksi Error", "Tidak ada koneksi internet. Silakan periksa jaringan Anda.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Aktivasi Perangkat",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = deviceId,
            onValueChange = { deviceId = it },
            label = { Text("Masukkan Device ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (deviceId.isNotBlank()) {
                    coroutineScope.launch {
                        if (!isNetworkAvailable()) {
                            dialogInfo = DialogInfo("Koneksi Error", "Tidak ada koneksi internet.")
                            return@launch
                        }

                        isLoading = true
                        isActivationSuccess = false

                        try {
                            val response = RetrofitClient.instance.activateDevice(deviceId)

                            if (response.isSuccessful && response.body()?.data != null) {
                                val deviceName = response.body()?.data?.name
                                with(sharedPreferences.edit()) {
                                    putString(KEY_DEVICE_ID, deviceId)
                                    apply()
                                }
                                dialogInfo = DialogInfo("Aktivasi Berhasil!", "Nama Perangkat: $deviceName")
                                isActivationSuccess = true
                            } else {
                                val errorMsg = response.body()?.message ?: "Device tidak ditemukan."
                                dialogInfo = DialogInfo("Aktivasi Gagal", errorMsg)
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "IOException: ${e.message}")
                            dialogInfo = DialogInfo("Koneksi Gagal", "Gagal terhubung ke server. Periksa koneksi & alamat IP.")
                        } catch (e: HttpException) {
                            Log.e(TAG, "HttpException: ${e.code()} ${e.message()}")
                            val errorMsg = if (e.code() == 404) "Device tidak ditemukan (Error 404)." else "Error: ${e.code()} - ${e.message()}"
                            dialogInfo = DialogInfo("Error HTTP", errorMsg)
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception: ${e.javaClass.simpleName} ${e.message}")
                            dialogInfo = DialogInfo("Error", "Terjadi kesalahan tidak terduga.")
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    dialogInfo = DialogInfo("Input Tidak Valid", "Device ID tidak boleh kosong.")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Aktivasi")
        }
    }

    if (isLoading) {
        LoadingModal()
    }

    dialogInfo?.let { info ->
        MessageDialog(
            dialogInfo = info,
            onDismiss = {
                dialogInfo = null
                // *** PERUBAHAN UTAMA: Arahkan ke PinActivity setelah aktivasi sukses ***
                if (isActivationSuccess) {
                    val intent = Intent(context, PinActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                }
            }
        )
    }
}


@Composable
fun LoadingModal() {
    Dialog(onDismissRequest = { /* Biarkan kosong agar tidak bisa ditutup */ }) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun MessageDialog(
    dialogInfo: DialogInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = dialogInfo.title, fontWeight = FontWeight.Bold) },
        text = { Text(text = dialogInfo.message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
