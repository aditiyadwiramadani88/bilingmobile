package com.example.billingapps

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.billingapps.api.RetrofitClient
import com.example.billingapps.api.StartSessionRequest
import com.example.billingapps.ui.theme.BillingAppsTheme
import kotlinx.coroutines.launch

class UnlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔒 Blok tombol BACK
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.d("UnlockActivity", "Tombol back ditekan, tapi diblokir.")
                    Toast.makeText(
                        this@UnlockActivity,
                        "Tidak bisa kembali dari layar ini.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        // 🔒 Blok “swipe close” atau kehilangan fokus (Recent Apps, Gesture)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        val sharedPreferences: SharedPreferences =
            getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
        val deviceId = sharedPreferences.getString("deviceId", "indonesia") ?: "indonesia"

        setContent {
            BillingAppsTheme {
                UnlockScreen(
                    deviceId = deviceId,
                    onUnlockSuccess = {
                        // Kirim sinyal ke service agar berhenti blok
                        sendBroadcast(Intent("ACTION_UNLOCK_SUCCESS"))

                        // Tampilkan loading 5 detik sebelum pindah
                        Handler(Looper.getMainLooper()).postDelayed({
                            val prefs = getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
                            val lastPackage = prefs.getString("lastLockedPackage", null)

                            if (lastPackage != null) {
                                val launchIntent = packageManager.getLaunchIntentForPackage(lastPackage)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launchIntent)
                                    Log.d("UnlockActivity", "Membuka kembali app terakhir: $lastPackage")
                                }
                            } else {
                                val homeIntent = Intent(Intent.ACTION_MAIN)
                                homeIntent.addCategory(Intent.CATEGORY_HOME)
                                homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(homeIntent)
                            }

                            finishAffinity()
                        }, 5000)
                    }
                )
            }
        }
    }

    // 🔒 Jika user geser (Recent Apps/Home), munculkan lagi activity ini
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        Log.d("BlockScreenActivity", "User menekan Home/Recent Apps → moveTaskToBack + finish")
        moveTaskToBack(true)
        finishAndRemoveTask()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            Log.d("BlockScreenActivity", "Kehilangan fokus (Recent Apps). Tutup dengan delay.")
            Handler(Looper.getMainLooper()).postDelayed({
                finishAndRemoveTask()
            }, 300)
        }
    }

    @Composable
    fun UnlockScreen(
        deviceId: String,
        onUnlockSuccess: () -> Unit
    ) {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isUnlockSuccess by remember { mutableStateOf(false) }
        // State baru untuk menampilkan loading screen setelah sukses
        var showSuccessLoading by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        fun startUnlockSession() {
            scope.launch {
                isLoading = true
                errorMessage = null
                try {
                    val request = StartSessionRequest(
                        deviceId = deviceId,
                        username = username,
                        password = password
                    )
                    val response = RetrofitClient.instance.startSession(request)

                    if (response.isSuccessful && response.body() != null) {
                        isUnlockSuccess = true
                    } else {
                        errorMessage = response.errorBody()?.string()
                            ?: "Gagal memulai sesi. Kode: ${response.code()}"
                    }
                } catch (e: Exception) {
                    errorMessage = "Terjadi kesalahan jaringan: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }

        LaunchedEffect(isUnlockSuccess) {
            if (isUnlockSuccess) {
                // Tampilkan UI loading sukses
                showSuccessLoading = true
                // Jalankan callback dari activity
                onUnlockSuccess()
            }
        }

        LaunchedEffect(errorMessage) {
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                errorMessage = null
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            if (showSuccessLoading) {
                // Tampilan loading baru setelah unlock berhasil
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Unlock Berhasil!",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Membuka aplikasi dalam 5 detik...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                // Tampilan form login
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Masuk untuk Membuka",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { startUnlockSession() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text("UNLOCK")
                            }
                        }

                        TextButton(onClick = { (context as? UnlockActivity)?.finish() }) {
                            Text("Batal", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
