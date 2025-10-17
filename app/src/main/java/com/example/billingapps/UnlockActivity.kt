package com.example.billingapps
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        val sharedPreferences: SharedPreferences = getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
        val deviceId = sharedPreferences.getString("deviceId", "indonesia") ?: "indonesia"
        setContent {
            BillingAppsTheme {
                UnlockScreen(
                    deviceId = deviceId,
                    onUnlockSuccess = {
                        // Aksi setelah berhasil unlock, misalnya menutup activity ini
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun UnlockScreen(
    deviceId: String,
    onUnlockSuccess: () -> Unit
) {
    // State dielola langsung di dalam Composable, tanpa ViewModel
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUnlockSuccess by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope() // Coroutine scope untuk Composable

    // Fungsi untuk memanggil API
    fun startUnlockSession() {
        scope.launch {
            isLoading = true
            errorMessage = null
            isUnlockSuccess = false
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
                    errorMessage = response.errorBody()?.string() ?: "Gagal memulai sesi. Kode: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage = "Terjadi kesalahan jaringan: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Listener untuk aksi setelah unlock berhasil
    LaunchedEffect(isUnlockSuccess) {
        if (isUnlockSuccess) {
            Toast.makeText(context, "Unlock Berhasil!", Toast.LENGTH_SHORT).show()
            onUnlockSuccess()
        }
    }

    // Listener untuk menampilkan error
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            errorMessage = null // Reset error setelah ditampilkan
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "Masuk untuk Membuka", style = MaterialTheme.typography.headlineMedium)

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
                    onClick = {
                        startUnlockSession()
                    },
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

