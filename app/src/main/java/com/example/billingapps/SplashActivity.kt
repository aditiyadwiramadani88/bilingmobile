package com.example.billingapps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "BillingAppPrefs"
private const val KEY_DEVICE_ID = "deviceId"

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Panggil Composable untuk UI Splash Screen
            SplashScreenUI()
        }

        CoroutineScope(Dispatchers.Main).launch {
            // Beri jeda agar splash screen terlihat
            delay(2500) // Waktu bisa disesuaikan

            val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val deviceId = sharedPreferences.getString(KEY_DEVICE_ID, null)

            // Tentukan activity selanjutnya berdasarkan status aktivasi
            val nextActivity = if (deviceId != null) {
                PinActivity::class.java
            } else {
                ActivationActivity::class.java
            }

            startActivity(Intent(this@SplashActivity, nextActivity))
            // Tutup SplashActivity agar tidak bisa kembali
            finish()
        }
    }
}

@Composable
fun SplashScreenUI() {
    // Latar belakang utama splash screen, warnanya disesuaikan dengan gambar referensi
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF6A5AE0)), // Warna ungu dari gambar
        contentAlignment = Alignment.Center
    ) {
        // Kontainer putih dengan sudut membulat untuk ikon
        Box(
            modifier = Modifier
                .size(180.dp) // Ukuran kontainer ikon
                .clip(RoundedCornerShape(40.dp)) // Membuat sudut menjadi bulat
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            // Gambar/Ikon di tengah.
            // GANTI R.drawable.ic_avatar DENGAN RESOURCE GAMBAR ANDA SENDIRI.
            // Pastikan Anda sudah menambahkan file gambar (misal: ic_avatar.png)
            // ke dalam folder res/drawable di project Android Anda.
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Placeholder, ganti dengan ikon Anda
                contentDescription = "Logo Aplikasi",
                modifier = Modifier.size(120.dp) // Ukuran ikon di dalam kontainer
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SplashScreenPreview() {
    SplashScreenUI()
}
