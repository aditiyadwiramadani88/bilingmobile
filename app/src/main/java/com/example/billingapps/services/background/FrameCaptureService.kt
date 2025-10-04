package com.example.billingapps.services.background

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.billingapps.R
import com.example.billingapps.ScreenCaptureActivity
import com.example.billingapps.api.FrameScreenshotRequest
import com.example.billingapps.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class FrameCaptureService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var frameRunnable: Runnable

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isCapturing = false

    companion object {
        const val TAG = "FrameCaptureService"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val ACTION_STOP_CAPTURE = "com.example.billingapps.STOP_CAPTURE"
        private const val UPDATE_INTERVAL_MS = 3 * 1000L
        private const val NOTIFICATION_ID = 3
        private const val NOTIFICATION_CHANNEL_ID = "FrameCaptureChannel"
        private const val TARGET_WIDTH = 480
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶️ Frame Capture Service command received.")

        if (intent?.action == ACTION_STOP_CAPTURE) {
            Log.d(TAG, "Stop action received. Stopping service.")
            stopScreenCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            startScreenCapture(resultCode, resultData)
        } else {
            Log.e(TAG, "Failed to get permission data.")
            stopSelf()
        }

        return START_STICKY
    }

    @SuppressLint("WrongConstant")
    private fun startScreenCapture(resultCode: Int, data: Intent) {
        if (isCapturing) return
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null, cannot start capture.")
            stopSelf()
            return
        }

        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, Resources.getSystem().displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        setupFrameSender()
        handler.post(frameRunnable)
        isCapturing = true
        Log.i(TAG, "Screen capture started successfully.")
    }

    private fun setupFrameSender() {
        frameRunnable = Runnable {
            captureAndSendFrame()
            handler.postDelayed(frameRunnable, UPDATE_INTERVAL_MS)
        }
    }

    private fun captureAndSendFrame() {
        val image = imageReader?.acquireLatestImage() ?: return
        val bitmap = imageToBitmap(image)
        image.close()

        val resizedBitmap = resizeBitmap(bitmap, TARGET_WIDTH)
        val base64String = convertBitmapToBase64(resizedBitmap)

        val sharedPreferences = getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
        val deviceId = sharedPreferences.getString("deviceId", null)

        if (deviceId.isNullOrEmpty()) {
            Log.e(TAG, "Device ID not found, cannot send frame.")
            return
        }
        scope.launch { sendFrameToApi(deviceId, base64String) }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun resizeBitmap(bitmap: Bitmap, newWidth: Int): Bitmap {
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val newHeight = (newWidth * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun convertBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        return "data:image/png;base64,$base64"
    }


    private suspend fun sendFrameToApi(deviceId: String, base64Frame: String) {
        try {
            val request = FrameScreenshotRequest(frameSsBase64 = base64Frame)
            val response = RetrofitClient.instance.updateFrameScreenshot(deviceId, request)
            if (response.isSuccessful) {
                Log.i(TAG, "✅ Frame successfully sent to API.")
            } else {
                Log.e(TAG, "API call failed with code: ${response.code()} and message: ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during API call", e)
        }
    }

    private fun stopScreenCapture() {
        if (!isCapturing) return
        handler.removeCallbacks(frameRunnable)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        mediaProjection = null
        isCapturing = false

        val prefs = getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean(ScreenCaptureActivity.PREF_KEY_IS_CAPTURE_RUNNING, false)
            apply()
        }
        Log.i(TAG, "Screen capture stopped and status updated.")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        job.cancel()
        Log.d(TAG, "⏹ Frame Capture Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Screen Capture Service",
                // --- UBAH KE IMPORTANCE_MIN ---
                NotificationManager.IMPORTANCE_MIN
            )
            channel.description = "Channel for silent screen capture service."
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Billing Apps")
            .setContentText("Screen monitoring service is active.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            // --- UBAH KE PRIORITY_MIN ---
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
