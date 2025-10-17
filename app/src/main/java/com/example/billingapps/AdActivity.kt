package com.example.billingapps

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.billingapps.api.RetrofitClient
import com.example.billingapps.api.UpdateAdStatusRequest
import com.example.billingapps.ui.theme.BillingAppsTheme
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class AdActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AD_ID = "extra_ad_id"
        const val EXTRA_AD_NAME = "extra_ad_name"
        const val EXTRA_AD_TYPE = "extra_ad_type"
        const val EXTRA_AD_CONTENT = "extra_ad_content"
        const val EXTRA_AD_DURATION = "extra_ad_duration"
        const val EXTRA_DEVICE_ID = "extra_device_id"
        private const val TAG = "AdActivity"
    }

    private var adId: String? = null
    private var deviceId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hasBeenExpired = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adId = intent.getStringExtra(EXTRA_AD_ID)
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        val adName = intent.getStringExtra(EXTRA_AD_NAME) ?: "Iklan"
        val adType = intent.getStringExtra(EXTRA_AD_TYPE) ?: "text"
        val adContent = intent.getStringExtra(EXTRA_AD_CONTENT) ?: "Konten tidak tersedia."
        val adDuration = intent.getIntExtra(EXTRA_AD_DURATION, 30)

        if (adId == null || deviceId == null) {
            Log.e(TAG, "Ad ID or Device ID is missing. Finishing activity.")
            finish()
            return
        }

        updateAdStatus("displayed")

        handler.postDelayed({
            expireAdAndFinish()
        }, adDuration * 1000L)


        // --- PERUBAHAN UTAMA ---
        // Menghapus Box pembungkus dan langsung memanggil composable fullscreen.
        // Activity kini akan langsung menampilkan konten iklan secara penuh.
        setContent {
            BillingAppsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FullScreenAdContent(
                        adName = adName,
                        adType = adType,
                        adContent = adContent,
                        onDismiss = {
                            expireAdAndFinish()
                        }
                    )
                }
            }
        }
    }

    private fun updateAdStatus(status: String) {
        val currentAdId = adId
        val currentDeviceId = deviceId
        if (currentAdId == null || currentDeviceId == null) {
            Log.w(TAG, "Cannot update status, adId or deviceId is null.")
            return
        }

        lifecycleScope.launch {
            try {
                val request = UpdateAdStatusRequest(deviceId = currentDeviceId, status = status)
                val response = RetrofitClient.instance.updateAdStatus(currentAdId, request)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.i(TAG, "✅ Ad status update successful: ${responseBody?.message}")
                } else {
                    Log.e(TAG, "🚨 Failed to update ad status. Code: ${response.code()}, Msg: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception while updating ad status", e)
            }
        }
    }

    private fun expireAdAndFinish() {
        if (!hasBeenExpired) {
            hasBeenExpired = true
            updateAdStatus("expired")
            handler.removeCallbacksAndMessages(null)
            finish()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

// Perubahan: Composable `AdContentCard` diubah menjadi `FullScreenAdContent`.
// Composable ini sekarang menggunakan `Box` yang mengisi seluruh layar.
@Composable
fun FullScreenAdContent(
    adName: String,
    adType: String,
    adContent: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Background default jika media gagal dimuat
    ) {
        // Konten Iklan (disesuaikan untuk memenuhi seluruh layar)
        when (adType) {
            "image" -> AdImageContent(contentUrl = adContent)
            "video" -> AdVideoContent(videoUrl = adContent)
            "text" -> AdTextContent(text = adContent)
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tipe konten tidak didukung: $adType", color = Color.White)
            }
        }

        // Tombol Close (selalu di atas, di pojok kanan atas)
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Ad",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(32.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .clip(CircleShape)
                .clickable { onDismiss() }
                .padding(4.dp),
            tint = Color.White
        )
    }
}

@Composable
fun AdTextContent(text: String) {
    // Box untuk menengahkan teks di seluruh layar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
            color = Color.White // Warna teks diubah agar kontras dengan background hitam
        )
    }
}

// Perubahan: `AdImageContent` diubah untuk mengisi seluruh layar.
@Composable
fun AdImageContent(contentUrl: String) {
    val fullUrl = buildFullMediaUrl(contentUrl)

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(fullUrl)
            .crossfade(true)
            .build(),
        contentDescription = "Ad Image",
        modifier = Modifier.fillMaxSize(), // Mengisi seluruh layar
        contentScale = ContentScale.Crop // Crop gambar agar pas, menjaga aspek rasio
    )
}


// Perubahan: `AdVideoContent` diubah untuk mengisi seluruh layar.
@Composable
fun AdVideoContent(videoUrl: String) {
    val isYouTubeUrl = videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")
    if (isYouTubeUrl) {
        YoutubePlayerWebView(videoUrl = videoUrl, modifier = Modifier.fillMaxSize()) // Mengisi seluruh layar
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black), contentAlignment = Alignment.Center
        ) {
            Text("Direct video player not implemented yet.", color = Color.White)
        }
    }
}


// --- Helper Functions ---
private fun buildFullMediaUrl(path: String?): String? {
    val baseUrl = MyApp.BE_URL
    if (path.isNullOrEmpty()) {
        return null
    }
    return if (path.startsWith("http://") || path.startsWith("https://")) {
        path
    } else {
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}




@Composable
private fun YoutubePlayerWebView(videoUrl: String, modifier: Modifier = Modifier) {
    val videoId = getYouTubeVideoId(videoUrl)
    if (videoId == null) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            Text("URL YouTube tidak valid", color = Color.White)
        }
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                val html = """
                        <!DOCTYPE html><html><head>
                        <style>body{margin:0;padding:0;overflow:hidden;background-color:black;}iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0;}</style>
                        </head><body>
                        <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&controls=0&showinfo=0&rel=0" frameborder="0" allow="autoplay; encrypted-media" allowfullscreen></iframe>
                        </body></html>
                    """.trimIndent()
                loadData(html, "text/html", "utf-8")
            }
        }
    )
}

