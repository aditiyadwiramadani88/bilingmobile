package com.example.billingapps

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.billingapps.services.background.FrameCaptureService

/**
 * Activity transparan yang HANYA bertugas untuk meminta izin MediaProjection (rekam layar).
 * Setelah izin didapat, activity ini akan memulai service, menyimpan statusnya, dan langsung menutup dirinya.
 */
class ScreenCaptureActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScreenCaptureActivity"
        const val PREF_KEY_IS_CAPTURE_RUNNING = "isScreenCaptureRunning"
    }

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val prefs = getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)

        if (result.resultCode == RESULT_OK && result.data != null) {
            Log.d(TAG, "Screen capture permission granted.")
            // Izin didapat, mulai service dengan data izin
            val serviceIntent = Intent(this, FrameCaptureService::class.java).apply {
                putExtra(FrameCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FrameCaptureService.EXTRA_RESULT_DATA, result.data)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            // Simpan status bahwa service sedang berjalan
            with(prefs.edit()) {
                putBoolean(PREF_KEY_IS_CAPTURE_RUNNING, true)
                apply()
            }
            Log.d(TAG, "Screen capture status saved: true")

        } else {
            Log.w(TAG, "Screen capture permission denied.")
            // Pastikan status tersimpan sebagai false jika izin ditolak
            with(prefs.edit()) {
                putBoolean(PREF_KEY_IS_CAPTURE_RUNNING, false)
                apply()
            }
        }
        // Tutup activity ini apapun hasilnya
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Requesting screen capture permission...")

        // PERBAIKAN: Gunakan Handler untuk menunda permintaan izin sejenak.
        // Ini memberi waktu bagi Activity untuk menyelesaikan siklus hidup 'create' dan 'resume',
        // sehingga mencegah "pause timeout" saat dialog izin sistem muncul.
        Handler(Looper.getMainLooper()).post {
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }
}
