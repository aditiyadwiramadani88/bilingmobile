package com.example.billingapps

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import coil3.compose.rememberAsyncImagePainter
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

        // Update status menjadi "displayed" saat activity dibuat
        updateAdStatus("displayed")

        // Jadwalkan update status menjadi "expired" sesuai durasi
        handler.postDelayed({
            expireAdAndFinish()
        }, adDuration * 1000L)


        setContent {
            BillingAppsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.6f)) {
                    AdDialog(
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
            handler.removeCallbacksAndMessages(null) // Hapus callback lain jika ada
            finish()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Pastikan semua callback handler dihapus untuk menghindari memory leak
        handler.removeCallbacksAndMessages(null)
    }
}

@Composable
fun AdDialog(
    adName: String,
    adType: String,
    adContent: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header (Judul Iklan & Tombol Close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = adName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Gray)
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Ad",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onDismiss() }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Konten Iklan
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (adType) {
                        "image" -> AdImageContent(contentUrl = adContent)
                        "video" -> AdVideoContent(videoUrl = adContent)
                        "text" -> AdTextContent(text = adContent)
                        else -> Text("Tipe konten tidak didukung: $adType")
                    }
                }
            }
        }
    }
}

@Composable
fun AdTextContent(text: String) {
    Text(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        lineHeight = 30.sp
    )
}

@Composable
fun AdImageContent(contentUrl: String) {
    val fullUrl = buildFullMediaUrl(contentUrl)
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(fullUrl)
            .crossfade(true)
            .build()
    )
    Image(
        painter = painter,
        contentDescription = "Ad Image",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun AdVideoContent(videoUrl: String) {
    val isYouTubeUrl = videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")
    if (isYouTubeUrl) {
        YoutubePlayerWebView(videoUrl = videoUrl, modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp)))
    } else {
        // Placeholder untuk video direct, bisa diimplementasikan dengan ExoPlayer jika perlu
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.Black, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center){
            Text("Direct video player not implemented yet.", color = Color.White)
        }
    }
}


// --- Helper Functions (diambil dari BlockScreenActivity) ---
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


