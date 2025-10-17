package com.example.billingapps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.billingapps.api.BlockContent
import com.example.billingapps.api.DeviceDetails
import com.example.billingapps.api.RetrofitClient
import com.example.billingapps.ui.theme.BillingAppsTheme
import kotlinx.coroutines.launch
import java.util.regex.Pattern

// --- Pastikan IP ini SAMA DENGAN IP KOMPUTER ANDA SAAT INI ---
private val LOCAL_SERVER_BASE_URL = MyApp.BE_URL.replace("/", "")

// --- ViewModel (Tidak ada perubahan) ---
class LockScreenViewModel : ViewModel() {
    var deviceDetails by mutableStateOf<DeviceDetails?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set

    fun fetchDeviceStatus(deviceId: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.instance.getDeviceStatus(deviceId)
                if (response.isSuccessful) {
                    deviceDetails = response.body()?.data
                } else {
                    errorMessage = "Gagal memuat data: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage = "Terjadi kesalahan jaringan. Pastikan koneksi & firewall benar. Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}

// --- Activity & Screen (Tidak ada perubahan signifikan) ---
class BlockScreenActivity : ComponentActivity() {
    private val viewModel: LockScreenViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreferences: SharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val deviceId = sharedPreferences.getString("deviceId", "indonesia")
        if (deviceId != null) { viewModel.fetchDeviceStatus(deviceId) }
        setContent {
            BillingAppsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LockScreen(viewModel = viewModel)
                }
            }
        }
    }

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

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("BlockScreenActivity", "Broadcast → finishAndRemoveTask()")
            Handler(Looper.getMainLooper()).post {
                finishAndRemoveTask()
            }
        }
    }

}



@Composable
fun LockScreen(viewModel: LockScreenViewModel) {
    val deviceDetails = viewModel.deviceDetails
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage

    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        errorMessage != null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = errorMessage, color = Color.Red, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
        }
        deviceDetails?.lockscreenDesign != null -> {
            LockScreenContent(design = deviceDetails.lockscreenDesign)
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Desain Lockscreen tidak tersedia.", textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun LockScreenContent(design: com.example.billingapps.api.LockscreenDesign) {
    val context = LocalContext.current // Mendapatkan context
    val contentBlocks = listOfNotNull(design.blockContent1, design.blockContent2, design.blockContent3)
    Box(modifier = Modifier.fillMaxSize()) {
        val imagePainter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(buildFullMediaUrl(design.backgroundImage))
                .crossfade(true)
                .listener(onError = { _, result ->
                    Log.e("ImageLoader", "Gagal memuat background: ${result.throwable}")
                })
                .build(),
            error = ColorPainter(Color.Red.copy(alpha = 0.6f)),
            fallback = ColorPainter(Color.Gray)
        )
        Image(painter = imagePainter, contentDescription = "Background", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- PERUBAHAN DI SINI: Teks "Take a deep breath" dihapus ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Spacer untuk memberikan sedikit ruang dari atas layar
                Spacer(modifier = Modifier.height(32.dp))
                contentBlocks.forEach { block -> ContentBlock(block = block) }
            }

            // --- PERUBAHAN DI SINI: Tombol lama diganti dengan komponen baru ---
            GlassmorphicButton(
                onClick = {
                    val intent = Intent(context, UnlockActivity::class.java)
                    context.startActivity(intent)
                }
            )
        }
    }
}

/**
 * --- TOMBOL BARU ---
 * Composable untuk membuat tombol dengan efek glassmorphism (semi-transparan).
 * Tombol ini memiliki latar belakang buram, border tipis, dan teks yang jelas di atasnya.
 */
@Composable
fun GlassmorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50)) // Bentuk tombol yang sangat bulat
            .background(Color.White.copy(alpha = 0.2f)) // Latar belakang semi-transparan
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(50)) // Border putih tipis
            .clickable(onClick = onClick) // Menambahkan aksi klik
            .padding(horizontal = 48.dp, vertical = 16.dp), // Padding di dalam tombol
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "UNLOCK",
            color = Color.White, // Warna teks putih agar kontras
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.25f),
                    offset = Offset(0f, 4f),
                    blurRadius = 8f
                )
            )
        )
    }
}


@Composable
fun ContentBlock(block: BlockContent) {
    when (block.type) {
        "slide_image" -> ImageSlider(mediaItems = block.media ?: emptyList())
        "video" -> VideoPlayer(videoUrl = block.videoUrl)
        "text" -> TextBlock(text = block.content)
    }
}

@Composable
fun TextBlock(text: String?) {
    if (!text.isNullOrEmpty()) {
        Text(text = text, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp), color = Color.White)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageSlider(mediaItems: List<com.example.billingapps.api.MediaItem>) {
    if (mediaItems.isNotEmpty()) {
        val pagerState = rememberPagerState(pageCount = { mediaItems.size })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(200.dp)) { page ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), shape = RoundedCornerShape(16.dp)) {
                    val imagePainter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(buildFullMediaUrl(mediaItems[page].url))
                            .crossfade(true)
                            .listener(onError = { _, result -> Log.e("ImageLoader", "Gagal memuat gambar slider: ${result.throwable}") })
                            .build(),
                        error = ColorPainter(Color.Red.copy(alpha = 0.6f)),
                        fallback = ColorPainter(Color.Gray)
                    )
                    Image(painter = imagePainter, contentDescription = "Image ${page + 1}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            if (mediaItems.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pagerState.pageCount) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                        Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(videoUrl: String?) {
    if (videoUrl.isNullOrEmpty()) return

    val isYouTubeUrl = videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isYouTubeUrl) {
                    YoutubePlayerWebView(videoUrl = videoUrl, modifier = Modifier.fillMaxSize())
                } else {
                    val fullVideoUrl = buildFullMediaUrl(videoUrl)
                    if (fullVideoUrl != null) {
                        DirectVideoPlayer(videoUrl = fullVideoUrl)
                    }
                }
            }
        }
    }
}

fun getYouTubeVideoId(youTubeUrl: String): String? {
    val pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*"
    val compiledPattern = Pattern.compile(pattern)
    val matcher = compiledPattern.matcher(youTubeUrl)
    return if (matcher.find()) { matcher.group() } else { null }
}

@Composable
private fun YoutubePlayerWebView(videoUrl: String, modifier: Modifier = Modifier) {
    val videoId = getYouTubeVideoId(videoUrl)
    if (videoId == null) {
        Log.e("YoutubePlayerWebView", "Video ID tidak valid dari URL: $videoUrl")
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            Text("URL YouTube tidak valid", color = Color.White)
        }
        return
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webViewClient = WebViewClient()

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            body { margin: 0; background-color: #000; }
                            .video-container { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
                            iframe { width: 100%; height: 100%; border: 0; }
                        </style>
                    </head>
                    <body>
                        <div class="video-container">
                            <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&controls=0&showinfo=0&rel=0&loop=1&playlist=$videoId"
                                    frameborder="0"
                                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                                    allowfullscreen>
                            </iframe>
                        </div>
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    )
}

@Composable
fun DirectVideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true // Autoplay
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = Modifier,
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = true
            }
        }
    )
}

private fun buildFullMediaUrl(path: String?): String? {
    if (path.isNullOrEmpty()) { return null }
    return if (path.startsWith("http://") || path.startsWith("https://")) {
        path
    } else {
        LOCAL_SERVER_BASE_URL + path
    }
}
