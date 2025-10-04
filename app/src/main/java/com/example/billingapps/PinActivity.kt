package com.example.billingapps

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billingapps.api.PinLoginRequest
import com.example.billingapps.api.RetrofitClient
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

// Konstanta SharedPreferences (pastikan sama dengan di ActivationActivity)
private const val PREFS_NAME = "BillingAppPrefs"
private const val KEY_DEVICE_ID = "deviceId"

class PinActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PinScreen()
        }
    }
}

@Composable
fun PinScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val deviceId = sharedPreferences.getString(KEY_DEVICE_ID, null)

    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var dialogInfo by remember { mutableStateOf<DialogInfo?>(null) }
    var isLoginSuccess by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val TAG = "PinScreen"

    // Jika karena suatu alasan deviceId tidak ada, kembali ke halaman aktivasi
    if (deviceId == null) {
        LaunchedEffect(Unit) {
            val intent = Intent(context, ActivationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
            (context as? ComponentActivity)?.finish()
        }
        return // Hentikan komposisi lebih lanjut
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Masukkan PIN",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pin = it },
            label = { Text("PIN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 20.sp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (pin.isNotBlank()) {
                    coroutineScope.launch {
                        isLoading = true
                        isLoginSuccess = false
                        try {
                            val request = PinLoginRequest(pin = pin)
                            val response = RetrofitClient.instance.loginWithPin(deviceId, request)

                            if (response.isSuccessful && response.body()?.success == true) {
                                isLoginSuccess = true
                                // Tidak perlu menampilkan dialog sukses, langsung navigasi
//                                val intent = Intent(context, HomeActivity::class.java).apply {
//                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                                }
//                                context.startActivity(intent)
//                                (context as? ComponentActivity)?.finish()

                                context.startActivity(Intent(context, HomeActivity::class.java))
                            } else {
                                val errorMsg = response.body()?.message ?: "PIN tidak valid atau terjadi kesalahan."
                                dialogInfo = DialogInfo("Login Gagal", errorMsg)
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "IOException: ${e.message}")
                            dialogInfo = DialogInfo("Koneksi Gagal", "Gagal terhubung ke server.")
                        } catch (e: HttpException) {
                            Log.e(TAG, "HttpException: ${e.code()} ${e.message()}")
                            val errorMsg = when(e.code()) {
                                404 -> "Device tidak ditemukan."
                                401 -> "PIN yang Anda masukkan salah."
                                else -> "Terjadi error: ${e.code()}"
                            }
                            dialogInfo = DialogInfo("Login Gagal", errorMsg)
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception: ${e.javaClass.simpleName} ${e.message}")
                            dialogInfo = DialogInfo("Error", "Terjadi kesalahan tidak terduga.")
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    dialogInfo = DialogInfo("Input Tidak Valid", "PIN tidak boleh kosong.")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            Text("Login")
        }
    }

    if (isLoading) {
        LoadingModal() // Menggunakan composable dari ActivationActivity
    }

    dialogInfo?.let { info ->
        MessageDialog(
            dialogInfo = info,
            onDismiss = {
                dialogInfo = null
                // Hapus PIN yang salah setelah dialog ditutup
                if (!isLoginSuccess) {
                    pin = ""
                }
            }
        )
    }
}
